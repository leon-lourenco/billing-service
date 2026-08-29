plugins {
    // Resolves the Java 21 toolchain from a download service when no matching local JDK exists,
    // so a fresh machine can build this without installing a specific JDK by hand first.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "billing-service"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("card-billing-shared/versions/libs.versions.toml"))
        }
    }
}
