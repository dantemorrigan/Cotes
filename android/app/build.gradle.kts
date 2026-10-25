plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// versionCode — число коммитов: растёт с каждой сборкой из main и одинаково для тега и ветки.
// versionName — из тега vX.Y.Z при релизе (или COTES_VERSION_NAME от релизного workflow), иначе X.Y.Z-dev.N.
val baseVersion = "1.2.5"
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)
val releaseTag: String? = System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" && it.startsWith("v") }
    ?.removePrefix("v")

// Ключ подписи расшифровывает scripts/decrypt-key.sh (только в CI). Без него — отладочный ключ.
val releaseKeystore: File? = System.getenv("COTES_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "io.github.dantemorrigan.cotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.dantemorrigan.cotes"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = System.getenv("COTES_VERSION_NAME") ?: releaseTag ?: "$baseVersion-dev.$commitCount"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storeType = "PKCS12"
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "cotes"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    // Вход в Google и доступ к скрытой папке приложения в Drive.
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    // Отправка изменений, когда приложение уже в фоне.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Ставит готовые профили Compose при установке: интерфейс плавный с первого запуска.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    // В юнит-тестах org.json из android.jar — заглушка, нужна настоящая библиотека.
    testImplementation("org.json:json:20260814")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
