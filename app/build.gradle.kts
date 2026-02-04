import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "dev.patrick.astra"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.patrick.astra"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val compileSdkExpected = libs.versions.compileSdk.get().toInt()
val targetSdkExpected = libs.versions.targetSdk.get().toInt()
val minSdkExpected = libs.versions.minSdk.get().toInt()

tasks.register("checkSdkAlignment") {
    group = "verification"
    description = "Fails if Android SDK versions drift from the version catalog."
    doLast {
        val android = project.extensions.getByName("android")
        fun sdkValueToInt(value: Any?): Int? {
            return when (value) {
                null -> null
                is Int -> value
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> {
                    val method = value.javaClass.methods.firstOrNull {
                        it.name == "getApiLevel" && it.parameterCount == 0
                    }
                    val apiLevel = method?.invoke(value)
                    when (apiLevel) {
                        null -> null
                        is Int -> apiLevel
                        is Number -> apiLevel.toInt()
                        is String -> apiLevel.toIntOrNull()
                        else -> null
                    }
                }
            }
        }

        val compileSdkRaw =
            android.javaClass.methods.firstOrNull { it.name == "getCompileSdk" }?.invoke(android)
        val defaultConfig =
            android.javaClass.methods.firstOrNull { it.name == "getDefaultConfig" }?.invoke(android)
        val minSdkRaw =
            defaultConfig?.javaClass?.methods?.firstOrNull { it.name == "getMinSdk" }?.invoke(defaultConfig)
        val targetSdkRaw =
            defaultConfig?.javaClass?.methods?.firstOrNull { it.name == "getTargetSdk" }?.invoke(defaultConfig)

        val compileSdkActual = sdkValueToInt(compileSdkRaw)
        val minSdkActual = sdkValueToInt(minSdkRaw)
        val targetSdkActual = sdkValueToInt(targetSdkRaw)

        val errors = mutableListOf<String>()
        if (compileSdkActual != compileSdkExpected) {
            errors.add("compileSdk=$compileSdkActual (catalog=$compileSdkExpected)")
        }
        if (minSdkActual != minSdkExpected) {
            errors.add("minSdk=$minSdkActual (catalog=$minSdkExpected)")
        }
        if (targetSdkActual != targetSdkExpected) {
            errors.add("targetSdk=$targetSdkActual (catalog=$targetSdkExpected)")
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "Android SDK drift detected. " + errors.joinToString(", ")
            )
        }
    }
}

tasks.named("check") {
    dependsOn("checkSdkAlignment")
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
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.lifecycle.runtime.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
