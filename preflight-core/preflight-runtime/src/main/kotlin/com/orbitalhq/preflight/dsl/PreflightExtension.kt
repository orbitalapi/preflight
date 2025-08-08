package com.orbitalhq.preflight.dsl

import com.orbitalhq.SourcePackage
import com.orbitalhq.firstRawObject
import com.orbitalhq.firstRawValue
import com.orbitalhq.firstTypedInstace
import com.orbitalhq.models.TypedInstance
import com.orbitalhq.preflight.dsl.PreflightExtension.Companion.PreflightTestCaseKey
import com.orbitalhq.query.QueryResult
import com.orbitalhq.rawObjects
import com.orbitalhq.schemaServer.core.adaptors.taxi.TaxiSchemaSourcesAdaptor
import com.orbitalhq.schemaServer.core.file.FileProjectSpec
import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.stubbing.StubService
import com.orbitalhq.testVyne
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import lang.taxi.CompilationException
import lang.taxi.TaxiDocument
import lang.taxi.packages.TaxiPackageLoader
import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import com.orbitalhq.schemaServer.core.file.packages.FileSystemPackageLoader
import com.orbitalhq.utils.files.ReactivePollingFileSystemMonitor
import io.kotest.assertions.AssertionFailedError
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.coroutines.withContext
import lang.taxi.query.TaxiQLQueryString
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class PreflightExtension(val projectRoot: Path = Paths.get("./")) : BeforeSpecListener, AfterTestListener,
    TestCaseExtension {

    companion object {
        val PreflightTestCaseKey = object : CoroutineContext.Key<PreflightTestCaseContext> {}
    }

    /**
     * Provides access to the compiled taxi document.
     * This is lower-level than Orbital's schema object
     */
    lateinit var taxi: TaxiDocument
        private set

    lateinit var schema: TaxiSchema
        private set

    lateinit var sourcePackage: SourcePackage
        private set

    /**
     * Provides access to the actual Taxi project (the equivalent of the
     * taxi.conf file)
     */
    lateinit var taxiProject: TaxiPackageProject
        private set

    private val capturedScenarios = mutableMapOf<TestCase, CapturedQuery>()


    override suspend fun beforeSpec(spec: Spec) {
        envVariableContainer.markImmutable()
        withClue("Taxi project should compile without errors") {
            val taxiSchema = try {
                TaxiSchema.from(
                    sourcePackage,
                    onErrorBehaviour = TaxiSchema.Companion.TaxiSchemaErrorBehaviour.THROW_EXCEPTION
                )
            } catch (e: CompilationException) {
                fail("Taxi project has errors: \n${e.message}")
            }

            this.schema = taxiSchema
            this.taxi = taxiSchema.taxi
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
        val packageLoader = FileSystemPackageLoader(spec, TaxiSchemaSourcesAdaptor(), fileMonitor)
        val converter = TaxiSchemaSourcesAdaptor()
        val packageMetadata = converter.buildMetadata(packageLoader)
            .block()!!
        val sourcePackage = converter.convert(packageMetadata, packageLoader).block()!!
        return sourcePackage
    }

    fun orbital(): Pair<Orbital, StubService> {
        return testVyne(this.schema)
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
        return query(taxiQl, stubCustomizer)
            .firstRawValue()
    }


    suspend fun queryForObject(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForObject(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForObject(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): Map<String, Any?> {
        return query(taxiQl, stubCustomizer)
            .firstRawObject()
    }

    suspend fun queryForCollection(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForCollection(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForCollection(
        taxiQl: String,
        stubCustomizer: (StubService) -> Unit = {}
    ): List<Map<String, Any?>> {
        return query(taxiQl, stubCustomizer)
            .rawObjects()
    }

    suspend fun queryForTypedInstance(taxiQl: String, vararg stubScenarios: StubScenario) =
        queryForTypedInstance(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))

    suspend fun queryForTypedInstance(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): TypedInstance {
        return query(taxiQl, stubCustomizer)
            .firstTypedInstace()
    }

    suspend fun query(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): QueryResult {
        val (orbital, stub) = orbital()
        stubCustomizer(stub)
        val testContext = coroutineContext[PreflightTestCaseKey]
        if (testContext != null) {
            capturedScenarios[testContext.testCase] = CapturedQuery(stub, taxiQl)
        } else {
            println("A test is executing without a context - this shouldn't happen")
        }
        return orbital.query(taxiQl)
    }

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult {
        val context = PreflightTestCaseContext(testCase)
        return withContext(context) {
            val testResult = execute(testCase)
            val capturedScenario = capturedScenarios[testCase]
            if (testResult.isErrorOrFailure && capturedScenario != null) {

                val failure = testResult as TestResult.Failure
                val originalError = failure.cause as AssertionFailedError
                val (_, playgroundLink) = PlaygroundScenarioFactory.buildPlaygroundScenario(
                    capturedScenario,
                    sourcePackage,
                    schema,
                    originalError,
                    testCase
                )
                val errorMessageWithPlaygroundLink = """${originalError.message}
                        |
                        |This error is explorable in Taxi Playground at the following link: $playgroundLink
                    """.trimMargin()
                val failureWithPlaygroundLink = failure.copy(
                    cause = AssertionFailedError(
                        message = errorMessageWithPlaygroundLink,
                        cause = originalError.cause,
                        expectedValue = originalError.expectedValue,
                        actualValue = originalError.actualValue,
                    )
                )
                failureWithPlaygroundLink
            } else {
                testResult
            }

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