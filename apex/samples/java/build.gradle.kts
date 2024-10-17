plugins {
    `idea`
    `java`
    id("io.freefair.lombok") version "6.6.3"
}

val nexusUserName: String by project
val nexusPassword: String by project
val repositoryUsername: String by project
val repositoryPassword: String by project

val junitVersion = "5.8.2"

allprojects {
    group = "com.sandbox.events"
    repositories {
        mavenLocal() {
            content {
                includeGroup("com.eventstore")
                includeGroup("com.sandbox")
            }
        }
        mavenCentral()
    }
}

dependencies {
    implementation(libs.evenstoredb.core)
    implementation(libs.springboot.web)
    implementation(libs.bundles.jackson)

    testImplementation(libs.springboot.test)
    testImplementation(libs.springboot.logging)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}