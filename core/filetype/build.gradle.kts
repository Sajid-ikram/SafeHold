import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin so the sniffer and policy run in plain JVM unit tests, fast and
// without an emulator.
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
    api(project(":core:model"))

    testImplementation(libs.junit)
}
