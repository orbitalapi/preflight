rootProject.name = "preflight"

// Include the plugin build as a composite
includeBuild("preflight-core")

// Include example projects as composites too
includeBuild("example-projects/simple-project")
includeBuild("example-projects/project-with-orbital-dependency")
includeBuild("example-projects/spec-project")