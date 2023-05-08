plugins {
    kotlin("jvm")
}

kotlin.jvmToolchain(11)

dependencies {
    api(project(":plugins:compose-compiler-plugin:compiler-hosted"))
}

description = "Compiler plugin that enables Compose"
group = "org.jetbrains.kotlin.experimental.compose"

val enableComposePublish = findProperty("kotlin.build.compose.publish.enabled") as String? == "true"
if (enableComposePublish) {
    publish {
        pom {
            name.set("Compose Compiler")
            developers {
                developer {
                    name.set("The Android Open Source Project")
                }
            }
        }
    }
}

standardPublicJars()