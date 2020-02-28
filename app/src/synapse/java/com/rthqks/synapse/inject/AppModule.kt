package com.rthqks.synapse.inject

import com.rthqks.synapse.ui.browse.NetworkListActivity
import com.rthqks.synapse.ui.build.BuilderActivity
import com.rthqks.synapse.ui.exec.NetworkActivity
import com.rthqks.synapse.ui.splash.SplashActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector


@Module(includes = [MainModule::class, DataModule::class, ExecutionModule::class])
abstract class AppModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeNetworkListActivity(): NetworkListActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeExecNetworkActivity(): NetworkActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeBuilderActivity(): BuilderActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeSplashActivity(): SplashActivity
}