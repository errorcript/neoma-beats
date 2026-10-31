plugins {
    id("com.android.application")
}

android {
    namespace = "space.neoma.beats"
    compileSdk = 35

    defaultConfig {
        applicationId = "space.neoma.beats"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        manifestPlaceholders["hostName"] = "beats.neoma.space"
        manifestPlaceholders["defaultUrl"] = "https://beats.neoma.space/tools/music"
        manifestPlaceholders["launcherName"] = "Neoma Beats"
        manifestPlaceholders["assetStatements"] = """[{ "relation": ["delegate_permission/common.handle_all_urls"], "target": { "namespace": "web", "site": "https://beats.neoma.space" } }]"""
    }

    signingConfigs {
        create("release") {
            storeFile = file("../neoma-release.jks")
            storePassword = "neoma2026"
            keyAlias = "neomabeats"
            keyPassword = "neoma2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
    implementation("androidx.browser:browser:1.8.0")
}
