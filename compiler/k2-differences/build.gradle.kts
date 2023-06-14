plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

    testImplementation(projectTests(":compiler:tests-common-new"))
    testImplementation(projectTests(":generators:test-generator"))

    testImplementation(project(":compiler:fir:checkers"))
    testImplementation(project(":compiler:fir:checkers:checkers.jvm"))
    testImplementation(project(":compiler:fir:checkers:checkers.js"))
    testImplementation(project(":compiler:fir:checkers:checkers.native"))
    testImplementation(project(":plugins:android-extensions-compiler"))
    testImplementation(project(":plugins:fir-plugin-prototype"))
    testImplementation(project(":plugins:parcelize:parcelize-compiler:parcelize.k1"))
    testImplementation(project(":plugins:parcelize:parcelize-compiler:parcelize.k2"))
    testImplementation(project(":kotlinx-serialization-compiler-plugin.k1"))
    testImplementation(project(":kotlinx-serialization-compiler-plugin.k2"))
    testImplementation(project(":kotlin-noarg-compiler-plugin.k1"))
    testImplementation(project(":kotlin-noarg-compiler-plugin.k2"))
    testImplementation(project(":kotlin-assignment-compiler-plugin.k1"))
    testImplementation(project(":kotlin-assignment-compiler-plugin.k2"))

    testImplementation(project(":compiler:fir:raw-fir:raw-fir.common"))
    testImplementation(project(":compiler:frontend"))
    testImplementation(project(":compiler:frontend.java"))
    testImplementation(project(":js:js.frontend"))
    testImplementation(project(":native:frontend.native"))
    testImplementation(project(":wasm:wasm.frontend"))

    // Errors.java have some ImmutableSet fields which we
    // don't need here, but otherwise getDeclaredFields fails
    testRuntimeOnly(commonDependency("com.google.guava:guava"))
}

optInToExperimentalCompilerApi()

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}
