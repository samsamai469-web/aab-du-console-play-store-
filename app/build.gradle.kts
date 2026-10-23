import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.anvil.samai"
    minSdk = 24
    targetSdk = 36
    versionCode = 5
    versionName = "1.0.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Clés lues depuis les variables d'environnement (secrets GitHub).
    // Si une clé est absente ou vide, on met une valeur d'exemple non vide pour que BuildConfig reste valide.
    fun secretValue(name: String): String {
      val v = (System.getenv(name) ?: "").trim().replace("\\", "").replace("\"", "")
      return if (v.isEmpty()) "MY_$name" else v
    }
    val samOpenAiKey = (System.getenv("OPENAI_API_KEY") ?: "").trim().replace("\"", "")
    buildConfigField("String", "SAM_OPENAI_KEY", "\"${samOpenAiKey}\"")
    listOf(
      "GEMINI_API_KEY", "GROQ_API_KEY", "OPENROUTER_API_KEY",
      "TAVILY_API_KEY", "YOUTUBE_API_KEY", "RESEND_API_KEY", "GOOGLE_CLIENT_SECRET"
    ).forEach { key ->
      buildConfigField("String", key, "\"${secretValue(key)}\"")
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keyFile = file(keystorePath)
      if (keyFile.exists()) {
        storeFile = keyFile
        storePassword = System.getenv("STORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      
      // Sécurisation de la signature Release : ne pas crasher si le fichier keystore n'existe pas en local
      val releaseKeystore = signingConfigs.getByName("release").storeFile
      if (releaseKeystore != null && releaseKeystore.exists()) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  lint {
    checkReleaseBuilds = false
    disable += "InvalidFragmentVersionForActivityResult"
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configuration robuste du plugin Secrets Gradle :
// S'il n'y a pas de fichier .env présent, il bascule automatiquement sur .env.example
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  // Ces clés sont définies à la main dans defaultConfig (plus fiable que le fichier .env)
  ignoreList.add("OPENAI_API_KEY")
  ignoreList.add("GEMINI_API_KEY")
  ignoreList.add("GROQ_API_KEY")
  ignoreList.add("OPENROUTER_API_KEY")
  ignoreList.add("TAVILY_API_KEY")
  ignoreList.add("YOUTUBE_API_KEY")
  ignoreList.add("RESEND_API_KEY")
  ignoreList.add("GOOGLE_CLIENT_SECRET")
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
  // Platform BOMs
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))

  // Firebase
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation("com.google.firebase:firebase-storage")
  implementation("com.google.firebase:firebase-functions")

  // Core & Security
  implementation(libs.androidx.core.ktx)
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation("androidx.work:work-runtime-ktx:2.9.0")

  

  // Compose UI & Material 
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)

  // Architecture & Lifecycle
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Room Persistence
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)

  // Serialization & Coroutines
  implementation(libs.kotlinx.serialization.json)

  // Networking & Media
  implementation(libs.retrofit)
  implementation(libs.converter.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)
  implementation(libs.coil.compose)
  implementation(libs.youtube.player)
  implementation(libs.generativeai)

  // Credentials & Identity
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  // Unit Testing
  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  // Instrumentation Testing
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  // Debug tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
