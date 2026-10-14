plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// ktlint and detekt run from their CLIs instead of their Gradle plugins. The CLIs
// do not depend on AGP or Kotlin Gradle plugin internals, so an AGP or Gradle
// upgrade can never break the static checks.
val ktlint: Configuration = configurations.create("ktlint")
val detekt: Configuration = configurations.create("detekt")

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    detekt(libs.detekt.cli)
}

val kotlinSources = fileTree(rootDir) {
    include("**/src/**/*.kt", "**/*.gradle.kts")
    exclude("**/build/**", "**/.gradle/**")
}

// The embedded Kotlin compiler in both CLIs touches sun.misc.Unsafe, which newer
// JDKs warn about on every run.
val quietUnsafe = listOf("--sun-misc-unsafe-memory-access=allow")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin code style with ktlint."
    inputs.files(kotlinSources)
    inputs.file(".editorconfig")
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    jvmArgs(quietUnsafe)
    args("**/src/**/*.kt", "**/*.gradle.kts", "!**/build/**")
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fixes Kotlin code style issues with ktlint."
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    jvmArgs(quietUnsafe)
    args("-F", "**/src/**/*.kt", "**/*.gradle.kts", "!**/build/**")
}

tasks.register<JavaExec>("detekt") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs detekt over every module."
    inputs.files(kotlinSources)
    inputs.file("config/detekt/detekt.yml")
    classpath = detekt
    mainClass = "io.gitlab.arturbosch.detekt.cli.Main"
    jvmArgs(quietUnsafe)
    args(
        "--input",
        rootDir.absolutePath,
        "--config",
        file("config/detekt/detekt.yml").absolutePath,
        "--build-upon-default-config",
        "--excludes",
        "**/build/**,**/.gradle/**,**/*.gradle.kts",
    )
}
