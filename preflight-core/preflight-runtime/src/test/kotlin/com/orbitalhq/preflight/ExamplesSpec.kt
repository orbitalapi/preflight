package com.orbitalhq.preflight

import app.cash.turbine.test
import com.orbitalhq.preflight.dsl.OrbitalSpec
import com.orbitalhq.preflight.dsl.forSchema
import com.orbitalhq.stubbing.ResponseEmitter
import kotlin.time.Duration.Companion.seconds
import com.orbitalhq.expectMap
import io.kotest.matchers.shouldBe

class ExamplesSpec : OrbitalSpec(
    {

        describe("Examples") {
            it("can run a saved streaming query") {
                var eventEmitter: ResponseEmitter? = null
                val resultStream = runNamedQueryForStream("StreamPerson") { stubService ->
                    eventEmitter = stubService.addResponseEmitter("clickEvents")
                }
                resultStream.test(timeout = 10.seconds) {
                    eventEmitter!!.next("""{ "id" : "hi-1" }""")
                    val next = expectMap()
                    next.shouldBe(mapOf("personId" to "hi-1"))
                }
            }
        }
    }, forSchema(
        """
    model PersonClickedEvent {
       id : PersonId inherits String
    }
    model Person {
        personId : PersonId
    }
    service PersonApi {
        stream clickEvents : Stream<PersonClickedEvent>
        write operation upsertPerson(Person)
    }
    query StreamPerson {
        stream { PersonClickedEvent }
        call PersonApi::upsertPerson
    }
""".trimIndent()
    )
)