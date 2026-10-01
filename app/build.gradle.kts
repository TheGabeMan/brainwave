import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional release signing. Create `keystore.properties` in the repo root (it is
// git-ignored) with storeFile / storePassword / keyAlias / keyPassword to sign the
// APKs you hand to family members. Without it, only debug builds are signed.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "dev.gabrie.brainwave"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.gabrie.brainwave"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp { arg("room.schemaLocation", "$projectDir/schemas") }

        ndk {
            // libvosk.so is ~10 MB per ABI, so shipping all four would add 30 MB
            // to an APK that is hand-delivered. Every Android 8 phone is arm64;
            // add "armeabi-v7a" back here if someone turns up with a 32-bit one.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // android-mail and android-activation ship the same licence metadata,
            // in both .txt and .md flavours; a glob covers every variant.
            excludes += setOf(
                "/META-INF/{LICENSE,LICENSE.txt,LICENSE.md,NOTICE,NOTICE.txt,NOTICE.md}",
                "/META-INF/{DEPENDENCIES,INDEX.LIST,AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
            )
            // These *must* survive: JavaMail resolves providers through them.
            pickFirsts += setOf(
                "META-INF/javamail.default.providers",
                "META-INF/javamail.default.address.map",
                "META-INF/javamail.providers",
                "META-INF/javamail.address.map",
                "META-INF/mailcap",
            )
        }
    }

    dependenciesInfo {
        // F-Droid / reproducible builds: keep the signed dependency blob out of the APK.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.javamail.android.mail)
    implementation(libs.javamail.android.activation)

    implementation(libs.vosk.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
