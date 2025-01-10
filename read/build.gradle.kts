plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lhj.read"
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("cn.wanghaomiao:JsoupXpath:2.5.3")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.louiscad.splitties:splitties-appctx:3.0.0")
    implementation("com.louiscad.splitties:splitties-systemservices:3.0.0")
    implementation("com.louiscad.splitties:splitties-views:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.mozilla:rhino:1.8.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.github.microutils:kotlin-logging:1.6.24")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
}