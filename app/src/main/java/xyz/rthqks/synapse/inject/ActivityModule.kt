package xyz.rthqks.synapse.inject

import dagger.Module
import dagger.android.ContributesAndroidInjector
import xyz.rthqks.synapse.ui.build.ConnectionFragment
import xyz.rthqks.synapse.ui.build.NodeFragment
import xyz.rthqks.synapse.ui.build.NodeListDialog
import xyz.rthqks.synapse.ui.edit.EditPropertiesFragment

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeEditPropertiesFragment(): EditPropertiesFragment

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
