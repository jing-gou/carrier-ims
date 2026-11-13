import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

// Release signing is read from keystore.properties, then from the environment.
// Anything missing falls back to the debug key, so a fresh clone still builds an
// installable APK — check the signer before publishing one.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, variable: String): String? =
    signing.getProperty(key) ?: System.getenv(variable)

val storePath = signingValue("storeFile", "NRST_KEYSTORE")
val storeSecret = signingValue("storePassword", "NRST_KEYSTORE_PASSWORD")
val keyName = signingValue("keyAlias", "NRST_KEY_ALIAS")
val keySecret = signingValue("keyPassword", "NRST_KEY_PASSWORD")
val hasReleaseKey = storePath != null && storeSecret != null && keyName != null &&
        keySecret != null && rootProject.file(storePath).exists()

if (!hasReleaseKey) {
    logger.warn("nr-signal-thresholds: no release keystore configured, release builds use the debug key")
}

android {
    namespace = "io.github.jingou.nrst"
    compileSdk = 37
    compileSdkMinor = 2
    buildToolsVersion = "37.0.0"

    enableKotlin = false

    defaultConfig {
        applicationId = "io.github.jingou.nrst"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(storePath!!)
                storePassword = storeSecret
                keyAlias = keyName
                keyPassword = keySecret
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs[if (hasReleaseKey) "release" else "debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    // Modern libxposed service client: this is how the GUI reaches the framework's writable
    // remote preferences and how it asks which processes the module is currently hooked into.
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}
