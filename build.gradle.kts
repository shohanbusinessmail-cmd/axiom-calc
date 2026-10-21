// Axiom Calc - root build script.
// All plugin versions are declared once in gradle/libs.versions.toml and applied
// here with `apply false` so that the :app module controls the actual application.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
