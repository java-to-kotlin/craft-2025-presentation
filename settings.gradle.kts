import org.apache.tools.ant.DirectoryScanner

rootProject.name = "craft-2025-presentation"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

val http4kVersion = "6.6.0.1"
val forkhandlesVersion = "2.22.2.1"
val jacksonVersion = "2.19.0"
val junitVersion = "5.13.0-M2"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("forkhandles", "dev.forkhandles:forkhandles-bom:$forkhandlesVersion")
            library("result4k","dev.forkhandles:result4k:$forkhandlesVersion")
            library("values4k","dev.forkhandles:values4k:$forkhandlesVersion")
            library("tuples4k","dev.forkhandles:tuples4k:$forkhandlesVersion")

            library("http4k-bom", "org.http4k:http4k-bom:$http4kVersion")
            library("http4k-core", "org.http4k:http4k-core:$http4kVersion")
            library("http4k-server-undertow", "org.http4k:http4k-server-undertow:$http4kVersion")
            library("http4k-client-apache", "org.http4k:http4k-client-apache:$http4kVersion")
            library("http4k-config", "org.http4k:http4k-config:$http4kVersion")
            library("http4k-template-handlebars", "org.http4k:http4k-template-handlebars:$http4kVersion")

            library("http4k-testing-approval", "org.http4k:http4k-testing-approval:$http4kVersion")
            library("http4k-testing-hamkrest", "org.http4k:http4k-testing-hamkrest:$http4kVersion")
            library("http4k-testing-strikt", "org.http4k:http4k-testing-strikt:$http4kVersion")

            bundle("http4k", listOf(
                "http4k-bom",
                "http4k-core",
                "http4k-server-undertow",
                "http4k-client-apache",
                "http4k-config",
                "http4k-template-handlebars"
            ))

            bundle("http4k-testing", listOf(
                "http4k-testing-approval",
                "http4k-testing-hamkrest",
                "http4k-testing-strikt"
            ))

            library("jackson-databind", "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
            library("jackson-module-kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
            library("jackson-module-parameter-names", "com.fasterxml.jackson.module:jackson-module-parameter-names:$jacksonVersion")
            library("jackson-datatype-jdk8", "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
            bundle("jackson", listOf(
                "jackson-databind",
                "jackson-module-kotlin",
                "jackson-module-parameter-names",
                "jackson-datatype-jdk8",
                "jackson-datatype-jsr310"
            ))

            library("faker", "io.github.serpro69:kotlin-faker:1.16.0")

            library("junit-bom", "org.junit:junit-bom:$junitVersion")
            library("junit-jupiter", "org.junit.jupiter:junit-jupiter:$junitVersion")

            bundle("junit", listOf(
                "junit-jupiter"
            ))
        }
    }
}

/* Unfortunately, Gradle requires this to be configured globally, not
 * in the task that needs it.
 */
DirectoryScanner.removeDefaultExclude("**/.gitignore")

include("examples:signup")

