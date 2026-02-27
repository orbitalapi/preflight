# preflight-spec

A standalone library for reading and writing Preflight test spec files. These are markdown files — one test per file — that serve as both living documentation and machine-parseable test specifications.

This module has **no dependency on Orbital or preflight-runtime**. It parses markdown to a data model and generates markdown from a data model. The bridge to Preflight's test execution lives in `preflight-runtime`.

## Spec format

Every spec file starts with YAML front matter declaring the version, followed by a strict heading structure:

```markdown
---
spec-version: 0.1
---

# Customer Order Flow

Optional description of what this test covers.

## Query

` ``taxiql
find { Customer(customerId == "12345") } with { orders: Order[] }
` ``

## Data Sources

### Fetch Customer Details
<!-- operation: getCustomer -->

Response:
` ``json
{ "id": "12345", "name": "Alice Smith" }
` ``

### List Customer Orders
<!-- operation: getOrdersForCustomer -->

Response:
` ``json
[{ "orderId": "ORD-99", "status": "confirmed" }]
` ``

## Expected Result

` ``json
{
  "customer": { "name": "Alice Smith" },
  "orders": [{ "id": "ORD-99", "status": "confirmed" }]
}
` ``

## Flow

` ``mermaid
sequenceDiagram
    participant Q as Query Engine
    Q->>C: getCustomer
    C-->>Q: Customer
` ``
```

*(The backticks above are escaped for nesting. Real files use standard triple-backtick fencing.)*

### Sections

| Heading | Level | Required | Purpose |
|---------|-------|----------|---------|
| `# <Test Name>` | H1 | Yes | Test name. Exactly one per file. |
| `## Query` | H2 | Yes | TaxiQL query in a `taxiql` fenced code block. |
| `## Data Sources` | H2 | Yes | Parent for stubbed operations. |
| `### <label>` | H3 | Yes (1+) | Each H3 under Data Sources is one stubbed operation. |
| `## Expected Result` | H2 | Yes | Expected output in a `json` fenced code block. |
| `## Flow` | H2 | No | Optional Mermaid sequence diagram. |

Unrecognised headings and prose between sections are silently ignored — these files are living docs and users can annotate freely.

### Stub directives

Each `### <label>` section must have an HTML comment directive immediately after the heading:

```markdown
### Fetch Customer
<!-- operation: getCustomer -->
```

For streaming operations, add `mode: stream`:

```markdown
### Price Updates
<!-- operation: priceStream, mode: stream -->

Message:
` ``json
{ "price": 150.00 }
` ``

Message:
` ``json
{ "price": 151.25 }
` ``
```

- **`request-response`** (default): expects a single `Response:` block.
- **`stream`**: expects one or more `Message:` blocks, replayed in order.

The `operation` value is a **Taxi operation name**, matching `StubService.addResponse(operationName, json)` at runtime.

## Data model

```kotlin
data class TestSpec(
    val specVersion: String,       // "0.1"
    val name: String,              // from H1
    val description: String?,      // prose between H1 and first H2
    val query: String,             // TaxiQL query text
    val dataSources: List<Stub>,
    val expectedResult: String,    // raw JSON
    val flow: String?              // raw Mermaid text
)

data class Stub(
    val label: String,             // H3 heading text
    val operationName: String,     // Taxi operation name
    val mode: StubMode,
    val response: String?,         // JSON for request-response stubs
    val messages: List<String>?    // ordered JSON messages for stream stubs
)

enum class StubMode {
    REQUEST_RESPONSE,
    STREAM
}
```

## Reading a spec

From a string:

```kotlin
val spec = TestSpecReader.read(markdownText)
```

From a file:

```kotlin
val spec = TestSpecReader.readFile(Path.of("test-resources/specs/customer-order.md"))
```

An optional `filename` parameter is included in error messages to help users locate problems:

```kotlin
val spec = TestSpecReader.read(markdownText, filename = "customer-order.md")
```

Parse failures throw `SpecParseException` with context about what went wrong:

```
Preflight spec parse error in customer-order.md (section: Data Sources): Stub "Fetch Customer" is missing a source directive
```

## Writing a spec

Build a `TestSpec` and write it:

```kotlin
val spec = TestSpec(
    specVersion = "0.1",
    name = "Customer Order Flow",
    description = "Verifies the customer order lookup.",
    query = """find { Customer(customerId == "12345") } with { orders: Order[] }""",
    dataSources = listOf(
        Stub(
            label = "Fetch Customer Details",
            operationName = "getCustomer",
            mode = StubMode.REQUEST_RESPONSE,
            response = """{ "id": "12345", "name": "Alice Smith" }""",
            messages = null
        ),
        Stub(
            label = "List Customer Orders",
            operationName = "getOrdersForCustomer",
            mode = StubMode.REQUEST_RESPONSE,
            response = """[{ "orderId": "ORD-99", "status": "confirmed" }]""",
            messages = null
        )
    ),
    expectedResult = """{ "customer": { "name": "Alice Smith" }, "orders": [{ "id": "ORD-99" }] }""",
    flow = null
)

// To a string
val markdown = TestSpecWriter.write(spec)

// To a file
TestSpecWriter.writeFile(spec, Path.of("specs/customer-order.md"))
```

## Running specs as tests

In `preflight-runtime`, the `MarkdownSpec` base class discovers `.md` files and runs them as Kotest tests:

```kotlin
class MyTests : MarkdownSpec()
```

This scans `test-resources/specs/` for `.md` files. Each file becomes a test case — stubs are configured, the query is executed, and the result is compared structurally against the expected JSON.

A custom path can be provided:

```kotlin
class MyTests : MarkdownSpec(specsPath = "test-resources/my-specs")
```
