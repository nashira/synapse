package com.rthqks.synapse.inject

import android.content.Context
import androidx.room.Room
import com.rthqks.synapse.SynapseApp
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.data.SynapseDb
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

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