package com.orbitalhq.preflight.dsl

import com.orbitalhq.SourcePackage
import com.orbitalhq.firstRawObject
import com.orbitalhq.firstRawValue
import com.orbitalhq.firstTypedInstace
import com.orbitalhq.models.TypedInstance
import com.orbitalhq.preflight.dsl.PreflightExtension.Companion.PreflightTestCaseKey
import com.orbitalhq.preflight.dsl.containers.ContainerSupport
import com.orbitalhq.query.QueryResult
import com.orbitalhq.query.connectors.OperationInvoker
import com.orbitalhq.rawObjects
import com.orbitalhq.schemaServer.core.adaptors.taxi.TaxiSchemaSourcesAdaptor
import com.orbitalhq.schemaServer.core.file.FileProjectSpec
import com.orbitalhq.schemaServer.core.file.packages.FileSystemPackageLoader
import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.stubbing.StubService
import com.orbitalhq.testVyneWithStub
import com.orbitalhq.typedInstances
import com.orbitalhq.utils.files.ReactivePollingFileSystemMonitor
import io.kotest.assertions.AssertionFailedError
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import lang.taxi.CompilationException
import lang.taxi.TaxiDocument
import lang.taxi.packages.TaxiPackageLoader
import lang.taxi.query.TaxiQLQueryString
import org.taxilang.packagemanager.DefaultDependencyFetcherProvider
import org.taxilang.packagemanager.DependencyFetcherProvider
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.io.path.absolute

sealed interface PreflightSourceConfig {
    fun loadSchema(environmentVariables: Map<String, String> = emptyMap()): TaxiSchema
}

class FilePathSourceConfig(
    private val projectRoot: Path = Paths.get("./"),
    private val dependencyFetcherProvider: DependencyFetcherProvider = DefaultDependencyFetcherProvider
) : PreflightSourceConfig {
    override fun loadSchema(environmentVariables: Map<String, String>): TaxiSchema {
        val loader = TaxiPackageLoader.forDirectoryContainingTaxiFile(projectRoot.absolute().normalize())
        val taxiProject = loader.load()
        val sourcePackage = loadSourcePackage(taxiProject.packageRootPath!!)

        return withClue("Taxi project should compile without errors") {
            val taxiSchema = try {
                TaxiSchema.from(
                    sourcePackage,
                    environmentVariables = environmentVariables,
                    onErrorBehaviour = TaxiSchema.Companion.TaxiSchemaErrorBehaviour.THROW_EXCEPTION
                )
            } catch (e: CompilationException) {
                fail("Taxi project has errors: \n${e.message}")
            }
            taxiSchema
        }
    }

    /**
     * Loads a source path into a SourcePackage
     * Uses the orbital approach of loading (using a FileSystemPackageLoader)
     * rather than a simple TaxiPackageLoader, as we need to support transpilation of non-taxi sources
     */
    private fun loadSourcePackage(packageRootPath: Path): SourcePackage {
        val spec = FileProjectSpec(path = packageRootPath)
        val fileMonitor = ReactivePollingFileSystemMonitor(packageRootPath, Duration.ofHours(9999))
        val converter = TaxiSchemaSourcesAdaptor(
            dependencyFetcherProvider = dependencyFetcherProvider
        )
        val packageLoader = FileSystemPackageLoader(spec, converter, fileMonitor)
        val packageMetadata = converter.buildMetadata(packageLoader)
            .block()!!
        val sourcePackage = converter.convert(packageMetadata, packageLoader).block()!!
        return sourcePackage
    }
}

class StringSourceConfig(private val source: String) : PreflightSourceConfig {
    override fun loadSchema(environmentVariables: Map<String, String>): TaxiSchema {
        return TaxiSchema.from(source, environmentVariables = environmentVariables)
    }
}

fun forSchema(source: String) = StringSourceConfig(source)

