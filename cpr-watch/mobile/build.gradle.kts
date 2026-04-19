import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

private fun loadLocalProperties(): Properties {
    val props = Properties()
    val rootDir = rootProject.projectDir
    val monorepoRoot = rootDir.parentFile?.resolve("local.properties")
    if (monorepoRoot != null && monorepoRoot.exists()) {
        FileInputStream(monorepoRoot).use { props.load(it) }
    }
    val localFile = rootDir.resolve("local.properties")
    if (localFile.exists()) FileInputStream(localFile).use { props.load(it) }
    return props
}

private fun escapeBuildConfigValue(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private val localPropertiesForKeys: Properties by lazy { loadLocalProperties() }

android {
    namespace = "com.hackathon.cprwatch.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hackathon.cprwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            val raw = localPropertiesForKeys.getProperty("ANTHROPIC_API_KEY", "").trim()
                .removeSurrounding("\"")
            buildConfigField(
                "String",
                "ANTHROPIC_API_KEY",
                "\"${escapeBuildConfigValue(raw)}\""
            )
            val modelRaw = localPropertiesForKeys.getProperty("ANTHROPIC_MODEL", "").trim()
                .removeSurrounding("\"")
            val modelForBuild = modelRaw.ifEmpty { "claude-sonnet-4-6" }
            buildConfigField(
                "String",
                "ANTHROPIC_MODEL",
                "\"${escapeBuildConfigValue(modelForBuild)}\""
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"\"")
            buildConfigField("String", "ANTHROPIC_MODEL", "\"\"")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
