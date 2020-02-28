package com.rthqks.synapse.inject

import com.rthqks.synapse.polish.GalleryActivity
import com.rthqks.synapse.polish.PolishActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [MainModule::class, DataModule::class, ExecutionModule::class])
abstract class AppModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributePolishActivity(): PolishActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeGalleryActivity(): GalleryActivity
}