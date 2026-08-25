plugins {
    // Resolves the Java 21 toolchain from a download service when no matching local JDK exists,
    // so a fresh machine can build this without installing a specific JDK by hand first.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The version catalog is local to this repo for now. Once `card-billing-shared` exists it moves
// there and gets consumed as a submodule, the same way all four services will consume it:
//
//   dependencyResolutionManagement {
//       versionCatalogs { create("libs") { from(files("card-billing-shared/versions/libs.versions.toml")) } }
//   }
//
// Until then `gradle/libs.versions.toml` is picked up automatically by convention.

rootProject.name = "billing-service"
