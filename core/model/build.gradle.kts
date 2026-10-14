import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android dependencies, so every module (the isolated renderer
// included) can share these types without pulling in anything else.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
