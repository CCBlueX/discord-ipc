plugins {
    `maven-publish`
    kotlin("jvm") version "2.4.0"
}

group = "net.ccbluex"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}