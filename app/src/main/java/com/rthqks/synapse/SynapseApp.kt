package com.rthqks.synapse

import com.facebook.stetho.Stetho
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import com.rthqks.synapse.inject.DaggerAppComponent

class SynapseApp : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerAppComponent.builder()
            .application(this)
            .build()

    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
    }
}