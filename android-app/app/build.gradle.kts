import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing key. keystore.properties is not in git: it holds storeFile, storePassword,
// keyAlias and keyPassword. Without it, release builds fall back to the debug key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    // Keeps the original R package so the ported Crazyflie/ESP-Drone sources compile unchanged.
    namespace = "com.espressif.espdrone.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thedronecommunity.drone"
        minSdk = 26
        targetSdk = 36
        versionCode = 27
        versionName = "1.2.5"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
