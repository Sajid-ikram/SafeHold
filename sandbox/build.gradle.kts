import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The isolated renderer. Keep this module tiny: it depends only on core:model,
// because everything here can end up loaded into the :renderer process.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.ikram.sandbox.renderer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
