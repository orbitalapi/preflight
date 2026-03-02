package com.orbitalhq.preflight.spec

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MarkdownFormatTest : DescribeSpec({

    describe("MarkdownFormat") {

        it("reads and writes TestSpec via annotation-driven format") {
            val format = markdownFormat<TestSpec>()
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Simple Test
                |
                |## Query
                |
                |```taxiql
                |find { Customer }
                |```
                |
                |## Data Sources
                |
                |### Get Customer
                |<!-- operation: getCustomer -->
                |
                |Response:
                |```json
                |{ "id": "1" }
                |```
                |
                |## Expected Result
                |
                |```json
                |{ "id": "1" }
                |```
            """.trimMargin()

            val spec = format.read(markdown)
            spec.name shouldBe "Simple Test"
            spec.query shouldBe "find { Customer }"
            spec.dataSources[0].label shouldBe "Get Customer"
            spec.dataSources[0].operationName shouldBe "getCustomer"
            spec.expectedResult shouldBe """{ "id": "1" }"""
        }

        it("reads and writes TestScenario via annotation-driven format") {
            val format = markdownFormat<TestScenario>()
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Customer Scenario
                |
                |## Schema
                |
                |```taxi
                |model Customer {
                |    id: CustomerId inherits String
                |}
                |```
                |
                |## Questions
                |
                |### Find all customers
                |
                |Expected Query:
                |```taxiql
                |find { Customer[] }
                |```
                |
                |#### Data Sources
                |
                |##### Get Customers
                |<!-- operation: getCustomers -->
                |
                |Response:
                |```json
                |[{ "id": "1" }]
                |```
                |
            """.trimMargin()

            val scenario = format.read(markdown)
            scenario.name shouldBe "Customer Scenario"
            scenario.schema shouldBe "model Customer {\n    id: CustomerId inherits String\n}"
            scenario.questions[0].questionToAsk shouldBe "Find all customers"
            scenario.questions[0].expectedQuery shouldBe "find { Customer[] }"
            scenario.questions[0].stubs[0].label shouldBe "Get Customers"
            scenario.questions[0].stubs[0].operationName shouldBe "getCustomers"
        }

        it("round-trips enum directives with convention") {
            MarkdownFormat.enumToDirectiveValue(StubMode.REQUEST_RESPONSE) shouldBe "request-response"
            MarkdownFormat.enumToDirectiveValue(StubMode.STREAM) shouldBe "stream"
            MarkdownFormat.directiveValueToEnum("request-response", StubMode::class) shouldBe StubMode.REQUEST_RESPONSE
            MarkdownFormat.directiveValueToEnum("stream", StubMode::class) shouldBe StubMode.STREAM
        }

        it("handles CodeBlockQualifier during read") {
            val format = markdownFormat<TestSpec>()
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Qualifier Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Data Sources
                |
                |### Stub
                |<!-- operation: getFoo -->
                |
                |Response:
                |```json
                |{}
                |```
                |
                |## Expected Result
                |
                |```json typedInstance
                |{ "type": "Foo", "value": {} }
                |```
            """.trimMargin()

            val spec = format.read(markdown)
            spec.resultFormat shouldBe ResultFormat.TYPED_INSTANCE
        }

        it("handles CodeBlockQualifier during write") {
            val format = markdownFormat<TestSpec>()
            val spec = TestSpec(
                name = "Test",
                description = null,
                query = "find { Foo }",
                dataSources = listOf(
                    Stub("S", "getFoo", StubMode.REQUEST_RESPONSE, null, "{}", null)
                ),
                expectedResult = "{}",
                resultFormat = ResultFormat.TYPED_INSTANCE,
                flow = null
            )
            val output = format.write(spec)
            output.contains("```json typedInstance") shouldBe true
        }
    }
})
