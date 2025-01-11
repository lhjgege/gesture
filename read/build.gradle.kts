plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("kotlin-parcelize")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    // 设定Room的KSP参数
    ksp {
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
        arg("room.generateKotlin", "false")
        //arg("room.schemaLocation", "$projectDir/schemas")

    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging{
        exclude("META-INF/INDEX.LIST")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
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
    implementation("cn.hutool:hutool-crypto:5.8.22")
    implementation("org.apache.commons:commons-text:1.12.0")
}