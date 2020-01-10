package com.rthqks.synapse.inject

import android.content.Context
import androidx.room.Room
import com.rthqks.synapse.SynapseApp
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.data.SynapseDb
import com.rthqks.synapse.ui.browse.NetworkListActivity
import com.rthqks.synapse.ui.build.BuilderActivity
import com.rthqks.synapse.ui.exec.NetworkActivity
import com.rthqks.synapse.ui.splash.SplashActivity
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import javax.inject.Singleton

@Module(includes = [MainModule::class, DataModule::class])
abstract class AppModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeNetworkListActivity(): NetworkListActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeExecNetworkActivity(): NetworkActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeBuilderActivity(): BuilderActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ActivityModule::class])
    abstract fun contributeSplashActivity(): SplashActivity
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
            .fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun provideSynapseDao(db: SynapseDb): SynapseDao {
        return db.dao()
    }
}