package com.orbitalhq.preflight.spec

import com.orbitalhq.preflight.spec.internal.*
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.*
import kotlin.reflect.full.*

class MarkdownFormat<T : Any>(private val type: KClass<T>) {

    fun read(markdown: String, filename: String? = null): T {
        val doc = DocumentParser.parse(markdown, filename)
        return readFromDocument(doc, type, filename)
    }

    fun write(value: T): String {
        val doc = writeToDocument(value, type)
        return DocumentRenderer.render(doc)
    }

    fun readFile(path: Path): T = read(path.readText(), filename = path.name)

    fun writeFile(value: T, path: Path) {
        path.writeText(write(value))
    }

    private fun <C : Any> readFromDocument(doc: ParsedDocument, cls: KClass<C>, filename: String?): C {
        val constructor = cls.primaryConstructor
            ?: throw SpecParseException("Class ${cls.simpleName} has no primary constructor", filename = filename)

        val args = mutableMapOf<KParameter, Any?>()
        // Track qualifier values resolved from code blocks (qualifierProperty -> resolved enum value)
        val resolvedQualifiers = mutableMapOf<String, Any>()

        // First pass: resolve annotated properties and collect qualifiers
        for (param in constructor.parameters) {
            val prop = cls.memberProperties.find { it.name == param.name } ?: continue

            val codeBlockAnn = prop.findAnnotation<CodeBlock>()
            val sectionAnn = prop.findAnnotation<Section>()

            // Handle @Section @CodeBlock with qualifierProperty
            if (sectionAnn != null && codeBlockAnn != null && codeBlockAnn.qualifierProperty.isNotEmpty()) {
                val section = doc.sections.find { it.title == sectionAnn.name }
                val codeBlock = section?.content?.filterIsInstance<ParsedContent.CodeBlock>()?.firstOrNull()
                if (codeBlock != null) {
                    args[param] = codeBlock.code
                    // Resolve the qualifier for the referenced property
                    val qualifierPropName = codeBlockAnn.qualifierProperty
                    val qualifierParam = constructor.parameters.find { it.name == qualifierPropName }
                    val qualifierProp = cls.memberProperties.find { it.name == qualifierPropName }
                    if (qualifierParam != null && qualifierProp != null) {
                        val qualifierCls = qualifierProp.returnType.classifier as? KClass<*>
                        if (qualifierCls != null && qualifierCls.java.isEnum && CodeBlockQualifier::class.java.isAssignableFrom(qualifierCls.java)) {
                            val enumConstants = qualifierCls.java.enumConstants
                            val qualifierStr = codeBlock.qualifier
                            val matched = enumConstants.filterIsInstance<CodeBlockQualifier>().find {
                                it.qualifier == qualifierStr
                            }
                            if (matched != null) {
                                resolvedQualifiers[qualifierPropName] = matched
                            }
                        }
                    }
                } else {
                    // Section or code block not found
                    if (!param.isOptional && param.type.isMarkedNullable) {
                        args[param] = null
                    } else if (!param.isOptional) {
                        throw SpecParseException(
                            "Missing required field '${param.name}' for ${cls.simpleName}",
                            filename = filename
                        )
                    }
                }
                continue
            }

            val value = resolvePropertyValue(prop, doc, doc.sections, filename)
            if (value != null) {
                args[param] = value
            } else if (param.name in resolvedQualifiers) {
                // Already handled via qualifier resolution
            } else if (!param.isOptional && param.type.isMarkedNullable) {
                args[param] = null
            } else if (!param.isOptional) {
                throw SpecParseException(
                    "Missing required field '${param.name}' for ${cls.simpleName}",
                    filename = filename
                )
            }
        }

        // Second pass: apply resolved qualifier values
        for ((propName, qualValue) in resolvedQualifiers) {
            val param = constructor.parameters.find { it.name == propName }
            if (param != null && param !in args) {
                args[param] = qualValue
            }
        }

        return constructor.callBy(args)
    }

