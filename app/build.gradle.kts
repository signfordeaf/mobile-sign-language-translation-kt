import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The Android equivalent of Flutter's `--dart-define`: resolve a value from, in order, a Gradle
// project property (`-P<NAME>=...` / gradle.properties), the gitignored `local.properties`, or an
// environment variable, and bake it into BuildConfig. Returns "" when unset. `local.properties`
// is the recommended spot — it is not committed, so real credentials never enter version control.
val sfdLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun sfdDefine(name: String): String {
    val raw = (project.findProperty(name) as String?)
        ?: sfdLocalProps.getProperty(name)
        ?: System.getenv(name)
        ?: ""
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.signfordeaf.mobilesignlanguage"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.signfordeaf.mobilesignlanguage"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Credentials injected at build time (see README "Running the example"). Same idea as the
        // Flutter example's --dart-define toolArgs. Empty by default → the demo falls back to the
        // on-screen fields.
        buildConfigField("String", "SIGNFORDEAF_API_KEY", "\"${sfdDefine("SIGNFORDEAF_API_KEY")}\"")
        buildConfigField("String", "SIGNFORDEAF_API_URL", "\"${sfdDefine("SIGNFORDEAF_API_URL")}\"")
        buildConfigField("String", "SIGNFORDEAF_ORIGIN_URL", "\"${sfdDefine("SIGNFORDEAF_ORIGIN_URL")}\"")
        buildConfigField("String", "SIGNFORDEAF_FDID", "\"${sfdDefine("SIGNFORDEAF_FDID")}\"")
        buildConfigField("String", "SIGNFORDEAF_TID", "\"${sfdDefine("SIGNFORDEAF_TID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(path = ":signtranslate"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
