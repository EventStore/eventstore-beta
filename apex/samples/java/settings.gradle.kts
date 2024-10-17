rootProject.name = "events"

val nexusUserName: String by settings
val nexusPassword: String by settings

pluginManagement.repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val junit = version("junit", "5.9.2")
            val spring = version("spring", "2.7.11")
            val jackson = version("jackson", "2.15.0")
            library("evenstoredb-core", "com.eventstore", "db-client-java").version("5.4.1")
            library("springboot-logging", "org.springframework.boot", "spring-boot-starter-logging").versionRef(spring)
            library("springboot-web", "org.springframework.boot", "spring-boot-starter-web").versionRef(spring)
            library("springboot-jdbc", "org.springframework.boot", "spring-boot-starter-jdbc").versionRef(spring)
            library("springboot-test", "org.springframework.boot", "spring-boot-starter-test").versionRef(spring)
            library("spring-vault-core", "org.springframework.vault", "spring-vault-core").version("2.3.4")
            library("assertj-core", "org.assertj", "assertj-core").version("3.24.2")
            library("mockito-core", "org.mockito", "mockito-core").version("4.11.0")
            library("jackson-module-parameter-names", "com.fasterxml.jackson.module", "jackson-module-parameter-names").versionRef(jackson)
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").versionRef(jackson)
            library("jackson-datatype-jdk8", "com.fasterxml.jackson.datatype", "jackson-datatype-jdk8").versionRef(jackson)
            library("jackson-dataformat-yaml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml").versionRef(jackson)
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit)
            library("junit-jupiter-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit)
            library("junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params").versionRef(junit)
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef(junit)
            library("junit-jupiter-system-stubs", "uk.org.webcompere", "system-stubs-jupiter").version("2.0.2")
            // Bundles.
            bundle("jackson", listOf("jackson-module-parameter-names", "jackson-datatype-jsr310", "jackson-datatype-jdk8", "jackson-dataformat-yaml"))
        }
    }
}

