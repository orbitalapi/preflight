package com.orbitalhq.preflight.spec

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

object TestScenarioReader {

    private val format = markdownFormat<TestScenario>()

    fun read(markdown: String, filename: String? = null): TestScenario =
        format.read(markdown, filename)

    fun readFile(path: Path): TestScenario = read(path.readText(), filename = path.name)
}
