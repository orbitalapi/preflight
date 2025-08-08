package com.orbitalhq.preflight.dsl

import com.orbitalhq.schemas.taxi.TaxiSchema
import com.orbitalhq.stubbing.StubService
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.TaxiDocument
import lang.taxi.packages.TaxiPackageProject

/**
 * Base class for writing Orbital tests using Preflight.
 * Extends Kotest's DescribeSpec to provide testing capabilities with Orbital-specific functionality.
 * 
 * This class provides extension methods for executing queries against Orbital services
 * with optional stubbing capabilities for external dependencies.
 * 
 * @param body The test specification body that defines the test structure
 */
abstract class OrbitalSpec(body: OrbitalSpec.() -> Unit) : DescribeSpec() {

    @Suppress("MemberVisibilityCanBePrivate")
    val preflight = PreflightExtension()

    init {
        extension(preflight)
        this.body()
    }

    fun environmentVariables(vararg pairs: Pair<String, String>) = preflight.environmentVariables(*pairs)
    fun environmentVariables(env: Map<String, String>) = preflight.environmentVariables(env)
    fun env(key: String, value: String) = preflight.env(key,value)

    /**
     * Provides access to the compiled taxi document.
     * This is lower-level than Orbital's schema object
     */
    val taxi: TaxiDocument
        get() {
            return preflight.taxi
        }

    /**
     * Returns the Orbital schema - which provides a higher-level
     * API for interacting with the Taxi schema
     */
    val schema: TaxiSchema
        get() {
            return preflight.schema
        }

    /**
     * Provides access to the Taxi Package Project - taxi's taxi.conf file,
     * and source roots etc.
     */
    val taxiProject: TaxiPackageProject
        get() {
            return preflight.taxiProject
        }
    /**
     * Executes the query, and returns a raw scalar value (Int, String, Boolean, etc).
     * Optionally accepts a callback for customizing the Stub service, which allows for
     * fine-grained control of how stubs are configured.
     *
     * For most use-cases, the stubScenarios overload proivded a more convenient way of configuring stubs
     */
    suspend fun String.queryForScalar(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForScalar(this, stubCustomizer)

    /**
     * Executes the query and returns a raw scalar value (Int, String, Boolean, etc).
     * This overload accepts stub scenarios for convenient stubbing configuration.
     * 
     * @param stubScenarios Variable number of stub scenarios to configure service behavior
     * @return The scalar result of the query execution
     */
    suspend fun String.queryForScalar(vararg stubScenarios: StubScenario) =
        preflight.queryForScalar(this, *stubScenarios)

    /**
     * Executes the query and returns a single object/entity.
     * Accepts a callback for customizing the Stub service, which allows for
     * fine-grained control of how stubs are configured.
     * 
     * @param stubCustomizer Optional callback to customize stub service configuration
     * @return The object result of the query execution
     */
    suspend fun String.queryForObject(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForObject(this, stubCustomizer)

    /**
     * Executes the query and returns a single object/entity.
     * This overload accepts stub scenarios for convenient stubbing configuration.
     * 
     * @param stubScenarios Variable number of stub scenarios to configure service behavior
     * @return The object result of the query execution
     */
    suspend fun String.queryForObject(vararg stubScenarios: StubScenario) =
        preflight.queryForObject(this, *stubScenarios)

    /**
     * Executes the query and returns a collection of objects/entities.
     * Accepts a callback for customizing the Stub service, which allows for
     * fine-grained control of how stubs are configured.
     * 
     * @param stubCustomizer Optional callback to customize stub service configuration
     * @return The collection result of the query execution
     */
    suspend fun String.queryForCollection(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForCollection(this, stubCustomizer)

    /**
     * Executes the query and returns a collection of objects/entities.
     * This overload accepts stub scenarios for convenient stubbing configuration.
     * 
     * @param stubScenarios Variable number of stub scenarios to configure service behavior
     * @return The collection result of the query execution
     */
    suspend fun String.queryForCollection(vararg stubScenarios: StubScenario) =
        preflight.queryForCollection(this, *stubScenarios)

    /**
     * Executes the query and returns a strongly-typed instance based on the query's return type.
     * Accepts a callback for customizing the Stub service, which allows for
     * fine-grained control of how stubs are configured.
     * 
     * @param stubCustomizer Optional callback to customize stub service configuration
     * @return The typed instance result of the query execution
     */
    suspend fun String.queryForTypedInstance(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForTypedInstance(this, stubCustomizer)

    /**
     * Executes the query and returns a strongly-typed instance based on the query's return type.
     * This overload accepts stub scenarios for convenient stubbing configuration.
     * 
     * @param stubScenarios Variable number of stub scenarios to configure service behavior
     * @return The typed instance result of the query execution
     */
    suspend fun String.queryForTypedInstance(vararg stubScenarios: StubScenario) =
        preflight.queryForTypedInstance(this, *stubScenarios)
}
