package com.orbitalhq.preflight.spec

@MarkdownSpec
data class TestScenario(
    @FrontMatter("spec-version") val specVersion: String = "0.1",
    @Title val name: String,
    @Description val description: String?,
    @Section("Schema") @CodeBlock("taxi") val schema: String,
    @Section("Questions") val questions: List<TestQuestion>
)

data class TestQuestion(
    @Heading val questionToAsk: String,
    @LabeledCodeBlock("Expected Query", "taxiql") val expectedQuery: String,
    @Section("Data Sources") val stubs: List<Stub>
)
