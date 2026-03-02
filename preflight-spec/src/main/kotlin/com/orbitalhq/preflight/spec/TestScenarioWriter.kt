package com.orbitalhq.preflight.spec

import java.nio.file.Path
import kotlin.io.path.writeText

object TestScenarioWriter {

    private val format = markdownFormat<TestScenario>()

    fun write(scenario: TestScenario): String = format.write(scenario)

    fun writeFile(scenario: TestScenario, path: Path) {
        path.writeText(write(scenario))
    }
}
