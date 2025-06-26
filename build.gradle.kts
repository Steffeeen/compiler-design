import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
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
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("com.github.ajalt.clikt:clikt:5.0.3")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}

val compilerOptions = listOf("-Xwhen-guards", "-Xcontext-parameters")

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs = compilerOptions
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    freeCompilerArgs = compilerOptions
}