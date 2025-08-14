# 🚀 Preflight 0.0.4 Released: MongoDB Support, Direct Orbital API Access, and More!

We're excited to announce the release of **Preflight 0.0.4**, the biggest update yet to our Kotlin testing framework for Taxi and Orbital projects! This release brings significant new capabilities for integration testing, enhanced API access, and improved developer experience.

## 🎯 What's New

### 🗄️ MongoDB Container Support
The most requested feature is finally here! Preflight now provides first-class support for testing against MongoDB databases using TestContainers.

```kotlin
// Configure MongoDB connector
preflight {
    connectors = listOf(
        ConnectorSupport.Kafka,
        ConnectorSupport.MongoDB  // New!
    )
}

// Use in your tests
class MongoIntegrationSpec : OrbitalSpec({
    withContainers(
        mongoConnector("user-db")
    )
    
    it("should query users from MongoDB") {
        val mongo = containerForConnection<MongoContainerSupport>("user-db")
        
        // Insert test data
        mongo.mongoClient()
            .getDatabase("user_management")
            .getCollection("users")
            .insertOne(Document("id", "user123").append("name", "John"))
        
        // Test your Taxi queries
        """find { User(UserId == "user123") }""".queryForObject()
            .shouldBe(mapOf("id" to "user123", "name" to "John"))
    }
})
```

**Key Features:**
- Pre-configured MongoDB 6.0.7 containers
- Automatic database initialization with test credentials
- Direct MongoDB client access for test data setup
- Seamless integration with existing Kafka containers

### 🔧 Direct Orbital API Access
Need more control? Access the underlying Orbital instance directly for advanced testing scenarios:

```kotlin
it("should access Orbital API for advanced testing") {
    val orbital = orbital()
    
    // Inspect compiled schema
    val schema = orbital.schema
    val userType = schema.type("User")
    
    // Execute queries with custom parameters
    val result = orbital.query("""
        given { userId : UserId = parameter("userId") }  
        find { User(id == userId) }
    """, QueryContext.builder().withParameter("userId", "123").build())
    
    // Access internal services
    val invoker = orbital.operationInvoker
}
```

**Use Cases:**
- Schema metadata inspection and validation
- Custom query execution with parameters
- Advanced debugging and introspection
- Testing Orbital's internal behavior

### 📊 Mixed Source Format Support
Preflight now automatically handles projects with multiple schema formats:

```kotlin
// Your project structure
├── src
│   ├── taxi-schemas/
│   │   └── user.taxi
│   ├── avro-schemas/     // New!
│   │   └── events.avsc
│   └── openapi-specs/    // New!
│       └── api.yaml
```

**Automatic Transpilation:**
- **Avro schemas** (`.avsc`) → Taxi types
- **OpenAPI specs** (`.yaml`, `.json`) → Taxi services  
- **Taxi schemas** (`.taxi`) → Native support
- All formats unified in a single compiled schema for testing

### ⚙️ Enhanced Configuration
More control over your testing environment:

```kotlin
preflight {
    // Customize Orbital version
    orbitalVersion = "0.37.0"  // or "0.37.0-M1" for milestones
    
    // Use multiple connectors
    connectors = listOf(
        ConnectorSupport.Kafka,
        ConnectorSupport.MongoDB
    )
}
```

### 🎯 Named Query Arguments
Pass arguments to your named queries in tests:

```kotlin
it("should execute named queries with arguments") {
    runNamedQueryForObject("getUserWithDetails", mapOf(
        "userId" to "user123",
        "includePreferences" to true
    )) {
        // Configure stubs as needed
    }
}
```

## 🔄 Breaking Changes

### Java 21 Requirement
**Action Required:** Preflight 0.0.4 requires Java 21 (previously Java 17).

```bash
# Update your JAVA_HOME
export JAVA_HOME=/path/to/java-21

# Verify version
java -version
# Should show: java version "21.x.x"
```

Update your Gradle builds to use Java 21:
```kotlin
kotlin {
    jvmToolchain(21)
}
```

## 📚 Improved Documentation

We've significantly expanded our documentation with:
- Complete MongoDB integration testing guide
- Direct Orbital API access examples
- Mixed source format documentation
- Enhanced getting started guide
- Comprehensive changelog (new!)

## 🚀 Getting Started

### New Projects
```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.23"
    id("com.orbitalhq.preflight") version "0.0.4"
}

preflight {
    orbitalVersion = "0.36.0-M9"  // or your preferred version
    connectors = listOf(
        ConnectorSupport.Kafka,
        ConnectorSupport.MongoDB
    )
}
```

### Existing Projects
Update your plugin version:
```kotlin
id("com.orbitalhq.preflight") version "0.0.4"  // was "0.0.3"
```

## 🎉 What's Next?

Looking ahead to future releases, we're exploring:
- PostgreSQL container support
- REST API mocking with WireMock
- Enhanced streaming test utilities  
- Performance improvements and optimizations
- Integration with Nebula for real data source testing

## 📖 Resources

- **Documentation:** [Latest docs with all new features](https://preflight.orbitalhq.com)
- **Changelog:** [Complete version history](https://preflight.orbitalhq.com/changelog)  
- **Examples:** Check out our [example projects](https://github.com/orbitalapi/preflight/tree/main/example-projects)
- **Issues:** [Report bugs or request features](https://github.com/orbitalapi/preflight/issues)

## 🙏 Thank You

A huge thanks to our community for the feedback, bug reports, and feature requests that made this release possible. Special shoutout to everyone who contributed to testing the MongoDB integration!

---

**Happy Testing!** 🧪

*The Orbital Team*

---

### Version Compatibility
- **Preflight:** 0.0.4
- **Orbital:** 0.36.0-M9 (default, configurable)
- **Java:** 21+ (required)
- **Kotlin:** 1.9.23
- **Gradle:** 7.0+ (recommended: 8.0+)