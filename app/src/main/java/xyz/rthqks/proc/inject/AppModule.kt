package xyz.rthqks.proc.inject

import dagger.Module
import dagger.android.ContributesAndroidInjector
import xyz.rthqks.proc.ui.GraphEditActivity

@Module
abstract class AppModule {


    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeVisionActivity(): GraphEditActivity
}