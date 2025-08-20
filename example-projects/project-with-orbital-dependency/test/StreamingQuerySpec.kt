import app.cash.turbine.test
import com.orbitalhq.preflight.dsl.OrbitalSpec
import kotlin.time.Duration.Companion.seconds
import com.orbitalhq.expectMap
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class StreamingQuerySpec : OrbitalSpec({
    describe("Project with Orbital dependencies") {
        it("can unit test a streaming query") {
            runNamedQueryForStreamOfTypedInstances("com.foo.StreamPersonEventsToMongo") { stubService ->
                stubService.addResponseEmitter("clickEvents")
                    .next(
                        """{
                        | "id" : "jimmy"
                        |}
                    """.trimMargin()
                    )
                stubService.addResponseReturningInputs("upsertPerson")
            }
                .test(5.seconds) {
                    val next = expectMap()
                    next.shouldNotBeNull()
                    next.shouldBe(mapOf("personId" to "jimmy"))
                }
        }
        it("resolves the env variables from env.conf") {
            schema.service("com.foo.PersonEventsKafkaService")
                .streamOperations
                .single()
                .firstMetadata("com.orbitalhq.kafka.KafkaOperation")
                .params["topic"]
                .shouldBe("click-clack")

        }
    }
})
