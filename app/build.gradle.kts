import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release imza bilgileri repoda değil, bilgisayardaki keystore.properties dosyasında (.gitignore'da)
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.reader(Charsets.UTF_8).use { load(it) }
}

android {
    namespace = "com.celtikarda.galerireels"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.celtikarda.galerireels"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "0.9"

        // libVLC her işlemci mimarisi için ~25 MB; sadece telefonlardaki 64-bit ARM
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // keystore.properties yoksa (ör. GitHub'dan indirip derleyen biri) imzasız APK çıkar
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Debug da aynı anahtarla imzalanır; telefondaki sürüm silinmeden debug ↔ release
            // birbirinin üstüne kurulabilir (silmek beğenileri, izlenmeleri götürür)
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    packaging {
        // VLC'nin kütüphanesi 46 MB; sıkıştırılınca APK neredeyse yarıya iniyor (indirmesi hızlı)
        jniLibs.useLegacyPackaging = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.coil.compose)
    implementation(libs.libvlc)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
