# Building and Releasing

## Project Structure

The repository uses two separate Gradle builds connected via composite builds:

- **`preflight-spec/`** — Standalone build for the markdown test spec parser/writer. Has no Orbital dependencies (only commonmark + jackson), so Orbital itself can depend on it without circular dependencies.
- **`preflight-core/`** — Main build containing `preflight-runtime` and `preflight-gradle-plugin`. Depends on Orbital libraries. Consumes `preflight-spec` via composite build (`includeBuild`).

The root `settings.gradle.kts` wires both builds together along with the example projects.

## Building locally

```bash
# Full build from repo root (builds everything including examples)
./gradlew build

# Build preflight-spec standalone
cd preflight-spec && ./gradlew build

# Build preflight-core (resolves preflight-spec via composite build)
cd preflight-core && ./gradlew build
```

## Installing to local Maven repo

To test changes locally before releasing, publish all modules to `~/.m2`:

```bash
cd preflight-core
./gradlew publishAllMavenLocal
```

### Using a local pre-release version in another project

Consuming projects need `mavenLocal()` in their `pluginManagement` repositories so Gradle can find the locally-published plugin:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

Then reference the local version in `build.gradle.kts`:

```kotlin
plugins {
    id("com.orbitalhq.preflight") version "0.1.0" // matches the version you published locally
}
```

The plugin itself injects the Orbital Maven repositories automatically, so no other repository configuration is needed in the consuming project.

## Bumping the version

The version is set in **two places** (kept in sync manually):

1. `preflight-core/build.gradle.kts` — `val PROJECT_VERSION = "0.1.0-SNAPSHOT"`
2. `preflight-spec/build.gradle.kts` — `version = "0.1.0-SNAPSHOT"`

All preflight-core submodules inherit their version from `preflight-core/build.gradle.kts`. The Gradle plugin also embeds it at build time via a generated `Versions.kt` constant.

## Releasing

Releases are triggered by pushing a git tag. GitHub Actions handles building, signing, and publishing.

```bash
# 1. Make sure you're on main with a clean tree
git checkout main
git pull

# 2. Bump the version in both build.gradle.kts files, commit

# 3. Tag the release
git tag v0.1.0

# 4. Push the commit and tag
git push origin main
git push origin v0.1.0
```

The `release.yml` workflow then:
1. Builds the project with JDK 21
2. Signs artifacts with GPG
3. Publishes `preflight-spec` to the **Orbital Maven repository**
4. Publishes the Gradle plugin to the **Gradle Plugin Portal**
5. Publishes `preflight-runtime` to the **Orbital Maven repository**
6. Creates a GitHub Release with JARs and auto-generated notes

## Where artifacts are published

| Artifact                                | Destination                                               | How consumers resolve it                                    |
|-----------------------------------------|-----------------------------------------------------------|-------------------------------------------------------------|
| `com.orbitalhq.preflight` Gradle plugin | [Gradle Plugin Portal](https://plugins.gradle.org/)       | `plugins { id("com.orbitalhq.preflight") }`                 |
| `preflight-runtime`                     | Orbital Maven repo (`https://repo.orbitalhq.com/release`) | `maven { url = uri("https://repo.orbitalhq.com/release") }` |
| `preflight-spec`                        | Orbital Maven repo (same)                                 | Same repository config                                      |

Nothing is published to Maven Central. Consumers need the Orbital Maven repository in their `repositories {}` block to resolve the runtime (the Gradle plugin injects this automatically).

## Required CI secrets

These must be configured in the GitHub repository settings:

- `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET` — Gradle Plugin Portal credentials
- `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` — artifact signing
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` — S3 access for Orbital Maven repo
