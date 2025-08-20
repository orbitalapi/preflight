import app.cash.turbine.test
import com.orbitalhq.expectMap
import com.orbitalhq.expectRawMap
import com.orbitalhq.preflight.dsl.OrbitalSpec
import com.orbitalhq.preflight.dsl.containers.kafka.KafkaContainerSupport
import com.orbitalhq.preflight.dsl.containers.kafka.kafkaContainer
import com.orbitalhq.preflight.dsl.containers.mongo.mongoConnector
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class WithContainersSpec : OrbitalSpec({
    withContainers(
        kafkaContainer("quotes-kafka"),
        mongoConnector("quotes-mongo")
    )
    describe("test with containers") {
        it("should stream data from a Kafka container") {
            val kafkaContainer = containerForConnection<KafkaContainerSupport>("quotes-kafka")
            queryForStreamOfMaps(
                """
                stream { StockQuote }
            """.trimIndent()
            ).test {
                kafkaContainer.sendMessage("""{ "ticker" : "AAPL" }""", "stockPrices")
                val next = expectRawMap()
                next.shouldBe(mapOf("ticker" to "AAPL"))
            }
        }

        it("should stream data from Kafka into Mongo") {
            val kafkaContainer = containerForConnection<KafkaContainerSupport>("quotes-kafka")
            queryForStreamOfMaps(
                """
                stream { StockQuote }
                call MongoQuotesService::insertQuote
            """.trimIndent()
            ).test(timeout = 5.seconds) {
                kafkaContainer.sendMessage("""{ "ticker" : "AAPL" }""", "stockPrices")
                val next = expectRawMap()
                next.shouldBe(mapOf("symbol" to "AAPL"))
            }
        }
    }
})