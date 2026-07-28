plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.4.0"
}

group = "net.ccbluex"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.gson:gson:2.8.9")
    api("org.apache.logging.log4j:log4j-core:2.24.3")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
