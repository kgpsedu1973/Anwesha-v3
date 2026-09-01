import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.anwesha.school"
    minSdk = 24
    targetSdk = 36
    versionCode = 10
    versionName = "3.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk {
      abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
    }
  }

  bundle {
    abi {
      enableSplit = true
    }
    density {
      enableSplit = true
    }
    language {
      enableSplit = true
    }
  }

  signingConfigs {
    create("release") {
      val keystorePropertiesFile = rootProject.file("keystore.properties")
      val keystoreProperties = Properties()
      if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { stream ->
          keystoreProperties.load(stream)
        }
      }

      val envKeystorePath = System.getenv("ANWESHA_KEYSTORE_PATH")
        ?: keystoreProperties.getProperty("storeFile")
        ?: System.getenv("KEYSTORE_PATH")
        ?: "${rootDir}/anwesha-release.jks"

      var targetKeystore = file(envKeystorePath)
      if (!targetKeystore.exists() || targetKeystore.length() == 0L) {
        val base64File = rootProject.file("anwesha-release.keystore.base64")
        if (base64File.exists()) {
          try {
            val decodedBytes = Base64.getDecoder().decode(base64File.readText().replace("\\s".toRegex(), ""))
            targetKeystore.parentFile?.mkdirs()
            targetKeystore.writeBytes(decodedBytes)
          } catch (_: Exception) {}
        }
      }

      if (targetKeystore.exists() && targetKeystore.length() > 0L) {
        storeFile = targetKeystore
        storePassword = System.getenv("ANWESHA_KEYSTORE_PASSWORD")
          ?: keystoreProperties.getProperty("storePassword")
          ?: System.getenv("STORE_PASSWORD")
          ?: "anwesha123"
        keyAlias = System.getenv("ANWESHA_KEY_ALIAS")
          ?: keystoreProperties.getProperty("keyAlias")
          ?: "anwesha_school_key"
        keyPassword = System.getenv("ANWESHA_KEY_PASSWORD")
          ?: keystoreProperties.getProperty("keyPassword")
          ?: System.getenv("KEY_PASSWORD")
          ?: storePassword
      } else {
        val debugKeystore = file("${rootDir}/debug.keystore")
        if (!debugKeystore.exists() || debugKeystore.length() == 0L) {
          val debugBase64 = rootProject.file("debug.keystore.base64")
          if (debugBase64.exists()) {
            try {
              val decodedBytes = Base64.getDecoder().decode(debugBase64.readText().replace("\\s".toRegex(), ""))
              debugKeystore.parentFile?.mkdirs()
              debugKeystore.writeBytes(decodedBytes)
            } catch (_: Exception) {}
          }
        }
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      val debugKeystore = file("${rootDir}/debug.keystore")
      if (!debugKeystore.exists() || debugKeystore.length() == 0L) {
        val debugBase64 = rootProject.file("debug.keystore.base64")
        if (debugBase64.exists()) {
          try {
            val decodedBytes = Base64.getDecoder().decode(debugBase64.readText().replace("\\s".toRegex(), ""))
            debugKeystore.parentFile?.mkdirs()
            debugKeystore.writeBytes(decodedBytes)
          } catch (_: Exception) {}
        }
      }
      storeFile = debugKeystore
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE"
      excludes += "META-INF/LICENSE.txt"
      excludes += "META-INF/license.txt"
      excludes += "META-INF/NOTICE"
      excludes += "META-INF/NOTICE.txt"
      excludes += "META-INF/notice.txt"
      excludes += "META-INF/ASL2.0"
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Google Auth & Play Services for Drive OAuth integration:
  implementation(libs.play.services.auth)
  implementation(libs.google.api.services.drive)
  implementation(libs.google.api.client.android)
  implementation(libs.google.http.client.gson)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.mlkit.segmentation.selfie)
  implementation(libs.play.services.mlkit.document.scanner)
  // implementation(libs.mlkit.text.recognition)
  // implementation(libs.mlkit.text.recognition.devanagari)
  implementation(libs.opencv)
  // implementation(libs.tesseract4android)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
