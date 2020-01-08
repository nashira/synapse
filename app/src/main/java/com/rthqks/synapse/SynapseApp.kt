package com.rthqks.synapse

import com.rthqks.synapse.inject.DaggerAppComponent
import com.rthqks.synapse.util.StethoWrapper
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication

class SynapseApp : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerAppComponent.builder()
            .application(this)
            .build()

    override fun onCreate() {
        super.onCreate()
        StethoWrapper.init(this)
    }
}