class PreflightExtension(
    private val sourceConfig: PreflightSourceConfig = FilePathSourceConfig(),
    private val envVariableContainer: EnvVariableContainer = SpecEnvVariables.newInstance()
) : BeforeSpecListener,
    BeforeTestListener,
    AfterTestListener,
    TestCaseExtension,
    EnvVariableContainer by envVariableContainer {

    companion object {
        val PreflightTestCaseKey = object : CoroutineContext.Key<PreflightTestCaseContext> {}
    }


    val containerRegistrations = mutableListOf<ContainerSupport>()

    fun addContainerRegistration(registration: ContainerSupport) {
        containerRegistrations.add(registration)
    }

    inline fun <reified T>  containerForConnection(connectionName: String):T {
        val containerSupport = this.containerRegistrations.firstOrNull { it.connectionName == connectionName }
            ?: error("No container with connectionName $connectionName is registered")
        return containerSupport as T
    }

    private var invokersCreated = false
    var invokers: List<OperationInvoker> = emptyList()
        private set


    /**
     * Provides access to the compiled taxi document.
     * This is lower-level than Orbital's schema object
     */
    lateinit var taxi: TaxiDocument
        private set

    lateinit var schema: TaxiSchema
        private set

    private lateinit var sourcePackage: SourcePackage
        private set

    private val capturedScenarios = mutableMapOf<TestCase, CapturedQuery>()


    override suspend fun beforeSpec(spec: Spec) {
        envVariableContainer.markImmutable()
        withClue("Taxi project should compile without errors") {
            val taxiSchema = try {
                sourceConfig.loadSchema(environmentVariables = envVariableContainer.envVariables)
            } catch (e: CompilationException) {
                fail("Taxi project has errors: \n${e.message}")
            }

            this.schema = taxiSchema
            this.taxi = taxiSchema.taxi
        }
    }

    private fun buildInvokersOnce() {
        if (invokersCreated) return
        if (!::schema.isInitialized) error("A startup sequencing issue occurred - cannot call buildInvokers before the schema has been provided")

        this.containerRegistrations.forEach {
            it.container.start()
        }
        this.invokers = this.containerRegistrations.map {
            it.invokerFactory(this.schema)
        }
        this.invokersCreated = true
    }


    fun orbital(): Pair<Orbital, StubService> {
        buildInvokersOnce()
        return testVyneWithStub(this.schema, this.invokers)
    }

    private fun stubScenariosToCustomizer(scenarios: List<StubScenario>): (StubService) -> Unit {
        return { stub ->
            scenarios.forEach { scenario ->
                stub.addResponse(scenario.operationName, scenario.response)
            }
        }
    }

    suspend fun queryForScalar(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForScalar(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForScalar(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): Any? {
        return query(taxiQl, emptyMap(), stubCustomizer)
            .firstRawValue()
    }


    suspend fun queryForMap(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForMap(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForMap(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): Map<String, Any?> {
        return query(taxiQl, emptyMap(), stubCustomizer)
            .firstRawObject()
    }

    suspend fun queryForCollectionOfMaps(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForCollectionOfMaps(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForCollectionOfMaps(
        taxiQl: String,
        stubCustomizer: (StubService) -> Unit = {}
    ): List<Map<String, Any?>> {
        return query(taxiQl, emptyMap(), stubCustomizer)
            .rawObjects()
    }

    suspend fun queryForTypedInstance(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForTypedInstance(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForTypedInstance(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): TypedInstance {
        return query(taxiQl, emptyMap(), stubCustomizer)
            .firstTypedInstace()
    }

    suspend fun runNamedQueryForCollectionOfTypedInstances(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): List<TypedInstance> {
        return runNamedQuery(queryName, arguments, stubCustomizer)
            .typedInstances()
    }

    suspend fun runNamedQueryForCollectionOfMaps(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): List<Map<String,Any?>> {
        return runNamedQuery(queryName, arguments, stubCustomizer)
            .rawObjects()
    }

    suspend fun runNamedQueryForTypedInstance(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): TypedInstance {
        return runNamedQuery(queryName, arguments, stubCustomizer)
            .firstTypedInstace()
    }

    suspend fun runNamedQueryForMap(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): Map<String,Any?> {
        return runNamedQuery(queryName, arguments, stubCustomizer)
            .firstTypedInstace()
            .toRawObject() as Map<String,Any?>
    }

    suspend fun runNamedQueryForStreamOfTypedInstances(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): Flow<TypedInstance> {
        return runNamedQuery(queryName, arguments, stubCustomizer).results
    }

    suspend fun runNamedQueryForStreamOfMaps(
        queryName: String,
        arguments: Map<String, Any?> = emptyMap(),
        stubCustomizer: (StubService) -> Unit = {}
    ): Flow<Map<String,Any?>> {
        return runNamedQuery(queryName, arguments, stubCustomizer).results
            .map { it.toRawObject() as Map<String,Any?>}
    }

    suspend fun queryForStreamOfTypedInstances(
        taxiQl: String,
        stubCustomizer: (StubService) -> Unit = {}
    ): Flow<TypedInstance> {
        return query(taxiQl, emptyMap(), stubCustomizer).results

    }
    suspend fun queryForStreamOfMaps(
        taxiQl: String,
        stubCustomizer: (StubService) -> Unit = {}
    ):Flow<Map<String,Any>> {
        return query(taxiQl, emptyMap(), stubCustomizer).results
            .map { it.toRawObject() as Map<String,Any> }

    }
    private suspend fun runNamedQuery(
        queryName: String,
        arguments: Map<String, Any?>,
        stubCustomizer: (StubService) -> Unit = {}
    ): QueryResult {
        val (orbital) = orbital()
        val savedQuery = orbital.schema.queries.singleOrNull {
            it.name.parameterizedName == queryName || it.name.name == queryName
        }
        if (savedQuery == null) {
            fail("No query named $queryName was found")
        }
        val taxiQl = savedQuery.sources.joinToString("\n") { it.content }
        return query(taxiQl, arguments, stubCustomizer)
    }

    private suspend fun query(
        taxiQl: String,
        arguments: Map<String, Any?>,
        stubCustomizer: (StubService) -> Unit = {}
    ): QueryResult {
        val (orbital, stub) = orbital()
        stubCustomizer(stub)
        val testContext = coroutineContext[PreflightTestCaseKey]
        if (testContext != null) {
            capturedScenarios[testContext.testCase] = CapturedQuery(stub, taxiQl)
        } else {
            println("A test is executing without a context - this shouldn't happen")
        }
        return orbital.query(taxiQl, arguments = arguments)
    }

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult {
        val context = PreflightTestCaseContext(testCase)
        return withContext(context) {
            val testResult = execute(testCase)
            testResult
//            val capturedScenario = capturedScenarios[testCase]
//            if (testResult.isFailure && capturedScenario != null) {
//                val failure = testResult as TestResult.Failure
//                val cause = failure.cause
//                if (cause is AssertionFailedError) {
//                    val originalError = failure.cause as AssertionFailedError
////                    val (_, playgroundLink) = PlaygroundScenarioFactory.buildPlaygroundScenario(
////                        capturedScenario,
////                        sourcePackage,
////                        schema,
////                        originalError,
////                        testCase
////                    )
////                    val errorMessageWithPlaygroundLink = """${originalError.message}
////                        |
////                        |This error is explorable in Taxi Playground at the following link: $playgroundLink
////                    """.trimMargin()
//                    val failureWithPlaygroundLink = failure.copy(
//                        cause = AssertionFailedError(
//                            message = originalError.message,
//                            cause = originalError.cause,
//                            expectedValue = originalError.expectedValue,
//                            actualValue = originalError.actualValue,
//                        )
//                    )
//                    failureWithPlaygroundLink
//                } else {
//                    testResult
//                }
//
//            } else {
//                testResult
//            }

        }
    }
}

data class CapturedQuery(
    val stub: StubService,
    val query: TaxiQLQueryString
)

data class PreflightTestCaseContext(val testCase: TestCase) : CoroutineContext.Element {
    override val key = PreflightTestCaseKey
}
