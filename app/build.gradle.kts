plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.cbkii.netveil"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.cbkii.netveil"
        minSdk = 35
        targetSdk = 35
        versionCode = 201
        versionName = "0.2.1"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
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

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    // API 101 remains intentional for compatibility with current Vector/LSPosed API-101+ frameworks.
    compileOnly("io.github.libxposed:api:101.0.1")
    testImplementation("junit:junit:4.13.2")
}
