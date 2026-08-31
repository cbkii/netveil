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
    enableKotlin = false

    defaultConfig {
        applicationId = "dev.ip.netveil"
        minSdk = 35
        targetSdk = 36
        versionCode = 205
        versionName = "1.1.1"
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

    lint {
        // Keep release lint policy explicit and version-controlled. app/lint.xml intentionally
        // contains no issue suppressions, so lintRelease remains an authoritative release gate.
        lintConfig = file("lint.xml")
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
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json implementation is not executable in local JVM tests.
    testImplementation("org.json:json:20240303")
}
