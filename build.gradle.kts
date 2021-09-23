// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    extra.apply{
        set("composeVersion", "1.1.0-alpha04")
    }
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.30")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }

}

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.jetbrains.dokka") version "1.5.30"
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

apply("${rootDir}/scripts/publish-root.gradle")
