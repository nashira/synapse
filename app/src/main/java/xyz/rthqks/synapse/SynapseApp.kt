package xyz.rthqks.synapse

import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import xyz.rthqks.synapse.inject.DaggerAppComponent

class SynapseApp : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerAppComponent.builder()
            .application(this)
            .build()
}