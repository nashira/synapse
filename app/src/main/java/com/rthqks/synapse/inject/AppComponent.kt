package com.rthqks.synapse.inject


import com.rthqks.synapse.SynapseApp
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = [AndroidSupportInjectionModule::class, AppModule::class])
interface AppComponent: AndroidInjector<SynapseApp> {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: SynapseApp): Builder

        fun build(): AppComponent
    }
}