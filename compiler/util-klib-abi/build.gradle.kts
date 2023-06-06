plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    api(kotlinStdlib())
    api(project(":kotlin-util-klib"))
    api(project(":core:compiler.common"))
    testImplementation(kotlin("test"))
    testImplementation(intellijUtilRt())
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}
