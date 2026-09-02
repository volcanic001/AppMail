plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.david.mailapp"
    compileSdk = 36

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    defaultConfig {
        applicationId = "com.david.mailapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OAuth2 redirect URI for Gmail / Outlook
        manifestPlaceholders["appAuthRedirectScheme"] = "com.david.mailapp"

        buildConfigField("boolean", "PERF_TRACE_ENABLED", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "PERF_TRACE_ENABLED", "false")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "PERF_TRACE_ENABLED", "true")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            buildConfigField("boolean", "PERF_TRACE_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.savedstate)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ktor (HTTP client for REST APIs)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)

    // DataStore (token storage)
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)

    // Chrome Custom Tabs (OAuth2 flow)
    implementation(libs.browser)

    // WebKit (dark mode algorithmic darkening for WebView)
    implementation(libs.androidx.webkit)

    // AndroidX Core (FileProvider for PDF viewing)
    implementation(libs.core.ktx)

    // Jsoup (HTML cleaning/sanitization)
    implementation(libs.jsoup)

    // Tracing
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.profileinstaller)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)

    // Instrumented testing
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.navigation.testing)
}
