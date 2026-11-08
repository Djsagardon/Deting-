plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dating.bondly.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val imgbbKey = (project.findProperty("imgbbApiKey") as? String)?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("IMGBB_API_KEY") as? String)?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("imgbb_api_key") as? String)?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("imgbbKey") as? String)?.takeIf { it.isNotBlank() }
            ?: System.getenv("IMGBB_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("imgbbApiKey")?.takeIf { it.isNotBlank() }
            ?: System.getenv("imgbb_api_key")?.takeIf { it.isNotBlank() }
            ?: System.getenv("IMGBB_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("IMGBB_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("imgbbApiKey")?.takeIf { it.isNotBlank() }
            ?: run {
                val envFiles = listOf(rootProject.file(".env"), rootProject.file(".env.local"), file(".env"))
                envFiles.firstNotNullOfOrNull { f ->
                    if (f.exists()) {
                        f.readLines().firstNotNullOfOrNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("IMGBB_API_KEY=") || trimmed.startsWith("imgbb_api_key=") || trimmed.startsWith("IMGBB_KEY=")) {
                                trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                            } else null
                        }
                    } else null
                }
            }
            ?: ""
        buildConfigField("String", "IMGBB_API_KEY", "\"$imgbbKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("debugConfig") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.messaging)

    // Google Sign-In & Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // Preferences DataStore
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Permissions
    implementation(libs.accompanist.permissions)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Native WebRTC SDK
    implementation(libs.webrtc.sdk)

    // HTTP Client for Secure Backend Uploader
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
