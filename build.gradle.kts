// Tube: YouTube's own mobile site on the watch, in a bundled GeckoView (Wear OS has no WebView), with crown volume.
plugins {
  id("com.android.application") version "9.1.1"
}

android {
  namespace = "com.example.tube"
  compileSdk = 37 // GeckoView 157 requires it
  defaultConfig {
    applicationId = "com.aistudio.tube"
    minSdk = 30
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    ndk { abiFilters += "armeabi-v7a" } // the Pixel Watch is 32-bit ARM only
  }
  packaging { jniLibs { useLegacyPackaging = true } } // compress GeckoView's native libs: a much smaller APK to push over WiFi
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    // GeckoView 157 brings kotlin-stdlib 2.4, newer than AGP's bundled Kotlin. This app only calls stdlib
    // basics (apply/also), unchanged between them. ponytail: drop this flag once AGP's Kotlin reaches 2.4.
    freeCompilerArgs.add("-Xskip-metadata-version-check")
  }
}

dependencies {
  implementation("org.mozilla.geckoview:geckoview-armeabi-v7a:157.0.20260924084938")
}
