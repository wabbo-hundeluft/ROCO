plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.wabbo.healthconnectcoach"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wabbos.healthcoach"
        minSdk = 28
        targetSdk = 37
        versionCode = 23
        versionName = "2.0.20"
        manifestPlaceholders["healthCoachLabel"] = "ROCO"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            val visualHome = gradle.gradleUserHomeDir.resolve("health-coach-test-home")
            it.systemProperty("user.home", visualHome.absolutePath)
            it.doFirst { visualHome.mkdirs() }
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("healthCoachPreview").orNull == "true") {
                applicationIdSuffix = ".v2preview"
                versionNameSuffix = "-preview"
                manifestPlaceholders["healthCoachLabel"] = "ROCO"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.health.connect.client)
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    // The older transitive annotation checker crashes in AGP 9.4's K2 analysis.
    implementation("androidx.annotation:annotation-experimental:1.6.0")

    testImplementation(libs.androidx.arch.core.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.json:json:20240303")
}
