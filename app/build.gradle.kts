plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release version: taken from the release tag (SECONDES_VERSION env var or Gradle property,
// "v1.2.0" or "1.2.0"). versionCode = major * 10000 + minor * 100 + patch, so it strictly
// increases with the version as long as minor and patch stay below 100.
// Local default when nothing is set: 0.1.0 (versionCode 100).
fun releaseSetting(name: String): String? =
    (System.getenv(name) ?: providers.gradleProperty(name).orNull)?.takeIf { it.isNotBlank() }

val versionRegex = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
val rawVersion = releaseSetting("SECONDES_VERSION") ?: "0.1.0"
val versionMatch = versionRegex.matchEntire(rawVersion.trim())
    ?: throw GradleException("SECONDES_VERSION must look like v1.2.0 or 1.2.0, got '$rawVersion'")
val (verMajor, verMinor, verPatch) = versionMatch.destructured.toList().map { it.toInt() }
require(verMinor < 100 && verPatch < 100) { "minor and patch must be below 100 (got $rawVersion)" }

android {
    namespace = "io.github.benomite.secondes"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.benomite.secondes"
        minSdk = 26
        targetSdk = 35
        versionCode = verMajor * 10000 + verMinor * 100 + verPatch
        versionName = "$verMajor.$verMinor.$verPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The shared journal's server, e.g. -Psecondes.journalBaseUrl=https://example.org/journal.
        // Empty by default: no server is provisioned yet, so the days filled stay "not sent".
        val journalBaseUrl = (findProperty("secondes.journalBaseUrl") as String?).orEmpty()
        buildConfigField("String", "JOURNAL_BASE_URL", "\"${journalBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    // Signing key, read from env vars or Gradle properties, never from committed files.
    // When any of the four is missing, no release signing config is created: assembleRelease
    // still works locally and produces an unsigned APK (app-release-unsigned.apk), and
    // assembleDebug is unaffected (it always uses the debug keystore).
    val keystorePath = releaseSetting("SECONDES_KEYSTORE_PATH")
    val keystorePassword = releaseSetting("SECONDES_KEYSTORE_PASSWORD")
    val keyAliasName = releaseSetting("SECONDES_KEY_ALIAS")
    val keyPasswordValue = releaseSetting("SECONDES_KEY_PASSWORD")
    val canSignRelease = listOf(keystorePath, keystorePassword, keyAliasName, keyPasswordValue).all { it != null }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
