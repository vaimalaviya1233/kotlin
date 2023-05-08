plugins {
    kotlin("jvm")
    id("application")
    id("com.github.johnrengelman.shadow")
}

description = "Compiler Daemon that includes the Compose plugin"
group = "org.jetbrains.kotlin.experimental.compose"

kotlin.jvmToolchain(11)

application {
    mainClass.set("androidx.compose.compiler.daemon.MainKt")
}

dependencies {
    implementation(project(":plugins:compose-compiler-plugin:compiler-hosted"))
}

publish {
    pom {
        name.set("Compose Compiler Daemon")
        developers {
            developer {
                name.set("The Android Open Source Project")
            }
        }
    }
}

