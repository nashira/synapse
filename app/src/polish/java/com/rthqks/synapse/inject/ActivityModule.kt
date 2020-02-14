package com.rthqks.synapse.inject

import com.rthqks.synapse.polish.SettingsDialog
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeSettingsDialog(): SettingsDialog
}