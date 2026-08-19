package com.ridego.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ridego.app.data.AppState

class RideGoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        observeForeground()
    }

    /**
     * ProcessLifecycleOwner reports whether any RideGo activity is visible,
     * for the process as a whole. A single activity's onResume/onPause would
     * also flip during configuration changes and split-screen transitions,
     * which is not what "the driver is looking at RideGo" means.
     */
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
