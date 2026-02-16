plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val appVersionName = "1.1.0"

android {
    namespace = "com.carpetqr.app"
    compileSdk = 36

    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "com.carpetqr.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = appVersionName

        resValue("string", "app_name", "CARPET QR")
        resValue("string", "title_app", "CARPET QR")
        resValue("string", "app_version", appVersionName)

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

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("outputs/renamed-apk"))
    rename { _ -> "CARPET_QR_v${appVersionName}_debug.apk" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coil)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
