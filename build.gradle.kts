// AGP 9 compila Kotlin por sí mismo (Kotlin integrado) y ya no admite el plugin
// kotlin-android. Sin nada más usaría el KGP que trae de serie; ponerlo aquí en el
// classpath hace que use el de libs.versions.toml.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
