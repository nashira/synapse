package com.rthqks.synapse.inject

import android.content.Context
import androidx.room.Room
import com.rthqks.synapse.SynapseApp
import com.rthqks.flow.assets.AssetManager
import com.rthqks.flow.assets.VideoStorage
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

@Module
class ExecutionModule {

//    @Provides
//    fun provideDispatcher() = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
//
//    @Provides
//    fun provideGlesManager(assetManager: AssetManager) = GlesManager(assetManager)
//
//    @Provides
//    fun provideCameraManager(context: Context) = CameraManager(context)

//    @Provides
//    @Named("video")
//    fun provideVideoEncoder() = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//
//    @Provides
//    @Named("audio")
//    fun provideAudioEncoder() = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)

    @Singleton
    @Provides
    fun provideAssetManager(context: Context) = com.rthqks.flow.assets.AssetManager(context)

    @Singleton
    @Provides
    fun provideVideoStorage(context: Context) = com.rthqks.flow.assets.VideoStorage(context)
}