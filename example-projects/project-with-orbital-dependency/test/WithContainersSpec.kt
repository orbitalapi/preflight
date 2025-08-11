import app.cash.turbine.test
import com.orbitalhq.expectMap
import com.orbitalhq.expectRawMap
import com.orbitalhq.preflight.dsl.OrbitalSpec
import com.orbitalhq.preflight.dsl.containers.kafka.KafkaContainerSupport
import com.orbitalhq.preflight.dsl.containers.kafka.kafkaContainer
import io.kotest.matchers.shouldBe

class WithContainersSpec : OrbitalSpec({
    withContainers(
        kafkaContainer("quotes-kafka")
    )
    describe("test with containers") {
        it("should execute a test using the container") {
            val kafkaContainer = containerForConnection<KafkaContainerSupport>("quotes-kafka")
            queryForStreamOfObjects("""
                stream { StockQuote }
            """.trimIndent()).test {
                kafkaContainer.sendMessage("""{ "ticker" : "AAPL" }""", "stockPrices")
                val next = expectRawMap()
                next.shouldBe(mapOf("ticker" to "AAPL"))
            }
        }
    }
})