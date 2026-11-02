import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "ru.karaoketv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "ru.karaoketv"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${project.findProperty("youtubeApiKey") ?: ""}\"")
        buildConfigField("String", "VK_API_TOKEN", "\"${project.findProperty("vkApiToken") ?: ""}\"")
        buildConfigField("String", "VK_API_VERSION", "\"${project.findProperty("vkApiVersion") ?: "5.199"}\"")
        buildConfigField("String", "RUTUBE_SEARCH_ENDPOINT", "\"${project.findProperty("rutubeSearchEndpoint") ?: ""}\"")
    }

    buildFeatures { compose = true }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("KARAOKE_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("KARAOKE_STORE_PASSWORD")
            keyAlias = System.getenv("KARAOKE_KEY_ALIAS") ?: "karaoke-tv"
            keyPassword = System.getenv("KARAOKE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // Public APK must never contain the developer's source credentials.
            buildConfigField("String", "YOUTUBE_API_KEY", "\"\"")
            buildConfigField("String", "VK_API_TOKEN", "\"\"")
            buildConfigField("String", "RUTUBE_SEARCH_ENDPOINT", "\"\"")
        }
    }
    buildFeatures.buildConfig = true
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    testImplementation("junit:junit:4.13.2")
}
