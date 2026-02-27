rootProject.name = "preflight"

// Include preflight-spec first so it's available when preflight-core resolves
includeBuild("preflight-spec")
includeBuild("preflight-core")

// Include example projects as composites too
includeBuild("example-projects/simple-project")
includeBuild("example-projects/project-with-orbital-dependency")
includeBuild("example-projects/spec-project")