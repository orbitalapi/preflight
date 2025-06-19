package com.orbitalhq.preflight.dsl

import com.orbitalhq.asSourcePackage
import com.orbitalhq.firstRawObject
import com.orbitalhq.firstRawValue
import com.orbitalhq.firstTypedInstace
import com.orbitalhq.models.TypedInstance
import com.orbitalhq.query.QueryResult
import com.orbitalhq.rawObjects
import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.stubbing.StubService
import com.orbitalhq.testVyne
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.coroutines.flow.Flow
import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.errors
import lang.taxi.packages.TaxiPackageLoader
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.TaxiSourcesLoader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString

class PreflightExtension(val projectRoot: Path = Paths.get("./")) : BeforeSpecListener {
    /**
     * Provides access to the compiled taxi document.
     * This is lower-level than Orbital's schema object
     */
    lateinit var taxi: TaxiDocument
        private set;

    lateinit var schema: TaxiSchema
        private set;
    /**
     * Provides access to the actual Taxi project (the equivalent of the
     * taxi.conf file)
     */
    lateinit var taxiProject: TaxiPackageProject
        private set;

    override suspend fun beforeSpec(spec: Spec) {
        val loader = TaxiPackageLoader.forDirectoryContainingTaxiFile(projectRoot.absolute().normalize())
        this.taxiProject = loader.load()

        val taxiSources = TaxiSourcesLoader.loadPackageAndDependencies(projectRoot)


        withClue("Taxi project should compile without errors") {
            val (errors, taxi) = Compiler(taxiSources)
                .compileWithMessages()
            if (errors.errors().isNotEmpty()) {
                fail("Taxi project has errors: \n${CompilationException(errors).message}")
            }
            this.taxi = taxi
            val sourcePackage = taxiSources.asSourcePackage()
            this.schema = TaxiSchema(taxi, listOf(sourcePackage))
        }
    }

    fun orbital(taxi:TaxiDocument = this.taxi):Pair<Orbital, StubService> {
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