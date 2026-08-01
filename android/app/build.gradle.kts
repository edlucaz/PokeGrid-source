plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "online.idleworld.pokegrid"
    compileSdk = 36

    // CI (see .github/workflows/release.yml) passes VERSION_CODE=<run number> so every published
    // release's tag matches the versionCode actually baked into that APK, which is what the
    // in-app UpdateChecker compares against. Local builds fall back to 1.
    val releaseVersionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = "1.0.$releaseVersionCode"
    }

    // Not a real secret: this keystore exists only so every build (mine, CI's, yours) produces
    // the same signature, which is what lets Android install an update over an existing install
    // instead of demanding an uninstall first. It doesn't protect anything sensitive — anyone
    // with the source can already rebuild their own copy — so it's committed to the repo on
    // purpose rather than kept out of it like a real signing key would be.
    signingConfigs {
        create("update") {
            storeFile = file("../keystore/pokegrid-update.jks")
            storePassword = "pokegrid-update"
            keyAlias = "pokegrid-update"
            keyPassword = "pokegrid-update"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("update")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Each flavor is a distinct app (own applicationId, own icon/accent, own GameConfig) that
    // reuses all the game-agnostic plumbing (GamePanel, CredentialStore, InjectedScripts...) from
    // src/main. Flavor-only sources live in src/pokegrid and src/pokedream.
    flavorDimensions += "game"
    productFlavors {
        create("pokegrid") {
            dimension = "game"
            applicationId = "online.idleworld.pokegrid"
            resValue("string", "app_name", "PokeGrid")
        }
        create("pokedream") {
            dimension = "game"
            applicationId = "br.com.pokedream.grid"
            resValue("string", "app_name", "PokeDream")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity)
}
