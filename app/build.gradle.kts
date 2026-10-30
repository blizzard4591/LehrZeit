import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "de.janaberger.lehrzeit"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.janaberger.lehrzeit"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Only where the release key is set up, in ~/.gradle/gradle.properties (see RELEASING.md).
    // Elsewhere, e.g. on F-Droid's build server, the release APK stays unsigned.
    signingConfigs {
        providers.gradleProperty("lehrzeit.storeFile").orNull?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = providers.gradleProperty("lehrzeit.storePassword").orNull
                keyAlias = providers.gradleProperty("lehrzeit.keyAlias").orNull
                // A PKCS12 keystore's key has the keystore's password.
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        // A separate package, so a test install and the released app can live side by side.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // F-Droid: don't embed the dependency metadata block, which is encrypted with a Google key.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

room {
    // Exported schemas are committed so that future migrations can be written and tested.
    schemaDirectory("$projectDir/schemas")
}

// APKs to hand out, named with version and commit: ./gradlew distDebug (or distRelease) writes e.g.
// dist/LehrZeit-0.2.0-debug-9eaae7b.apk. Uncommitted changes add "-dirty"; without git, the commit is left out.
androidComponents {
    // Local, not top-level: the rename below must not capture the build script, or the configuration cache fails.
    val commit = providers.exec {
        commandLine("git", "describe", "--always", "--dirty", "--exclude=*")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }

    onVariants { variant ->
        val baseName = "LehrZeit-${android.defaultConfig.versionName}-${variant.name}"
        tasks.register<Copy>("dist${variant.name.replaceFirstChar(Char::uppercase)}") {
            group = "distribution"
            description = "Copies the ${variant.name} APK to dist/, named with its version and commit."
            from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
            into(rootDir.resolve("dist"))
            rename { file ->
                // Without the release key (see signingConfigs), release APKs are unsigned and can't be installed.
                val unsigned = if (file.endsWith("-unsigned.apk")) "-unsigned" else ""
                val hash = runCatching { commit.get() }.getOrDefault("").let { if (it.isEmpty()) "" else "-$it" }
                "$baseName$unsigned$hash.apk"
            }
            doNotTrackState("The commit can change while the APK stays the same.")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Glance brings an old WorkManager (2.7) that predates current Android versions.
    implementation(libs.androidx.work.runtime)
    // Declared explicitly so kotlinx-coroutines-test runs against the same version.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Checks the database migrations against the exported schemas.
    androidTestImplementation(libs.androidx.room.testing)
}
