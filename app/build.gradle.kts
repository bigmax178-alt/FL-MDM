plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.crashlytics)

    alias(libs.plugins.google.gms)
    id("kotlin-kapt")
//    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.firebaselabelapp"
    compileSdk = 35

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "FoodLabelPro.apk"
        }
    }


    defaultConfig {
        applicationId = "com.example.firebaselabelapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 59
        versionName = "1.9.19"
        // custom updates from pre-release

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("legacy_release") {
            // IMPORTANT: Ensure you have copied your debug.keystore to app/keystore/debug.keystore
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("legacy_release")
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

dependencies {
    implementation ( libs.core)

    // Import the BoM for the Firebase platform
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.androidx.compose.bom))

    // SNMP4J library for host names
    implementation(libs.snmp4j)
    implementation (libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process) // Use the latest version

    // In app/build.gradle.kts
    implementation (libs.accompanist.systemuicontroller)
    implementation(libs.androidx.material.icons.extended)


    implementation (libs.kotlinx.coroutines.core)       // or latest
    implementation (libs.kotlinx.coroutines.android)    // or latest

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Security and Encryption
    implementation (libs.androidx.security.crypto)

    // Activity and Lifecycle
    implementation (libs.androidx.activity.compose)
    implementation (libs.androidx.lifecycle.runtime.ktx)

    // For better kiosk mode support
    implementation (libs.androidx.core.ktx)

    // Crashlytics and Analytics AFTER firebase and androidx.compose boms
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)


    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.auth)
    // Declare the dependency for the Cloud Firestore library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.firestore)
    implementation (libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Bluetooth Thermal Printing:
    implementation(libs.threetenabp)
    implementation(libs.escpos.thermalprinter.android)

// --- Compose UI (Versions from compose-bom) ---
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // --- Test Dependencies ---
    implementation(libs.androidx.runner)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}