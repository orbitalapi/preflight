# CLAUDE.md

## Project Overview

Preflight is a Kotlin-based testing framework for Taxi/Orbital projects. It provides a lightweight wrapper around Kotest's DescribeSpec for writing unit and integration tests against Taxi schemas and Orbital services. The project consists of a multi-module Gradle build with a core runtime library and a Gradle plugin.

## Architecture

### Core Components

- **preflight-spec**: Standalone markdown test spec parser/writer (`preflight-spec/`)
  - Has zero Orbital dependencies (only commonmark + jackson)
  - Lives in its own top-level Gradle build so Orbital can depend on it without circular dependencies
  - `TestSpecReader` / `TestSpecWriter`: Parse and generate markdown test specifications

- **preflight-runtime**: Core testing DSL and execution engine (`preflight-core/preflight-runtime/`)
  - `OrbitalSpec`: Base test class extending Kotest's DescribeSpec with Taxi/Orbital-specific functionality
  - `PreflightExtension`: Kotest extension that handles Taxi compilation and Orbital service initialization
  - `StubHelper`: Utilities for stubbing external data sources in tests
  - Environment variable support and configuration management
  - Depends on `preflight-spec` via Maven coordinates (resolved by composite build locally)

- **preflight-gradle-plugin**: Gradle plugin for project integration (`preflight-core/preflight-gradle-plugin/`)
  - `PreflightPlugin`: Main plugin class that configures Kotlin JVM, dependencies, and test execution
  - Automatically sets up JVM toolchain (Java 21), source sets, and test runner configuration

### Project Structure

The repository uses a two-build composite structure:
- `preflight-spec/` — standalone Gradle build (no Orbital dependencies)
- `preflight-core/` — main Gradle build containing runtime and plugin (depends on Orbital)
- Root `settings.gradle.kts` wires both builds together with example projects via `includeBuild`
- Example projects demonstrate usage patterns and serve as integration tests
- Documentation site built with Next.js in `docs/` directory

## Common Development Commands

### Building and Testing
```bash
# Build all modules
./gradlew build

# Run all tests (including example projects)
./gradlew test

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Publish all subprojects to local Maven
./gradlew publishAllMavenLocal
```

### Working with Example Projects
```bash
# Test simple project example
cd example-projects/simple-project && ./gradlew test

# Test project with Orbital dependencies
cd example-projects/project-with-orbital-dependency && ./gradlew test

# Test mixed sources project (Avro, OpenAPI, Taxi)
cd example-projects/mixed-sources && ./gradlew test
```

### Plugin Development
```bash
# Build and test the Gradle plugin specifically
cd preflight-core/preflight-gradle-plugin && ./gradlew test

# Generate version constants (automatically done during build)
./gradlew generateVersionConstants
```

## Key Conventions

### Test Structure
- Tests extend `OrbitalSpec` class which provides Taxi query execution methods
- Follow Kotest's DescribeSpec style: `describe("context") { it("should...") { ... } }`
- Test files go in `test/` directory (not `src/test/`)
- Use `.queryForScalar()`, `.queryForObject()`, `.queryForCollection()` for different return types

### Stubbing External Services
```kotlin
// Preferred: using stub scenarios
"query".queryForObject(stub("serviceName").returns("json"))

// Advanced: using stub customizer callback
"query".queryForObject { stubService -> /* configure */ }
```

### Environment Variables
- Support loading from `test-resources/env.conf` files
- Override via `env()` or `environmentVariables()` methods in specs
- Use HOCON format for configuration files

## Dependencies and Repositories

The project uses:
- Kotlin 1.9.23 with JVM target 21
- Kotest 5.8.0 for testing framework
- Custom Orbital/Taxi libraries from `https://repo.orbitalhq.com/release`
- Gradle Shadow plugin for fat JAR creation
- Maven publishing with S3-based Orbital repository

## Configuration

### Orbital Version Configuration
The plugin supports configurable Orbital versions via the `preflight` extension:

```kotlin
// Default usage (Orbital 0.36.0-M9)
plugins {
    id("com.orbitalhq.preflight")
}

// Custom Orbital version
preflight {
    orbitalVersion = "0.37.0"
}
```

**Implementation details:**
- Extension class: `PreflightExtension` in `PreflightPlugin.kt` 
- Default version: "0.36.0-M9"
- Dependencies are added in `afterEvaluate` block to access extension values
- Orbital dependencies are injected by plugin, Taxi comes transitively

## Version Management

Project version is set in two places (kept in sync manually):
- `preflight-core/build.gradle.kts` — `val PROJECT_VERSION = "0.1.0-SNAPSHOT"` (inherited by runtime and plugin)
- `preflight-spec/build.gradle.kts` — `version = "0.1.0-SNAPSHOT"`

The Gradle plugin uses code generation to embed version constants at build time via the `generateVersionConstants` task.