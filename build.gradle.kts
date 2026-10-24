// AGP 9.3.1 with its built-in Kotlin support, Kotlin 2.4.10, Gradle 9.6.1 (see the wrapper).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
}
