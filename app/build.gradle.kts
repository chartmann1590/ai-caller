plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.localcallagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localcallagent"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "mode"
    productFlavors {
        create("sipAgent") {
            dimension = "mode"
            applicationIdSuffix = ".sip"
            versionNameSuffix = "-sip"
        }
        create("pstnControl") {
            dimension = "mode"
            applicationIdSuffix = ".pstn"
            versionNameSuffix = "-pstn"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-privacy"))
    implementation(project(":telephony-api"))
    implementation(project(":telephony-pstn"))
    implementation(project(":telephony-sip"))
    implementation(project(":audio-core"))
    implementation(project(":asr-local"))
    implementation(project(":tts-local"))
    implementation(project(":llm-litert"))
    implementation(project(":agent-orchestrator"))
    implementation(project(":benchmark"))
    implementation(project(":test-fixtures"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.graphics.path)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}

// Custom privacy verification task
tasks.register("verifyPrivacyManifest") {
    group = "verification"
    description = "Enforces strict privacy and security invariants on Android manifests."
    doLast {
        val manifests = listOf(
            file("src/main/AndroidManifest.xml"),
            file("src/sipAgent/AndroidManifest.xml"),
            file("src/pstnControl/AndroidManifest.xml")
        ).filter { it.exists() }

        for (manifestFile in manifests) {
            val content = manifestFile.readText()
            if (content.contains("android.permission.CAPTURE_AUDIO_OUTPUT")) {
                throw GradleException("PRIVACY VIOLATION: CAPTURE_AUDIO_OUTPUT detected in ${manifestFile.name}! Third-party apps cannot use privileged audio capture.")
            }
        }

        val pstnManifest = file("src/pstnControl/AndroidManifest.xml")
        if (pstnManifest.exists() && pstnManifest.readText().contains("android.permission.INTERNET")) {
            throw GradleException("PRIVACY VIOLATION: pstnControl flavor MUST NOT declare INTERNET permission!")
        }

        println("PRIVACY AUDIT PASSED: All privacy invariants satisfied. Zero privileged audio capture, zero PSTN internet access.")
    }
}
