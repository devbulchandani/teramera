import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// FCM without the google-services plugin (it doesn't support AGP 9 yet):
// read the Firebase config straight into BuildConfig fields.
val firebaseConfig: Map<*, *>? = project.file("google-services.json").takeIf { it.exists() }?.let {
    groovy.json.JsonSlurper().parseText(it.readText()) as Map<*, *>
}
val firebaseProjectInfo = firebaseConfig?.get("project_info") as? Map<*, *>
val firebaseClientInfo = (firebaseConfig?.get("client") as? List<*>)?.firstOrNull()
    ?.let { (it as? Map<*, *>)?.get("client_info") } as? Map<*, *>
val firstApiKey = ((firebaseConfig?.get("client") as? List<*>)?.firstOrNull()
    ?.let { (it as? Map<*, *>)?.get("api_key") } as? List<*>)?.firstOrNull() as? Map<*, *>
val firebaseProjectId = firebaseProjectInfo?.get("project_id")?.toString() ?: ""
val firebaseSenderId = firebaseProjectInfo?.get("project_number")?.toString() ?: ""
val firebaseAppId = firebaseClientInfo?.get("mobilesdk_app_id")?.toString() ?: ""
val firebaseApiKey = firstApiKey?.get("current_key")?.toString() ?: ""

android {
    namespace = "com.example.teramera"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.teramera"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "0.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Sign-In: put google.webClientId=<WEB client id> in local.properties
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${props.getProperty("google.webClientId", "")}\"")

        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.playauth)
    implementation(libs.googleid)
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}