import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.ikram.sandbox"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.ikram.sandbox"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * Golden rules 1, 6 and 8, enforced on the MERGED manifest: libraries add permissions
 * through manifest merging, which never shows up in our own AndroidManifest.xml.
 */
abstract class VerifyManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)

        // androidx.core declares and uses this signature-level permission to keep
        // runtime-registered receivers private on API < 33. It grants nothing.
        val allowed = setOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_BIOMETRIC",
            "${applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }
        val extra = requested.filterNot { it in allowed }

        val application = document.getElementsByTagName("application").item(0) as Element
        val services = document.getElementsByTagName("service")
        val renderer = (0 until services.length).map { services.item(it) as Element }
            .firstOrNull { it.getAttributeNS(androidNs, "name").endsWith(".RendererService") }
        val problems = buildList {
            if (extra.isNotEmpty()) add("Permissions outside the allowed set: ${extra.joinToString()}")
            if (application.getAttributeNS(androidNs, "allowBackup") != "false") {
                add("android:allowBackup must be false")
            }
            if (application.getAttributeNS(androidNs, "dataExtractionRules").isEmpty()) {
                add("android:dataExtractionRules must be set")
            }
            // Golden rule 6: image and PDF parsing only ever happens in this process.
            when {
                renderer == null -> add("RendererService is missing")

                renderer.getAttributeNS(androidNs, "isolatedProcess") != "true" ->
                    add("RendererService must set android:isolatedProcess=\"true\"")

                renderer.getAttributeNS(androidNs, "exported") != "false" ->
                    add("RendererService must not be exported")

                renderer.getAttributeNS(androidNs, "process") != ":renderer" ->
                    add("RendererService must run in the :renderer process")
            }
        }
        check(problems.isEmpty()) {
            "Merged manifest breaks the golden rules (see CLAUDE.md):\n" + problems.joinToString("\n") { " - $it" }
        }
        report.get().asFile.writeText(requested.sorted().joinToString("\n"))
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyManifestTask>("verify${variantName}Manifest") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Checks the merged ${variant.name} manifest against the golden rules."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            applicationId.set(variant.applicationId)
            report.set(layout.buildDirectory.file("reports/manifest/${variant.name}-permissions.txt"))
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(verify) }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verify) }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:filetype"))
    implementation(project(":sandbox"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
