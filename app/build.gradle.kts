
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

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
        versionCode = 35
        versionName = "1.0.36"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        val envProps = Properties()
        val envFile = rootProject.file(".env")

        if (envFile.exists()) {
            envFile.reader().use { reader ->
                envProps.load(reader)
            }
        }

        val openAiKey = (
            envProps.getProperty("OPENAI_API_KEY")
                ?.takeIf { it.isNotBlank() }
                ?: System.getenv("OPENAI_API_KEY")
                    ?.takeIf { it.isNotBlank() }
                ?: "MY_OPENAI_API_KEY"
            ).trim().removeSurrounding("\"")

        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            "\"$openAiKey\""
        )
    }

    // ==========================================
    // RELEASE KEY : SIGNATURE OBLIGATOIRE
    // ==========================================

    signingConfigs {
        create("release") {

            // GitHub Actions transmet KEYSTORE_PATH.
            // En local, le fichier attendu est app/release.jks.
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: "${rootDir}/app/release.jks"

            val keystoreFile = file(keystorePath)

            val storePass =
                System.getenv("STORE_PASSWORD") ?: ""

            val alias =
                System.getenv("KEY_ALIAS") ?: ""

            val keyPass =
                System.getenv("KEY_PASSWORD") ?: ""

            storeFile = keystoreFile
            storePassword = storePass
            keyAlias = alias
            keyPassword = keyPass
        }
    }

    // ==========================================
    // BUILD TYPES
    // ==========================================

    buildTypes {

        release {

            isCrunchPngs = false

            // Configuration similaire à Biblio BF
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            val releaseConfig =
                signingConfigs.getByName("release")

            // Vérification du fichier Release Key
            if (releaseConfig.storeFile?.isFile != true) {
                throw GradleException(
                    "ERREUR RELEASE : fichier release.jks introuvable. " +
                    "Vérifie KEYSTORE_PATH."
                )
            }

            // Vérification du mot de passe du keystore
            if (releaseConfig.storePassword.isNullOrBlank()) {
                throw GradleException(
                    "ERREUR RELEASE : KEYSTORE_PASSWORD manquant."
                )
            }

            // Vérification de l'alias
            if (releaseConfig.keyAlias.isNullOrBlank()) {
                throw GradleException(
                    "ERREUR RELEASE : KEY_ALIAS manquant."
                )
            }

            // Vérification du mot de passe de la clé privée
            if (releaseConfig.keyPassword.isNullOrBlank()) {
                throw GradleException(
                    "ERREUR RELEASE : KEY_PASSWORD manquant."
                )
            }

            // SIGNATURE RELEASE OBLIGATOIRE
            // Aucun recours à la clé Debug.
            signingConfig = releaseConfig
        }

        debug {
            signingConfig =
                signingConfigs.getByName("debug")
        }
    }

    // ==========================================
    // JAVA
    // ==========================================

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ==========================================
    // COMPOSE ET BUILD CONFIG
    // ==========================================

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ==========================================
    // LINT
    // ==========================================

    lint {
        checkReleaseBuilds = false
        disable += "InvalidFragmentVersionForActivityResult"
    }

    // ==========================================
    // TESTS
    // ==========================================

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // ==========================================
    // DEPENDENCIES INFO
    // ==========================================

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// ==============================================
// SECRETS GRADLE
// ==============================================

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"

    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
    ignoreList.add("OPENAI_API_KEY")
}

// ==============================================
// GOOGLE SERVICES
// ==============================================

googleServices {
    missingGoogleServicesStrategy =
        MissingGoogleServicesStrategy.WARN
}

// ==============================================
// DEPENDENCIES
// ==============================================

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
    ksp(libs.androidx.room.compiler)

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
