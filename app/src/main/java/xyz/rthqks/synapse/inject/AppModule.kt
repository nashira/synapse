package xyz.rthqks.synapse.inject

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import xyz.rthqks.synapse.SynapseApp
import xyz.rthqks.synapse.data.SynapseDao
import xyz.rthqks.synapse.data.SynapseDb
import xyz.rthqks.synapse.ui.browse.GraphListActivity
import xyz.rthqks.synapse.ui.edit.GraphEditActivity
import xyz.rthqks.synapse.ui.exec.ExecGraphActivity
import javax.inject.Singleton

@Module(includes = [MainModule::class, DataModule::class])
abstract class AppModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeGraphListActivity(): GraphListActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeGraphEditActivity(): GraphEditActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeExecGraphActivity(): ExecGraphActivity
}

@Module
class MainModule {

    @Provides
    @Singleton
    fun provideContext(app: SynapseApp): Context = app
}

@Module
class DataModule {

    @Singleton
    @Provides
    fun provideSynapseDb(context: Context): SynapseDb {
        return Room.databaseBuilder(context, SynapseDb::class.java, SynapseDb.NAME)
            .build()
    }

    @Singleton
    @Provides
    fun provideSynapseDao(db: SynapseDb): SynapseDao {
        return db.dao()
    }
}