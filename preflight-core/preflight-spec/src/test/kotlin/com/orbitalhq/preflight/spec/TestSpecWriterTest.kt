package com.orbitalhq.preflight.spec

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class TestSpecWriterTest : DescribeSpec({

    describe("TestSpecWriter") {

        it("writes front matter with spec version") {
            val spec = minimalSpec()
            val output = TestSpecWriter.write(spec)
            output shouldContain "---\nspec-version: 0.1\n---"
        }

        it("writes minimal spec with correct heading structure") {
            val spec = minimalSpec()
            val output = TestSpecWriter.write(spec)
            output shouldContain "# Minimal Test"
            output shouldContain "## Query"
            output shouldContain "## Data Sources"
            output shouldContain "### Get Customer"
            output shouldContain "## Expected Result"
        }

        it("writes spec with multiple stubs") {
            val spec = minimalSpec().copy(
                dataSources = listOf(
                    Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, parameters = null, response = """{ "id": "1" }""", messages = null),
                    Stub("Get Orders", "getOrders", StubMode.REQUEST_RESPONSE, parameters = null, response = """[{ "orderId": "A" }]""", messages = null)
                )
            )
            val output = TestSpecWriter.write(spec)
            output shouldContain "### Get Customer"
            output shouldContain "<!-- operation: getCustomer -->"
            output shouldContain "### Get Orders"
            output shouldContain "<!-- operation: getOrders -->"
        }

        it("writes stream-mode stub with mode directive present") {
            val spec = minimalSpec().copy(
                dataSources = listOf(
                    Stub("Price Stream", "priceStream", StubMode.STREAM, parameters = null, response = null, messages = listOf("""{ "price": 100 }"""))
                )
            )
            val output = TestSpecWriter.write(spec)
            output shouldContain "<!-- operation: priceStream, mode: stream -->"
            output shouldContain "Message:"
        }

        it("writes request-response stub without mode directive") {
            val spec = minimalSpec()
            val output = TestSpecWriter.write(spec)
            output shouldContain "<!-- operation: getCustomer -->"
            output shouldNotContain "mode:"
        }

        it("writes spec with description") {
            val spec = minimalSpec().copy(description = "This test verifies customer lookup.")
            val output = TestSpecWriter.write(spec)
            output shouldContain "This test verifies customer lookup."
        }

        it("writes spec without description") {
            val spec = minimalSpec().copy(description = null)
            val output = TestSpecWriter.write(spec)
            // Should go straight from H1 to ## Query without extra prose
            val lines = output.lines()
            val h1Index = lines.indexOfFirst { it.startsWith("# ") }
            val queryIndex = lines.indexOfFirst { it == "## Query" }
            // Between H1 and Query, there should only be blank lines
            val between = lines.subList(h1Index + 1, queryIndex)
            between.all { it.isBlank() }
        }

        it("writes spec with flow section") {
            val spec = minimalSpec().copy(flow = "sequenceDiagram\n    Q->>S: getFoo")
            val output = TestSpecWriter.write(spec)
            output shouldContain "## Flow"
            output shouldContain "```mermaid"
            output shouldContain "sequenceDiagram"
        }

        it("writes spec without flow section") {
            val spec = minimalSpec().copy(flow = null)
            val output = TestSpecWriter.write(spec)
            output shouldNotContain "## Flow"
            output shouldNotContain "```mermaid"
        }

        it("writes plain json info string for JSON format") {
            val spec = minimalSpec()
            val output = TestSpecWriter.write(spec)
            output shouldContain "```json\n{ \"id\": \"1\" }\n```"
            output shouldNotContain "typedInstance"
        }

        it("writes json typedInstance info string for TYPED_INSTANCE format") {
            val spec = minimalSpec().copy(
                resultFormat = ResultFormat.TYPED_INSTANCE,
                expectedResult = """{ "type": "Customer", "value": { "id": "1" } }"""
            )
            val output = TestSpecWriter.write(spec)
            output shouldContain "```json typedInstance"
        }

        it("writes Request block before Response when parameters are present") {
            val spec = minimalSpec().copy(
                dataSources = listOf(
                    Stub("Get Product", "getProduct", StubMode.REQUEST_RESPONSE,
                        parameters = """{ "productId": "PROD-1001" }""",
                        response = """{ "productId": "PROD-1001", "name": "Laptop" }""",
                        messages = null)
                )
            )
            val output = TestSpecWriter.write(spec)
            output shouldContain "Request:"
            output shouldContain "Response:"
            val requestIdx = output.indexOf("Request:")
            val responseIdx = output.indexOf("Response:")
            assert(requestIdx < responseIdx) { "Request should appear before Response" }
        }

        it("omits Request block when parameters are null") {
            val spec = minimalSpec()
            val output = TestSpecWriter.write(spec)
            output shouldNotContain "Request:"
        }

        it("writes stream stub with multiple messages in order") {
            val spec = minimalSpec().copy(
                dataSources = listOf(
                    Stub(
                        "Prices", "priceStream", StubMode.STREAM, parameters = null, response = null,
                        messages = listOf("""{ "price": 100 }""", """{ "price": 200 }""", """{ "price": 300 }""")
                    )
                )
            )
            val output = TestSpecWriter.write(spec)
            val messageIndices = Regex("Message:").findAll(output).map { it.range.first }.toList()
            messageIndices.size shouldBe 3
            // Verify order by checking the prices appear in order
            val firstPrice = output.indexOf("100")
            val secondPrice = output.indexOf("200")
            val thirdPrice = output.indexOf("300")
            assert(firstPrice < secondPrice && secondPrice < thirdPrice) {
                "Messages should appear in order"
            }
        }

        it("writes stream stub with single message") {
            val spec = minimalSpec().copy(
                dataSources = listOf(
                    Stub("Stream", "myStream", StubMode.STREAM, parameters = null, response = null, messages = listOf("""{ "value": 1 }"""))
                )
            )
            val output = TestSpecWriter.write(spec)
            val messageCount = Regex("Message:").findAll(output).count()
            messageCount shouldBe 1
        }
    }
})

private fun minimalSpec() = TestSpec(
    specVersion = "0.1",
    name = "Minimal Test",
    description = null,
    query = "find { Customer }",
    dataSources = listOf(
        Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, parameters = null, response = """{ "id": "1" }""", messages = null)
    ),
    expectedResult = """{ "id": "1" }""",
    flow = null
)
