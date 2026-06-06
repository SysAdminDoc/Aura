package com.freevibe.service

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckInstaller {
    fun install(context: Context) {
        FirebaseApp.initializeApp(context)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )
        Log.d("AppCheck", "Installed Firebase App Check debug provider")
    }
}