    private fun resolvePropertyValue(
        prop: KProperty1<*, *>,
        doc: ParsedDocument,
        sections: List<ParsedSection>,
        filename: String?
    ): Any? {
        val titleAnn = prop.findAnnotation<Title>()
        val descriptionAnn = prop.findAnnotation<Description>()
        val frontMatterAnn = prop.findAnnotation<FrontMatter>()
        val sectionAnn = prop.findAnnotation<Section>()
        val codeBlockAnn = prop.findAnnotation<CodeBlock>()

        return when {
            titleAnn != null -> doc.title
            descriptionAnn != null -> doc.description
            frontMatterAnn != null -> doc.frontMatter[frontMatterAnn.key]
            sectionAnn != null && codeBlockAnn != null -> {
                // Simple @Section @CodeBlock without qualifier (qualifier case handled in readFromDocument)
                val section = sections.find { it.title == sectionAnn.name } ?: return null
                section.content.filterIsInstance<ParsedContent.CodeBlock>().firstOrNull()?.code
            }
            sectionAnn != null && isList(prop) -> {
                val section = sections.find { it.title == sectionAnn.name } ?: return null
                val itemType = listItemType(prop)
                    ?: throw SpecParseException(
                        "Cannot determine list item type for property '${prop.name}'",
                        filename = filename
                    )
                section.children.map { childSection ->
                    readChildFromSection(childSection, itemType, doc, filename)
                }
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <C : Any> readChildFromSection(
        section: ParsedSection,
        cls: KClass<C>,
        doc: ParsedDocument,
        filename: String?
    ): C {
        val constructor = cls.primaryConstructor
            ?: throw SpecParseException("Class ${cls.simpleName} has no primary constructor", filename = filename)

        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val prop = cls.memberProperties.find { it.name == param.name } ?: continue
            val value = resolveChildPropertyValue(prop, section, doc, filename)
            if (value != null) {
                args[param] = value
            } else if (!param.isOptional && param.type.isMarkedNullable) {
                args[param] = null
            } else if (!param.isOptional) {
                throw SpecParseException(
                    "Missing required field '${param.name}' in section '${section.title}'",
                    filename = filename,
                    section = section.title
                )
            }
        }

        return constructor.callBy(args)
    }

    private fun resolveChildPropertyValue(
        prop: KProperty1<*, *>,
        section: ParsedSection,
        doc: ParsedDocument,
        filename: String?
    ): Any? {
        val headingAnn = prop.findAnnotation<Heading>()
        val directiveAnn = prop.findAnnotation<Directive>()
        val labeledCodeBlockAnn = prop.findAnnotation<LabeledCodeBlock>()
        val sectionAnn = prop.findAnnotation<Section>()
        val codeBlockAnn = prop.findAnnotation<CodeBlock>()

        return when {
            headingAnn != null -> section.title
            directiveAnn != null -> {
                val raw = section.directives[directiveAnn.key]
                if (raw != null) {
                    convertDirectiveValue(raw, prop)
                } else if (directiveAnn.defaultValue.isNotEmpty()) {
                    convertDirectiveValue(directiveAnn.defaultValue, prop)
                } else {
                    null
                }
            }
            labeledCodeBlockAnn != null && isListOfStrings(prop) -> {
                val blocks = section.content
                    .filterIsInstance<ParsedContent.CodeBlock>()
                    .filter { it.label == labeledCodeBlockAnn.label }
                blocks.map { it.code }.takeIf { it.isNotEmpty() }
            }
            labeledCodeBlockAnn != null -> {
                section.content
                    .filterIsInstance<ParsedContent.CodeBlock>()
                    .find { it.label == labeledCodeBlockAnn.label }
                    ?.code
            }
            sectionAnn != null && isList(prop) -> {
                val subSection = section.children.find { it.title == sectionAnn.name } ?: return null
                val itemType = listItemType(prop)
                    ?: throw SpecParseException(
                        "Cannot determine list item type for property '${prop.name}'",
                        filename = filename
                    )
                subSection.children.map { childSection ->
                    readChildFromSection(childSection, itemType, doc, filename)
                }
            }
            sectionAnn != null && codeBlockAnn != null -> {
                val subSection = section.children.find { it.title == sectionAnn.name } ?: return null
                subSection.content.filterIsInstance<ParsedContent.CodeBlock>().firstOrNull()?.code
            }
            else -> null
        }
    }

    private fun convertDirectiveValue(raw: String, prop: KProperty1<*, *>): Any {
        val propClass = prop.returnType.classifier as? KClass<*> ?: return raw
        if (propClass.java.isEnum) {
            return directiveValueToEnum(raw, propClass)
        }
        return raw
    }

    // --- Write logic ---

    private fun <C : Any> writeToDocument(value: C, cls: KClass<C>): ParsedDocument {
        val frontMatter = mutableMapOf<String, String>()
        var title: String? = null
        var description: String? = null
        val sections = mutableListOf<ParsedSection>()

        val constructor = cls.primaryConstructor ?: error("No primary constructor for ${cls.simpleName}")
        val props = constructor.parameters.mapNotNull { param ->
            cls.memberProperties.find { it.name == param.name }
        }

        // Collect all property values for qualifier resolution
        val qualifierValues = mutableMapOf<String, Any?>()
        for (prop in props) {
            @Suppress("UNCHECKED_CAST")
            val getter = prop as KProperty1<C, *>
            qualifierValues[prop.name] = getter.get(value)
        }

        for (prop in props) {
            @Suppress("UNCHECKED_CAST")
            val getter = prop as KProperty1<C, *>
            val propValue = getter.get(value)

            val titleAnn = prop.findAnnotation<Title>()
            val descriptionAnn = prop.findAnnotation<Description>()
            val frontMatterAnn = prop.findAnnotation<FrontMatter>()
            val sectionAnn = prop.findAnnotation<Section>()
            val codeBlockAnn = prop.findAnnotation<CodeBlock>()

            when {
                titleAnn != null -> title = propValue as? String
                descriptionAnn != null -> description = propValue as? String
                frontMatterAnn != null -> {
                    if (propValue != null) {
                        frontMatter[frontMatterAnn.key] = propValue.toString()
                    }
                }
                sectionAnn != null && codeBlockAnn != null -> {
                    val code = propValue as? String
                    if (code != null) {
                        var qualifier: String? = null
                        if (codeBlockAnn.qualifierProperty.isNotEmpty()) {
                            val qualVal = qualifierValues[codeBlockAnn.qualifierProperty]
                            if (qualVal is CodeBlockQualifier) {
                                qualifier = qualVal.qualifier
                            }
                        }
                        sections.add(
                            ParsedSection(
                                title = sectionAnn.name,
                                level = 2,
                                directives = emptyMap(),
                                content = listOf(
                                    ParsedContent.CodeBlock(
                                        language = codeBlockAnn.language,
                                        qualifier = qualifier,
                                        code = code,
                                        label = null
                                    )
                                ),
                                children = emptyList()
                            )
                        )
                    }
                }
                sectionAnn != null && isList(prop) -> {
                    val list = propValue as? List<*>
                    val itemType = if (list != null) listItemType(prop) else null
                    if (list != null && itemType != null) {
                        val children = list.filterNotNull().map { item ->
                            writeChildToSection(item, itemType, 3)
                        }
                        sections.add(
                            ParsedSection(
                                title = sectionAnn.name,
                                level = 2,
                                directives = emptyMap(),
                                content = emptyList(),
                                children = children
                            )
                        )
                    }
                }
                sectionAnn != null -> {
                    if (propValue != null) {
                        sections.add(
                            ParsedSection(
                                title = sectionAnn.name,
                                level = 2,
                                directives = emptyMap(),
                                content = listOf(ParsedContent.Paragraph(propValue.toString())),
                                children = emptyList()
                            )
                        )
                    }
                }
            }
        }

        return ParsedDocument(
            frontMatter = frontMatter,
            title = title,
            description = description,
            sections = sections
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <C : Any> writeChildToSection(item: Any, cls: KClass<C>, level: Int): ParsedSection {
        val value = item as C
        val constructor = cls.primaryConstructor ?: error("No primary constructor for ${cls.simpleName}")
        val props = constructor.parameters.mapNotNull { param ->
            cls.memberProperties.find { it.name == param.name }
        }

        var sectionTitle = ""
        val directives = mutableMapOf<String, String>()
        val content = mutableListOf<ParsedContent>()
        val children = mutableListOf<ParsedSection>()

        for (prop in props) {
            val getter = prop as KProperty1<C, *>
            val propValue = getter.get(value)

            val headingAnn = prop.findAnnotation<Heading>()
            val directiveAnn = prop.findAnnotation<Directive>()
            val labeledCodeBlockAnn = prop.findAnnotation<LabeledCodeBlock>()
            val sectionAnn = prop.findAnnotation<Section>()
            val codeBlockAnn = prop.findAnnotation<CodeBlock>()

            when {
                headingAnn != null -> sectionTitle = propValue as? String ?: ""
                directiveAnn != null -> {
                    if (propValue != null) {
                        val strVal = enumToDirectiveValue(propValue)
                        if (strVal != directiveAnn.defaultValue) {
                            directives[directiveAnn.key] = strVal
                        }
                    }
                }
                labeledCodeBlockAnn != null && isListOfStrings(prop) -> {
                    val list = propValue as? List<*>
                    if (list != null) {
                        for (listItem in list) {
                            if (listItem is String) {
                                content.add(
                                    ParsedContent.CodeBlock(
                                        language = labeledCodeBlockAnn.language,
                                        qualifier = null,
                                        code = listItem,
                                        label = labeledCodeBlockAnn.label
                                    )
                                )
                            }
                        }
                    }
                }
                labeledCodeBlockAnn != null -> {
                    val code = propValue as? String
                    if (code != null) {
                        content.add(
                            ParsedContent.CodeBlock(
                                language = labeledCodeBlockAnn.language,
                                qualifier = null,
                                code = code,
                                label = labeledCodeBlockAnn.label
                            )
                        )
                    }
                }
                sectionAnn != null && isList(prop) -> {
                    val list = propValue as? List<*>
                    val itemType = if (list != null) listItemType(prop) else null
                    if (list != null && itemType != null) {
                        val subChildren = list.filterNotNull().map { subItem ->
                            writeChildToSection(subItem, itemType, level + 2)
                        }
                        children.add(
                            ParsedSection(
                                title = sectionAnn.name,
                                level = level + 1,
                                directives = emptyMap(),
                                content = emptyList(),
                                children = subChildren
                            )
                        )
                    }
                }
                sectionAnn != null && codeBlockAnn != null -> {
                    val code = propValue as? String
                    if (code != null) {
                        children.add(
                            ParsedSection(
                                title = sectionAnn.name,
                                level = level + 1,
                                directives = emptyMap(),
                                content = listOf(
                                    ParsedContent.CodeBlock(
                                        language = codeBlockAnn.language,
                                        qualifier = null,
                                        code = code,
                                        label = null
                                    )
                                ),
                                children = emptyList()
                            )
                        )
                    }
                }
            }
        }

        return ParsedSection(
            title = sectionTitle,
            level = level,
            directives = directives,
            content = content,
            children = children
        )
    }

    // --- Utilities ---

    private fun isList(prop: KProperty1<*, *>): Boolean {
        val cls = prop.returnType.classifier as? KClass<*> ?: return false
        return cls == List::class || cls.isSubclassOf(List::class)
    }

    private fun isListOfStrings(prop: KProperty1<*, *>): Boolean {
        if (!isList(prop)) return false
        val typeArg = prop.returnType.arguments.firstOrNull()?.type ?: return false
        return (typeArg.classifier as? KClass<*>) == String::class
    }

    @Suppress("UNCHECKED_CAST")
    private fun listItemType(prop: KProperty1<*, *>): KClass<Any>? {
        val typeArg = prop.returnType.arguments.firstOrNull()?.type ?: return null
        return typeArg.classifier as? KClass<Any>
    }

    companion object {
        fun enumToDirectiveValue(value: Any): String {
            return if (value is Enum<*>) {
                value.name.lowercase().replace('_', '-')
            } else {
                value.toString()
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> directiveValueToEnum(value: String, cls: KClass<T>): T {
            val enumConstants = cls.java.enumConstants as Array<out Enum<*>>
            val normalized = value.uppercase().replace('-', '_')
            return enumConstants.first { it.name == normalized } as T
        }
    }
}

inline fun <reified T : Any> markdownFormat(): MarkdownFormat<T> = MarkdownFormat(T::class)
