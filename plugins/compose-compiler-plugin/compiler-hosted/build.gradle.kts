plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    implementation(project(":kotlin-stdlib"))
    implementation(project(":js:js.frontend"))
    implementation(project(":compiler:frontend"))
    implementation(project(":compiler:backend.jvm"))
    implementation(project(":compiler:cli-base"))
    implementation(project(":compiler:ir.serialization.js"))
    implementation(project(":compiler:backend.jvm.codegen"))

    compileOnly(intellijCore())

    testImplementation(commonDependency("junit:junit"))
    val platformVersion = commonDependencyVersion("org.junit", "junit-bom")
    testImplementation("org.junit.vintage:junit-vintage-engine:$platformVersion")
}

kotlin.jvmToolchain(11)

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xskip-metadata-version-check",
            "-Xjvm-default=all"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}