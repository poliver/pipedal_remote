plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

val kotlinMetadataVersion = libs.versions.kotlin.get()

subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion")
    }
}
