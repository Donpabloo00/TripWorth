package com.ridego.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ridego.app.data.AppState

class TripWorthApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        observeForeground()
    }

    private fun observeForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppState.setAppInForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppState.setAppInForeground(false)
            }
        })
    }
}
