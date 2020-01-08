package com.rthqks.synapse.inject

import dagger.Module
import dagger.android.ContributesAndroidInjector
import com.rthqks.synapse.ui.build.ConnectionFragment
import com.rthqks.synapse.ui.build.GraphFragment
import com.rthqks.synapse.ui.build.NodeFragment
import com.rthqks.synapse.ui.build.NodeListDialog
import com.rthqks.synapse.ui.edit.EditPropertiesFragment

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeEditPropertiesFragment(): EditPropertiesFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeGraphFragment(): GraphFragment

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
