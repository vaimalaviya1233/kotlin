plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "org.jetbrains.kotlin"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(intellijCore())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation(projectTests(":compiler:fir:modularized-tests"))
}

application {
    mainClass.set("MainKt")
}