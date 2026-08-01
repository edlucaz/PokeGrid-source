plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "online.idleworld.pokegrid"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
