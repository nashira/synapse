package com.rthqks.synapse.inject

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.room.Room
import com.rthqks.synapse.SynapseApp
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.data.SynapseDb
import com.rthqks.synapse.exec.CameraManager
import com.rthqks.synapse.gl.GlesManager
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Named
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

    @Provides
    fun provideDispatcher() = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    @Provides
    fun provideGlesManager(assetManager: AssetManager) = GlesManager(assetManager)

    @Provides
    fun provideCameraManager(context: Context) = CameraManager(context)

    @Provides
    @Named("video")
    fun provideVideoEncoder() = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

    @Provides
    @Named("audio")
    fun provideAudioEncoder() = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)

    @Singleton
    @Provides
    fun provideAssetManager(context: Context) = AssetManager(context)

    @Singleton
    @Provides
    fun provideVideoStorage(context: Context) = VideoStorage(context)
}