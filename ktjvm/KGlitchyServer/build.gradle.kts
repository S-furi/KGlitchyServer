plugins {
    kotlin("jvm") version "2.1.10"
    application
}

application {
    mainClass.set("io.github.sfuri.MainKt")
}

group = "io.github.sfuri"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}