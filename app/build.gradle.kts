import java.util.Properties

plugins {
    id("com.android.application")
}

val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties()
val hasReleaseSigning = releasePropertiesFile.isFile
if (hasReleaseSigning) {
    releasePropertiesFile.inputStream().use { releaseProperties.load(it) }
}

fun releaseProperty(name: String): String =
    releaseProperties.getProperty(name) ?: error("Missing release signing property: $name")

android {
    namespace = "io.github.cbkii.netveil"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ip.netveil"
        minSdk = 35
        targetSdk = 35
        versionCode = 201
        versionName = "0.2.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseProperty("storeFile"))
                storePassword = releaseProperty("storePassword")
                keyAlias = releaseProperty("keyAlias")
                keyPassword = releaseProperty("keyPassword")
            }
        }
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
