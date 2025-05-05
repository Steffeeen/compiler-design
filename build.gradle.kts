plugins {
    id("java")
    application
    kotlin("jvm") version "2.1.20"
}

group = "edu.kit.kastel.logic"
version = "1.0-SNAPSHOT"

application {
    mainModule = "edu.kit.kastel.vads.compiler"
    mainClass = "edu.kit.kastel.vads.compiler.Main"
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