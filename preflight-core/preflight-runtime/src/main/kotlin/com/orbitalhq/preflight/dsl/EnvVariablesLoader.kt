package com.orbitalhq.preflight.dsl

import com.typesafe.config.ConfigFactory

/**
 * Loads global env variables from test-resources/env.conf
 */
object EnvVariablesLoader {

    fun loadGlobalEnvVariables(): Map<String, String> {
        return ConfigFactory.load("env.conf")
            .root()
            .unwrapped()
            .mapValues { (_,value) -> value.toString() }
    }

    private fun tryLoadFromClasspath(): java.io.InputStream? {
        return PreflightExtension::class.java.getResourceAsStream("env.conf")
    }


}