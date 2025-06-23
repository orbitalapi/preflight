import com.orbitalhq.preflight.dsl.OrbitalSpec
import io.kotest.matchers.booleans.shouldBeTrue

class MixedSourcesSpec : OrbitalSpec({
    describe("A Mixed Source project") {
        it("should transpile sources from OAS and Avro schemas") {
            // Avro
            schema.hasType("movies.Film").shouldBeTrue()
            // OpenAPI
            schema.hasType("petstore.Pet").shouldBeTrue()
        }
    }
})