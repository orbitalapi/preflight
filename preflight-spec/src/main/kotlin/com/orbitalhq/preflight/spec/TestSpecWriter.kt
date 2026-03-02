package com.orbitalhq.preflight.spec

import java.nio.file.Path
import kotlin.io.path.writeText

object TestSpecWriter {

    private val format = markdownFormat<TestSpec>()

    fun write(spec: TestSpec): String = format.write(spec)

    fun writeFile(spec: TestSpec, path: Path) {
        path.writeText(write(spec))
    }
}
