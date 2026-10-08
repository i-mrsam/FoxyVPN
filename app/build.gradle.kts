import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val hevSocks5TunnelVersion = "2.17.1"
val hevSocks5TunnelDir = file("src/main/jni")

val fetchHevSocks5Tunnel = tasks.register<Exec>("fetchHevSocks5Tunnel") {
    val marker = File(hevSocks5TunnelDir, "Android.mk")
    outputs.file(marker)
    onlyIf { !marker.exists() }
    doFirst { hevSocks5TunnelDir.mkdirs() }
    commandLine(
        "git", "clone",
        "--branch", hevSocks5TunnelVersion,
        "--depth", "1",
        "--recursive",
        "--shallow-submodules",
        "https://github.com/heiher/hev-socks5-tunnel.git",
        hevSocks5TunnelDir.absolutePath,
    )
}

tasks.matching { it.name.startsWith("externalNativeBuild") || it.name.contains("NdkBuild") }
    .configureEach { dependsOn(fetchHevSocks5Tunnel) }
tasks.named("preBuild").configure { dependsOn(fetchHevSocks5Tunnel) }

val releaseKeystoreFile = rootProject.file("release-keystore.jks")
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val releaseKeyAlias: String? = System.getenv("SIGNING_KEY_ALIAS")
    ?: keystoreProperties.getProperty("keyAlias")
    ?: (project.findProperty("SIGNING_KEY_ALIAS") as? String)

val releaseKeyPassword: String? = System.getenv("SIGNING_KEY_PASSWORD")
    ?: keystoreProperties.getProperty("keyPassword")
    ?: (project.findProperty("SIGNING_KEY_PASSWORD") as? String)

val releaseStorePassword: String? = System.getenv("SIGNING_STORE_PASSWORD")
    ?: keystoreProperties.getProperty("storePassword")
    ?: (project.findProperty("SIGNING_STORE_PASSWORD") as? String)

val hasReleaseSigning = releaseKeystoreFile.exists() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank()

android {
    namespace = "com.mrsam.foxyvpn"
    compileSdk = 35

    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.mrsam.foxyvpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 42
        versionName = "2.0.0"

        externalNativeBuild {
            ndkBuild {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                arguments += listOf(

                    "APP_PLATFORM=android-26",

                    "APP_CFLAGS=-DPKGNAME=com/mrsam/foxyvpn/vpn/tun -DCLSNAME=HevSocks5Tunnel",
                )
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }



    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/*.SF")
        resources.excludes.add("META-INF/*.DSA")
        resources.excludes.add("META-INF/*.RSA")
        resources.pickFirsts.add("META-INF/io.netty.versions.properties")
    }
}


kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0-alpha10")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("io.netty:netty-handler:4.1.115.Final")
    implementation("io.netty:netty-codec-http2:4.1.115.Final")
    implementation("io.netty:netty-transport:4.1.115.Final")

    implementation("io.netty:netty-handler-proxy:4.1.115.Final")

    implementation("org.conscrypt:conscrypt-android:2.5.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

