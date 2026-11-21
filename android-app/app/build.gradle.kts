plugins {
    alias(libs.plugins.android.application)
}

android {
    // Keeps the original R package so the ported Crazyflie/ESP-Drone sources compile unchanged.
    namespace = "com.espressif.espdrone.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thedronecommunity.drone"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.2.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK can be side-loaded for testing.
            // Replace with your own keystore before publishing to Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    useLibrary("org.apache.http.legacy")

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/NOTICE")
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.material)
    implementation(libs.slf4j.android)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
}
