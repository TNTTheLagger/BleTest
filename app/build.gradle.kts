plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hazel.bletest"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.hazel.bletest"
        minSdk = 26
        targetSdk = 33
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.material.v140)
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation(libs.androidx.appcompat.v131)
    implementation(libs.androidx.constraintlayout.v211)
    implementation(libs.androidx.lifecycle.extensions)
}