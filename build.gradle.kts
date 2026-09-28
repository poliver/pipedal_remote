plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
