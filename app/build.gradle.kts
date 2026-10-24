import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing is optional and never committed: put keystore/shift-alarm-release.jks and
// keystore/keystore.properties (storePassword, keyAlias, keyPassword) in place to sign, otherwise
// the release APK is unsigned. Sign every update with the same key: Android refuses an update
// signed with another, and uninstalling to get round it loses the Ubeya sign-in.
val keystoreFile = rootDir.resolve("keystore/shift-alarm-release.jks")
val keystoreProps =
    Properties().apply {
        rootDir
            .resolve("keystore/keystore.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

android {
    namespace = "com.cyber1sh.shiftalarm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cyber1sh.shiftalarm"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }

    val canSign = keystoreFile.exists() && keystoreProps.getProperty("storePassword") != null

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // android.jar only has stubs of org.json; the JVM tests need the real thing.
    testImplementation("org.json:json:20240303")
}
