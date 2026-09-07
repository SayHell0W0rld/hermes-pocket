plugins {
    id("com.android.application")
}

import java.util.Properties

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
fun signingCredential(envName: String, propertyName: String): String? =
    System.getenv(envName) ?: keystoreProps.getProperty(propertyName)
val releaseStorePath: String? = signingCredential("KEYSTORE_FILE", "storeFile")

android {
    namespace = "com.hermes.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hermes.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseStorePath != null) {
                signingConfigs.create("release") {
                    storeFile = file(releaseStorePath)
                    storePassword = signingCredential("KEYSTORE_PASSWORD", "storePassword")
                    keyAlias = signingCredential("KEY_ALIAS", "keyAlias")
                    keyPassword = signingCredential("KEY_PASSWORD", "keyPassword")
                }
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.json:json:20250517")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("com.google.truth:truth:1.4.4")
}
