package com.orbitalhq.preflight.dsl

import com.orbitalhq.SourcePackage
import com.orbitalhq.firstRawObject
import com.orbitalhq.firstRawValue
import com.orbitalhq.firstTypedInstace
import com.orbitalhq.models.TypedInstance
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
import java.time.Duration

class PreflightExtension(val projectRoot: Path = Paths.get("./")) : BeforeSpecListener {
    /**
     * Provides access to the compiled taxi document.
     * This is lower-level than Orbital's schema object
     */
    lateinit var taxi: TaxiDocument
        private set

    lateinit var schema: TaxiSchema
        private set

    /**
     * Provides access to the actual Taxi project (the equivalent of the
     * taxi.conf file)
     */
    lateinit var taxiProject: TaxiPackageProject
        private set

    override suspend fun beforeSpec(spec: Spec) {
        val loader = TaxiPackageLoader.forDirectoryContainingTaxiFile(projectRoot.absolute().normalize())
        this.taxiProject = loader.load()

        val sourcePackage = loadSourcePackage(this.taxiProject.packageRootPath!!)

        withClue("Taxi project should compile without errors") {
            val taxiSchema = try {
                TaxiSchema.from(sourcePackage, onErrorBehaviour = TaxiSchema.Companion.TaxiSchemaErrorBehaviour.THROW_EXCEPTION)
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

    fun orbital():Pair<Orbital, StubService> {
        return testVyne(this.schema)
    }

    private fun stubScenariosToCustomizer(scenarios: List<StubScenario>): (StubService) -> Unit {
        return { stub ->
            scenarios.forEach { scenario ->
                stub.addResponse(scenario.operationName, scenario.response)
            }
        }
    }

    suspend fun queryForScalar(taxiQl: String, vararg stubScenarios: StubScenario) = queryForScalar(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))
    suspend fun queryForScalar(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}):Any? {
        return query(taxiQl, stubCustomizer)
            .firstRawValue()
    }


    suspend fun queryForObject(taxiQl: String, vararg stubScenarios: StubScenario) = queryForObject(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))
    suspend fun queryForObject(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}):Map<String,Any?> {
        return query(taxiQl, stubCustomizer)
            .firstRawObject()
    }
    suspend fun queryForCollection(taxiQl: String, vararg stubScenarios: StubScenario) = queryForCollection(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))
    suspend fun queryForCollection(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): List<Map<String, Any?>> {
        return query(taxiQl, stubCustomizer)
            .rawObjects()
    }
    suspend fun queryForTypedInstance(taxiQl: String, vararg stubScenarios: StubScenario) = queryForTypedInstance(taxiQl, stubScenariosToCustomizer(stubScenarios.toList()))
    suspend fun queryForTypedInstance(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}):TypedInstance {
        return query(taxiQl, stubCustomizer)
            .firstTypedInstace()
    }

    suspend fun query(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}): QueryResult {
        val (orbital,stub) = orbital()
        stubCustomizer(stub)
        return orbital.query(taxiQl)
    }
}