import com.orbitalhq.preflight.dsl.OrbitalSpec
import com.orbitalhq.preflight.dsl.PreflightExtension
import com.orbitalhq.preflight.dsl.stub
import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PersonTest : OrbitalSpec({
    describe("Simple tests") {
        it("returning a scalar") {
            """find { 1 + 2 }""".queryForScalar()
                .shouldBe(3)
        }
        it("returning an object") {
            """given { Person = { id: 123, age: 12 } }
                find { PossibleAdult }    
                """.queryForObject()
                .shouldBe(
                    mapOf(
                        "id" to 123,
                        "age" to 12,
                        "isAdult" to false
                    )
                )
        }
        it("can use stdlib functions") {
            """find {
                shouldBeTrue : Boolean = "hello".startsWith("he")
            }
            """.queryForObject()
                .shouldBe(mapOf("shouldBeTrue" to true))
        }
    }


    describe("Tests with stubs") {
        it("should let me customize a stub return value") {
            """
            find { Person(PersonId == 1) } as PossibleAdult
        """.queryForObject(
                stub("getPerson").returns("""{ "id" : 123, "age" : 36 }""")
            )
                .shouldBe(mapOf(
                    "id" to 123,
                    "age" to 36,
                    "isAdult" to true
                ))
        }

    }
})
