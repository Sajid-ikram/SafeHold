import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin so the sniffer and policy run in plain JVM unit tests, fast and
// without an emulator.
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
    api(project(":core:model"))

    testImplementation(libs.junit)
}
