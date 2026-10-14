import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android dependencies, so every module (the isolated renderer
// included) can share these types without pulling in anything else.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// This code runs inside the Android app, where lint's NewApi check does not reach a
// plain Kotlin module. Compiling against the Java 8 API (close to what Android 9, our
// minSdk, provides) turns a call to a newer JDK method into a build error.
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xjdk-release=1.8")
    }
}

dependencies {
    testImplementation(libs.junit)
}
