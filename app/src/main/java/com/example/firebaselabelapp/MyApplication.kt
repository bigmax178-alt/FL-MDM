package com.example.firebaselabelapp

import android.app.Application
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.jakewharton.threetenabp.AndroidThreeTen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.firebaselabelapp.bluetoothprinter.PrinterManager
import com.example.firebaselabelapp.kiosk.KioskManager
import com.example.firebaselabelapp.subscription.SessionValidator
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class MyApplication : Application() {

    // Singleton repository instance
    lateinit var repository: FirestoreRepository
        private set

    lateinit var kioskManager: KioskManager
        private set

    lateinit var printerManager: PrinterManager
        private set

    lateinit var db: FirebaseFirestore
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this) // Initialize AndroidThreeTen here

        db = Firebase.firestore
        repository = FirestoreRepository(this)
        kioskManager = KioskManager(this)

        printerManager = PrinterManager(this, kioskManager)

        // Register the lifecycle observer to manage the SessionValidator
//        val lifecycleObserver = AppLifecycleObserver(this)
//        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    override fun onTerminate() {
        super.onTerminate()

        // Clean up repository
        if (::repository.isInitialized) repository.cleanup()
        if(::printerManager.isInitialized) printerManager.cancelScope()
    }
}

//class AppLifecycleObserver(private val context: android.content.Context) : DefaultLifecycleObserver {
//
//    override fun onStart(owner: LifecycleOwner) {
//        // This is called when the app enters the foreground.
//        Log.d("AppLifecycleObserver", "App entered foreground.")
//        SessionValidator.start(context)
//    }
//
//    override fun onStop(owner: LifecycleOwner) {
//        // This is called when the app enters the background.
//        Log.d("AppLifecycleObserver", "App entered background.")
//        SessionValidator.stop()
//    }
//}