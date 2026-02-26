package com.orbitalhq.preflight.spec

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RoundTripTest : DescribeSpec({

    describe("round-trip") {

        it("round-trips a minimal spec with one stub") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Minimal Test",
                description = null,
                query = "find { Customer }",
                dataSources = listOf(
                    Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, """{ "id": "1" }""", null)
                ),
                expectedResult = """{ "id": "1" }""",
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with multiple stubs") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Multi Stub Test",
                description = "Tests with multiple data sources.",
                query = "find { Customer } with { orders: Order[] }",
                dataSources = listOf(
                    Stub("Fetch Customer", "getCustomer", StubMode.REQUEST_RESPONSE, """{ "id": "12345", "name": "Alice" }""", null),
                    Stub("Fetch Orders", "getOrders", StubMode.REQUEST_RESPONSE, """[{ "orderId": "ORD-1" }]""", null)
                ),
                expectedResult = """{ "customer": "Alice", "orders": [{ "orderId": "ORD-1" }] }""",
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with stream message sequence") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Stream Test",
                description = null,
                query = "stream { Prices }",
                dataSources = listOf(
                    Stub(
                        "Price Updates", "priceStream", StubMode.STREAM, null,
                        listOf("""{ "price": 100 }""", """{ "price": 200 }""", """{ "price": 300 }""")
                    )
                ),
                expectedResult = """[{ "price": 100 }, { "price": 200 }, { "price": 300 }]""",
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with optional fields (description and flow)") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Full Test",
                description = "A comprehensive test case.",
                query = "find { Customer }",
                dataSources = listOf(
                    Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, """{ "id": "1" }""", null)
                ),
                expectedResult = """{ "id": "1" }""",
                flow = "sequenceDiagram\n    Q->>S: getCustomer\n    S-->>Q: Customer"
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with TYPED_INSTANCE result format") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "TypedInstance Test",
                description = null,
                query = "find { Customer }",
                dataSources = listOf(
                    Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, """{ "id": "1" }""", null)
                ),
                expectedResult = """{ "type": "Customer", "value": { "id": "1" } }""",
                resultFormat = ResultFormat.TYPED_INSTANCE,
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with default JSON result format") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Plain JSON Test",
                description = null,
                query = "find { Customer }",
                dataSources = listOf(
                    Stub("Get Customer", "getCustomer", StubMode.REQUEST_RESPONSE, """{ "id": "1" }""", null)
                ),
                expectedResult = """{ "id": "1" }""",
                resultFormat = ResultFormat.JSON,
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a spec with mixed stream and request-response stubs") {
            val original = TestSpec(
                specVersion = "0.1",
                name = "Mixed Modes",
                description = null,
                query = "find { Dashboard }",
                dataSources = listOf(
                    Stub("Static Data", "getConfig", StubMode.REQUEST_RESPONSE, """{ "theme": "dark" }""", null),
                    Stub("Live Prices", "priceStream", StubMode.STREAM, null, listOf("""{ "price": 42 }""")),
                    Stub("User Profile", "getUser", StubMode.REQUEST_RESPONSE, """{ "name": "Bob" }""", null)
                ),
                expectedResult = """{ "theme": "dark", "price": 42, "name": "Bob" }""",
                flow = null
            )
            val roundTripped = TestSpecReader.read(TestSpecWriter.write(original))
            roundTripped shouldBe original
        }
    }
})
