import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// OAuth web client ID used as serverClientId for Credential Manager (research R12).
// Read from local.properties (google.webClientId) or the GOOGLE_WEB_CLIENT_ID env var in CI.
val webClientId: String = localProperties.getProperty("google.webClientId")
    ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
    ?: ""

// The attached script is uploaded by the app from its assets (research R11).
val scriptAssetsDir = layout.buildDirectory.dir("generated/scriptAssets")
val copyScriptAssets by tasks.registering(Sync::class) {
    from(rootProject.file("apps-script")) {
        include("appsscript.json", "Code.gs", "Logic.gs", "Enable.html")
    }
    into(scriptAssetsDir.map { it.dir("apps-script") })
}

android {
    namespace = "com.workoutrec"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.workoutrec"
        minSdk = 26
        targetSdk = 35
        // CI passes -PversionCode=<run number> so each published APK installs over the previous one.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    signingConfigs {
        // CI signs with a shared debug key when DEBUG_KEYSTORE_FILE is set (from the
        // DEBUG_KEYSTORE_BASE64 secret). A stable key lets published APKs update each other and
        // keeps the SHA-1 registered for the Android OAuth client valid (research R12).
        getByName("debug") {
            System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
                storeFile = file(path)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(scriptAssetsDir)
    // Room schemas, for migration tests on the device (research R1).
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // FR-014: every user-visible text must come from resources and exist in English and Russian.
        error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    }
}

tasks.named("preBuild") { dependsOn(copyScriptAssets) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
