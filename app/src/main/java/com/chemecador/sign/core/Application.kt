package com.chemecador.sign.core

import android.app.Application
import com.chemecador.sign.utils.log.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLoggingTree.deleteOldLogs(applicationContext)
        Timber.plant(FileLoggingTree(applicationContext))
        Timber.plant(Timber.DebugTree())
    }

}

