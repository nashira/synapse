package com.rthqks.synapse.inject

import com.rthqks.synapse.build.AddNodeFragment
import com.rthqks.synapse.build.ConnectionFragment
import com.rthqks.synapse.build.NetworkFragment
import com.rthqks.synapse.build.NodeListDialog
import com.rthqks.synapse.polish.EffectsFragment
import com.rthqks.synapse.polish.LutFragment
import com.rthqks.synapse.polish.SettingsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeSettingsDialog(): SettingsFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNetworkFragment(): NetworkFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeConnectionFragment(): ConnectionFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNodeListDialog(): NodeListDialog

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeAddNodeFragment(): AddNodeFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeLutFragment(): LutFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeEffectsFragment(): EffectsFragment
}