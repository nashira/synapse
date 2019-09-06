package xyz.rthqks.proc

import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import xyz.rthqks.proc.inject.DaggerAppComponent

class ProcApp : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerAppComponent.builder()
            .application(this)
            .build()
}