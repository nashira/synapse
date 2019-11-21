package xyz.rthqks.synapse.inject

import dagger.Module
import dagger.android.ContributesAndroidInjector
import xyz.rthqks.synapse.ui.build.NodeFragment
import xyz.rthqks.synapse.ui.edit.EditGraphFragment
import xyz.rthqks.synapse.ui.edit.EditPropertiesFragment

@Module(includes = [ViewModels::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeGraphEditFragment(): EditGraphFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeEditPropertiesFragment(): EditPropertiesFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeNodeFragment(): NodeFragment
}
