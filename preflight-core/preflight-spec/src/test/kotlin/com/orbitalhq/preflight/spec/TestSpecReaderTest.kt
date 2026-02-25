package com.orbitalhq.preflight.spec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class TestSpecReaderTest : DescribeSpec({

    describe("happy path") {

        it("parses a minimal valid spec") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Minimal Test
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

            val spec = TestSpecReader.read(markdown)
            spec.specVersion shouldBe "0.1"
            spec.name shouldBe "Minimal Test"
            spec.description.shouldBeNull()
            spec.query shouldBe "find { Customer }"
            spec.dataSources shouldHaveSize 1
            spec.dataSources[0].label shouldBe "Get Customer"
            spec.dataSources[0].operationName shouldBe "getCustomer"
            spec.dataSources[0].mode shouldBe StubMode.REQUEST_RESPONSE
            spec.dataSources[0].response shouldBe """{ "id": "1" }"""
            spec.expectedResult shouldBe """{ "id": "1" }"""
            spec.flow.shouldBeNull()
        }

        it("parses a full spec with multiple stubs") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Customer Order Flow
                |
                |This test verifies the customer order query.
                |
                |## Query
                |
                |```taxiql
                |find { Customer(customerId == "12345") } with { orders: Order[] }
                |```
                |
                |## Data Sources
                |
                |### Fetch Customer Details
                |<!-- operation: getCustomer -->
                |
                |Response:
                |```json
                |{ "id": "12345", "name": "Alice Smith" }
                |```
                |
                |### List Customer Orders
                |<!-- operation: getOrdersForCustomer -->
                |
                |Response:
                |```json
                |[{ "orderId": "ORD-99", "status": "confirmed" }]
                |```
                |
                |## Expected Result
                |
                |```json
                |{ "customer": { "name": "Alice Smith" }, "orders": [{ "id": "ORD-99" }] }
                |```
                |
                |## Flow
                |
                |```mermaid
                |sequenceDiagram
                |    participant Q as Query Engine
                |    Q->>C: getCustomer
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.name shouldBe "Customer Order Flow"
            spec.description shouldBe "This test verifies the customer order query."
            spec.dataSources shouldHaveSize 2
            spec.dataSources[0].operationName shouldBe "getCustomer"
            spec.dataSources[1].operationName shouldBe "getOrdersForCustomer"
            spec.flow.shouldNotBeNull()
            spec.flow shouldContain "sequenceDiagram"
        }

        it("parses description prose between H1 and first H2") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# My Test
                |
                |This is the description.
                |
                |It spans multiple paragraphs.
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
                |```json
                |{}
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.description shouldBe "This is the description.\n\nIt spans multiple paragraphs."
        }

        it("parses Flow section when present") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test With Flow
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
                |```json
                |{}
                |```
                |
                |## Flow
                |
                |```mermaid
                |sequenceDiagram
                |    Q->>S: getFoo
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.flow.shouldNotBeNull()
            spec.flow shouldContain "sequenceDiagram"
        }

        it("parses stream-mode stub with multiple messages") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Stream Test
                |
                |## Query
                |
                |```taxiql
                |stream { Prices }
                |```
                |
                |## Data Sources
                |
                |### Price Stream
                |<!-- operation: priceStream, mode: stream -->
                |
                |Message:
                |```json
                |{ "price": 100 }
                |```
                |
                |Message:
                |```json
                |{ "price": 200 }
                |```
                |
                |## Expected Result
                |
                |```json
                |[{ "price": 100 }, { "price": 200 }]
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.dataSources[0].mode shouldBe StubMode.STREAM
            spec.dataSources[0].messages!! shouldHaveSize 2
            spec.dataSources[0].messages!![0] shouldBe """{ "price": 100 }"""
            spec.dataSources[0].messages!![1] shouldBe """{ "price": 200 }"""
            spec.dataSources[0].response.shouldBeNull()
        }

        it("parses stream-mode stub with single message") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Stream Single
                |
                |## Query
                |
                |```taxiql
                |stream { Prices }
                |```
                |
                |## Data Sources
                |
                |### Price Stream
                |<!-- operation: priceStream, mode: stream -->
                |
                |Message:
                |```json
                |{ "price": 100 }
                |```
                |
                |## Expected Result
                |
                |```json
                |[{ "price": 100 }]
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.dataSources[0].mode shouldBe StubMode.STREAM
            spec.dataSources[0].messages!! shouldHaveSize 1
        }

        it("parses request-response stub as default mode") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Default Mode
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
                |```json
                |{}
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.dataSources[0].mode shouldBe StubMode.REQUEST_RESPONSE
        }

        it("ignores unrecognised H2 sections") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Notes
                |
                |Some notes that should be ignored.
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
                |```json
                |{}
                |```
                |
                |## Changelog
                |
                |- v1: Initial version
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.name shouldBe "Test"
            spec.dataSources shouldHaveSize 1
        }

        it("ignores prose and notes between recognised sections") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |Some explanation of the query.
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
                |```json
                |{}
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.query shouldBe "find { Foo }"
        }

        it("ignores unrecognised labels within stub sections") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
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
                |Description:
                |```text
                |This is a description that should be ignored.
                |```
                |
                |Response:
                |```json
                |{ "ok": true }
                |```
                |
                |## Expected Result
                |
                |```json
                |{ "ok": true }
                |```
            """.trimMargin()

            val spec = TestSpecReader.read(markdown)
            spec.dataSources[0].response shouldBe """{ "ok": true }"""
        }
    }

    describe("error cases") {

        it("fails with clear message when front matter is missing") {
            val markdown = """
                |# No Front Matter
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "Missing front matter"
        }

        it("fails with clear message when spec-version is missing from front matter") {
            val markdown = """
                |---
                |author: someone
                |---
                |
                |# Test
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "spec-version"
        }

        it("fails with clear message when spec-version is unsupported") {
            val markdown = """
                |---
                |spec-version: 0.3
                |---
                |
                |# Test
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "Unsupported spec version"
            ex.message shouldContain "0.3"
        }

        it("fails with clear message when H1 heading is missing") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
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
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "Missing H1 heading"
        }

        it("fails with clear message when ## Query section is missing") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
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
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "## Query"
        }

        it("fails with clear message when ## Data Sources section is missing") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Expected Result
                |
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "## Data Sources"
        }

        it("fails with clear message when ## Expected Result section is missing") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
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
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "## Expected Result"
        }

        it("fails with clear message when a stub has no directive") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Data Sources
                |
                |### My Stub
                |
                |Response:
                |```json
                |{}
                |```
                |
                |## Expected Result
                |
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "missing a source directive"
        }

        it("fails with clear message when directive is missing operation name") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Data Sources
                |
                |### My Stub
                |<!-- mode: stream -->
                |
                |Message:
                |```json
                |{}
                |```
                |
                |## Expected Result
                |
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "operation"
        }

        it("fails with clear message when stream stub has no Message blocks") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |stream { Prices }
                |```
                |
                |## Data Sources
                |
                |### Price Stream
                |<!-- operation: priceStream, mode: stream -->
                |
                |## Expected Result
                |
                |```json
                |[]
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "Stream-mode stub must have at least one Message"
        }

        it("error messages include the filename when provided") {
            val markdown = """
                |# No Front Matter
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown, filename = "my-test.md")
            }
            ex.message shouldContain "my-test.md"
        }

        it("error messages include the section name when applicable") {
            val markdown = """
                |---
                |spec-version: 0.1
                |---
                |
                |# Test
                |
                |## Query
                |
                |```taxiql
                |find { Foo }
                |```
                |
                |## Data Sources
                |
                |### Bad Stub
                |
                |Response:
                |```json
                |{}
                |```
                |
                |## Expected Result
                |
                |```json
                |{}
                |```
            """.trimMargin()

            val ex = shouldThrow<SpecParseException> {
                TestSpecReader.read(markdown)
            }
            ex.message shouldContain "Data Sources"
        }
    }
})
