import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    application
    kotlin("jvm") version "2.1.20"
}

group = "edu.kit.kastel.logic"
version = "1.0-SNAPSHOT"

application {
    mainClass = "edu.kit.kastel.vads.compiler.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(23)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xwhen-guards", "-Xcontext-parameters"))
}