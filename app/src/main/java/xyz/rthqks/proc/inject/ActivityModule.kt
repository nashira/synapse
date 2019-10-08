package xyz.rthqks.proc.inject

import dagger.Module
import dagger.android.ContributesAndroidInjector
import xyz.rthqks.proc.ui.edit.EditGraphFragment
import xyz.rthqks.proc.ui.edit.EditPropertiesFragment

@Module(includes = [ViewModelModule::class])
abstract class ActivityModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeGraphEditFragment(): EditGraphFragment

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributeEditPropertiesFragment(): EditPropertiesFragment
}
