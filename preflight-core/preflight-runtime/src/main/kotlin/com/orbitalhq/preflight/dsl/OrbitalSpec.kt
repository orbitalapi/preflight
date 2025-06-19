package com.orbitalhq.preflight.dsl

import com.orbitalhq.stubbing.StubService
import io.kotest.core.spec.style.DescribeSpec

abstract class OrbitalSpec(body: OrbitalSpec.() -> Unit) : DescribeSpec() {

    @Suppress("MemberVisibilityCanBePrivate")
    val preflight = PreflightExtension()

    init {
        extension(preflight)
        this.body()
//        OrbitalContext().body()
    }

    // helper methods
    suspend fun String.queryForScalar(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForScalar(this, stubCustomizer)

    suspend fun String.queryForScalar(vararg stubScenarios: StubScenario) =
        preflight.queryForScalar(this, *stubScenarios)

    suspend fun String.queryForObject(stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForObject(this, stubCustomizer)

    suspend fun String.queryForObject(vararg stubScenarios: StubScenario) =
        preflight.queryForObject(this, *stubScenarios)

    suspend fun String.queryForCollection(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForCollection(this, stubCustomizer)

    suspend fun String.queryForCollection(vararg stubScenarios: StubScenario) =
        preflight.queryForCollection(this, *stubScenarios)

    suspend fun String.queryForTypedInstance(taxiQl: String, stubCustomizer: (StubService) -> Unit = {}) =
        preflight.queryForTypedInstance(this, stubCustomizer)

    suspend fun String.queryForTypedInstance(vararg stubScenarios: StubScenario) =
        preflight.queryForTypedInstance(this, *stubScenarios)
}
