package com.alumanager

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Initialise Firebase manuellement avec la configuration du projet
 * "alu-manager" (pas besoin du fichier google-services.json).
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyDYEknTIVOiFvzQsXiiuFDW_qm9shhZfV8")
                .setApplicationId("1:1017863578454:web:6662a753e93f15d6d0acf7")
                .setProjectId("alu-manager")
                .setStorageBucket("alu-manager.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
