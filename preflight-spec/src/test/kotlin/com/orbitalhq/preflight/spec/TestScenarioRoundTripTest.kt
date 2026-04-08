package com.orbitalhq.preflight.spec

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TestScenarioRoundTripTest : DescribeSpec({

    describe("round-trip") {

        it("round-trips a minimal scenario") {
            val original = TestScenario(
                specVersion = "0.1",
                name = "Find customers",
                description = null,
                schema = "model Customer {\n    id: CustomerId inherits String\n    name: CustomerName inherits String\n}",
                questions = listOf(
                    TestQuestion(
                        questionToAsk = "Find all customers",
                        expectedQuery = "find { Customer[] }",
                        stubs = listOf(
                            Stub(
                                label = "Get Customers",
                                operationName = "getCustomers",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = null,
                                response = """[{ "id": "1", "name": "Alice" }]""",
                                messages = null
                            )
                        )
                    )
                )
            )
            val roundTripped = TestScenarioReader.read(TestScenarioWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a scenario with description") {
            val original = TestScenario(
                specVersion = "0.1",
                name = "Customer Orders",
                description = "Tests customer order queries against the schema.",
                schema = "model Customer {\n    id: CustomerId inherits String\n}",
                questions = listOf(
                    TestQuestion(
                        questionToAsk = "Find customer by ID",
                        expectedQuery = "find { Customer(CustomerId == \"123\") }",
                        stubs = listOf(
                            Stub(
                                label = "Get Customer",
                                operationName = "getCustomer",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = null,
                                response = """{ "id": "123" }""",
                                messages = null
                            )
                        )
                    )
                )
            )
            val roundTripped = TestScenarioReader.read(TestScenarioWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a scenario with multiple questions") {
            val original = TestScenario(
                specVersion = "0.1",
                name = "Multi-question Scenario",
                description = null,
                schema = "model Product {\n    id: ProductId inherits String\n    name: ProductName inherits String\n}",
                questions = listOf(
                    TestQuestion(
                        questionToAsk = "Find all products",
                        expectedQuery = "find { Product[] }",
                        stubs = listOf(
                            Stub(
                                label = "List Products",
                                operationName = "listProducts",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = null,
                                response = """[{ "id": "P1", "name": "Laptop" }]""",
                                messages = null
                            )
                        )
                    ),
                    TestQuestion(
                        questionToAsk = "Find product by ID",
                        expectedQuery = "find { Product(ProductId == \"P1\") }",
                        stubs = listOf(
                            Stub(
                                label = "Get Product",
                                operationName = "getProduct",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = """{ "productId": "P1" }""",
                                response = """{ "id": "P1", "name": "Laptop" }""",
                                messages = null
                            )
                        )
                    )
                )
            )
            val roundTripped = TestScenarioReader.read(TestScenarioWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a scenario with stream stubs") {
            val original = TestScenario(
                specVersion = "0.1",
                name = "Streaming Scenario",
                description = null,
                schema = "model Price {\n    value: PriceValue inherits Decimal\n}",
                questions = listOf(
                    TestQuestion(
                        questionToAsk = "Stream price updates",
                        expectedQuery = "stream { Price }",
                        stubs = listOf(
                            Stub(
                                label = "Price Feed",
                                operationName = "priceFeed",
                                mode = StubMode.STREAM,
                                parameters = null,
                                response = null,
                                messages = listOf("""{ "value": 100.0 }""", """{ "value": 200.0 }""")
                            )
                        )
                    )
                )
            )
            val roundTripped = TestScenarioReader.read(TestScenarioWriter.write(original))
            roundTripped shouldBe original
        }

        it("round-trips a scenario with multiple stubs per question") {
            val original = TestScenario(
                specVersion = "0.1",
                name = "Multi-stub Question",
                description = null,
                schema = "model Order {\n    id: OrderId inherits String\n}",
                questions = listOf(
                    TestQuestion(
                        questionToAsk = "Find orders for customer",
                        expectedQuery = "find { Order[] }",
                        stubs = listOf(
                            Stub(
                                label = "Get Customer",
                                operationName = "getCustomer",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = null,
                                response = """{ "id": "C1" }""",
                                messages = null
                            ),
                            Stub(
                                label = "Get Orders",
                                operationName = "getOrders",
                                mode = StubMode.REQUEST_RESPONSE,
                                parameters = null,
                                response = """[{ "id": "O1" }]""",
                                messages = null
                            )
                        )
                    )
                )
            )
            val roundTripped = TestScenarioReader.read(TestScenarioWriter.write(original))
            roundTripped shouldBe original
        }
    }
})
