package com.rthqks.synapse.inject

import com.rthqks.synapse.build2.ConnectionFragment
import com.rthqks.synapse.build2.NetworkFragment
import com.rthqks.synapse.build2.NodeFragment
import com.rthqks.synapse.build2.NodeListDialog
import com.rthqks.synapse.polish.SettingsDialog
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeSettingsDialog(): SettingsDialog

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNetworkFragment(): NetworkFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNodeFragment(): NodeFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeConnectionFragment(): ConnectionFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNodeListDialog(): NodeListDialog
}