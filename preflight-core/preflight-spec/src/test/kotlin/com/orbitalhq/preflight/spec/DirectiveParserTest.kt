package com.orbitalhq.preflight.spec

import com.orbitalhq.preflight.spec.internal.DirectiveParser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class DirectiveParserTest : DescribeSpec({

    describe("DirectiveParser.parse") {

        it("parses directive with operation name") {
            val result = DirectiveParser.parse("<!-- operation: getCustomer -->")
            result shouldContainExactly mapOf("operation" to "getCustomer")
        }

        it("parses directive with operation and mode") {
            val result = DirectiveParser.parse("<!-- operation: priceStream, mode: stream -->")
            result shouldContainExactly mapOf("operation" to "priceStream", "mode" to "stream")
        }

        it("handles extra whitespace in keys and values") {
            val result = DirectiveParser.parse("<!--   operation:   getCustomer  ,  mode:   stream   -->")
            result shouldContainExactly mapOf("operation" to "getCustomer", "mode" to "stream")
        }

        it("handles directive with trailing comma gracefully") {
            val result = DirectiveParser.parse("<!-- operation: getCustomer, -->")
            result shouldContainExactly mapOf("operation" to "getCustomer")
        }

        it("returns empty map for non-directive HTML comment") {
            val result = DirectiveParser.parse("<!-- This is just a comment -->")
            result.shouldBeEmpty()
        }

        it("ignores non-directive HTML comments (plain prose)") {
            val result = DirectiveParser.parse("<!-- This is just a plain note about the test -->")
            result.shouldBeEmpty()
        }

        it("returns empty map for empty comment") {
            val result = DirectiveParser.parse("<!---->")
            result.shouldBeEmpty()
        }
    }

    describe("DirectiveParser.parseAll") {

        it("merges multiple directive comments") {
            val result = DirectiveParser.parseAll(
                listOf(
                    "<!-- operation: priceStream -->",
                    "<!-- mode: stream -->"
                )
            )
            result shouldContainExactly mapOf("operation" to "priceStream", "mode" to "stream")
        }
    }
})
