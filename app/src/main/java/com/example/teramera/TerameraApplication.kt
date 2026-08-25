package com.example.teramera

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TerameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    /** Manual init — the google-services plugin doesn't support this project's AGP. */
    private fun initFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        if (BuildConfig.FIREBASE_PROJECT_ID.isEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID.ifEmpty { "teramera" })
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build(),
            )
        }
    }
}
