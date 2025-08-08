package com.orbitalhq.preflight.dsl

interface EnvVariableContainer {
    fun environmentVariables(vararg pairs: Pair<String, String>)
    fun environmentVariables(env: Map<String, String>)
    fun env(key: String, value: String)
    fun markImmutable()

    val envVariables:Map<String,String>
}

class SpecEnvVariables private constructor(globalVariables:Map<String,String>) : EnvVariableContainer  {
    private val variables = globalVariables.toMutableMap()
    private var isMutable = true
    companion object {
        fun newInstance():SpecEnvVariables {
            return SpecEnvVariables(EnvVariablesLoader.loadGlobalEnvVariables())
        }
    }

    override val envVariables: Map<String, String>
        get() {
            return variables.toMap()
        }

    override fun markImmutable() {
        this.isMutable = false
    }

    private fun requireIsMutable() {
        require(isMutable) { "Modifying env variables after the test has started is not allowed. Configure the env variables before the describe { ... } block"}
    }
    /**
     * Configure environment variables for this specific test spec.
     * These will override any global defaults with the same key.
     */
    override fun environmentVariables(vararg pairs: Pair<String, String>) {
        requireIsMutable()
        variables.putAll(pairs)
    }

    override fun environmentVariables(env: Map<String, String>) {
        requireIsMutable()
        variables.putAll(env)
    }

    override fun env(key: String, value: String) {
        requireIsMutable()
        variables[key] = value
    }
}