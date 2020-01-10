package com.rthqks.synapse.inject

import com.rthqks.synapse.ui.build.ConnectionFragment
import com.rthqks.synapse.ui.build.NetworkFragment
import com.rthqks.synapse.ui.build.NodeFragment
import com.rthqks.synapse.ui.build.NodeListDialog
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

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
