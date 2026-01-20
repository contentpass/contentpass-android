plugins {
    id("com.android.library")
    id("kotlin-android")
    kotlin("kapt")
}

android {
    compileSdk = 30

    defaultConfig {
        minSdk = 25
        targetSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles.add(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles.add(File("proguard-rules.pro"))
        }
        debug {
            manifestPlaceholders["appAuthRedirectScheme"] = "client.app.replaces"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.30")
    implementation("net.openid:appauth:0.10.0")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("com.squareup.moshi:moshi:1.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

kapt {
    correctErrorTypes = true
}

extra.apply{
    set("PUBLISH_GROUP_ID", "de.contentpass")
    set("PUBLISH_ARTIFACT_ID", "contentpass-android")
    set("PUBLISH_VERSION", "2.2.7")
}

apply("${rootProject.projectDir}/scripts/publish-module.gradle")
