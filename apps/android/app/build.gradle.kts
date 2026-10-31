plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kover)
}

fun endpoint(property: String): String =
    (project.findProperty(property) as String?)?.trim()?.removeSuffix("/")
        ?: error("Falta la propiedad $property en gradle.properties")

android {
    namespace = "com.tint.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tint.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["inviteHost"] = endpoint("tintInviteHost")
        buildConfigField("String", "IDENTITY_API_URL", "\"${endpoint("tintIdentityApiUrl")}\"")
        buildConfigField("String", "SYNC_WS_URL", "\"${endpoint("tintSyncWsUrl")}\"")
        buildConfigField("String", "SOCIAL_WS_URL", "\"${endpoint("tintSocialWsUrl")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kover {
    reports {
        filters {
            excludes {
                // Capa fina de Android/Compose: se prueba a mano en el dispositivo.
                annotatedBy("androidx.compose.runtime.Composable")
                classes(
                    "com.tint.app.MainActivity*",
                    "com.tint.app.AppContainer",
                    "com.tint.app.store.Prefs*",
                    "com.tint.app.ui.*",
                    "com.tint.app.auth.CredentialManagerGoogleSource*",
                    "com.tint.app.auth.FirebaseAuthGateway*",
                    "com.tint.app.sync.OkHttpSyncSocketFactory*",
                    "com.tint.app.sync.ExoPlayerPort*",
                    "com.tint.app.BuildConfig",
                    "*ComposableSingletons*",
                )
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
