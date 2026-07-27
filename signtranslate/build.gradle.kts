import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.signfordeaf.signtranslate"
    compileSdk = 34

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

mavenPublishing {
    // Publishes the release AAR + sources + javadoc jar.
    configure(AndroidSingleVariantLibrary(variant = "release"))

    coordinates("io.github.signfordeaf", "signtranslate", "2.0.0")

    pom {
        name.set("SignForDeaf Mobile Sign Language")
        description.set(
            "On-device sign-language translation for Android. Select any text and play " +
                "a looping sign-language video in a bottom sheet."
        )
        url.set("https://github.com/signfordeaf/mobile-sign-language-translation-kt")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/signfordeaf/mobile-sign-language-translation-kt/blob/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("signfordeaf")
                name.set("SignForDeaf")
                url.set("https://github.com/signfordeaf")
            }
        }
        scm {
            url.set("https://github.com/signfordeaf/mobile-sign-language-translation-kt")
            connection.set("scm:git:git://github.com/signfordeaf/mobile-sign-language-translation-kt.git")
            developerConnection.set("scm:git:ssh://git@github.com/signfordeaf/mobile-sign-language-translation-kt.git")
        }
    }

    // Host (Sonatype Central Portal) and signing are both driven by gradle.properties
    // keys read natively by the plugin: SONATYPE_HOST and RELEASE_SIGNING_ENABLED.
    // OSSRH shut down on 2025-06-30, so Central Portal is the only option.
    // Signing stays OFF locally (so keyless publishToMavenLocal + JitPack work) and the
    // release CI turns it on with -PRELEASE_SIGNING_ENABLED=true plus the in-memory key.
}

dependencies {

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Video playback (Media3 / ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    // AndroidX
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}