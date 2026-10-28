package com.pagecast.app

import android.app.Application
import com.pagecast.app.di.AppContainer

class PagecastApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.progressRecorder.start()
    }
}
