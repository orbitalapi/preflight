import com.orbitalhq.preflight.dsl.OrbitalSpec
import io.kotest.matchers.shouldBe

class OverridingGlobalEnvVariablesSpec : OrbitalSpec({

    env("click-events-topic-name", "clickstream")
    describe("overriding global env config variables") {
        it("should compile using the overridden env variable") {
            schema.service("com.foo.PersonEventsKafkaService")
                .streamOperations
                .single()
                .firstMetadata("com.orbitalhq.kafka.KafkaOperation")
                .params["topic"]
                .shouldBe("clickstream")
        }
    }
})