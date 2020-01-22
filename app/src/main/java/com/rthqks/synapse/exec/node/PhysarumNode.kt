package com.rthqks.synapse.exec.node

import android.opengl.GLES32
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.gl.Texture
import com.rthqks.synapse.logic.NumAgents
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.sqrt

class PhysarumNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val numAgents = properties[NumAgents]
    private val frameDuration = 33L
    private var envSize = Size(1024, 1024)
    private val agentDimension = ceil(sqrt(numAgents.toDouble())).toInt()
    private val debounce = AtomicBoolean()

    private val agentTexture1 =
        Texture(GLES32.GL_TEXTURE_2D, GLES32.GL_CLAMP_TO_EDGE, GLES32.GL_NEAREST)
    private val agentTexture2 =
        Texture(GLES32.GL_TEXTURE_2D, GLES32.GL_CLAMP_TO_EDGE, GLES32.GL_NEAREST)
    private val envTexture1 = Texture(GLES32.GL_TEXTURE_2D, GLES32.GL_REPEAT, GLES32.GL_LINEAR)
    private val envTexture2 = Texture(GLES32.GL_TEXTURE_2D, GLES32.GL_REPEAT, GLES32.GL_LINEAR)

    private var agentTexture = agentTexture1
    private var envTexture = envTexture1

    private var agentEvent: VideoEvent? = null
    private var envEvent: VideoEvent? = null

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT_AGENT -> {
                VideoConfig(
                    GLES32.GL_TEXTURE_2D,
                    agentDimension,
                    agentDimension,
                    GLES32.GL_RGBA16F,
                    GLES32.GL_RGBA,
                    GLES32.GL_FLOAT,
                    offersSurface = true
                ) as C
            }
            OUTPUT_ENV -> {

                Log.d(TAG, "output $envSize")
                withTimeoutOrNull(20) {
                    configAsync(INPUT_ENV).await()
                }
                Log.d(TAG, "output $envSize")

                VideoConfig(
                    GLES32.GL_TEXTURE_2D,
                    envSize.width,
                    envSize.height,
                    GLES32.GL_R8,
                    GLES32.GL_RED,
                    GLES32.GL_UNSIGNED_BYTE,
                    offersSurface = true
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        when (key) {
            INPUT_ENV -> envSize = (config as VideoConfig).size
        }
        super.setConfig(key, config)
    }

    override suspend fun create() {
        Log.d(TAG, "numAgents $numAgents agentDimension $agentDimension")
    }

    override suspend fun initialize() {
        val agentIn = connection(INPUT_AGENT)
        val agentOut = connection(OUTPUT_AGENT)
        val envIn = connection(INPUT_ENV)
        val envOut = connection(OUTPUT_ENV)
        Log.d(TAG, "$agentIn\n$agentOut\n\n$envIn\n$envOut")

        agentOut?.prime(VideoEvent(), VideoEvent())
        envOut?.prime(VideoEvent(), VideoEvent())
    }

    override suspend fun start() = coroutineScope {
        startJob = launch {

            val agentIn = channel(INPUT_AGENT)
            val envIn = channel(INPUT_ENV)
            val agentOut = channel(OUTPUT_AGENT)
            val envOut = channel(OUTPUT_ENV)
            if (agentIn == null && envIn == null && agentOut == null && envOut == null) {
                Log.d(TAG, "no connection")
                return@launch
            }

            execute()

            whileSelect {
                agentIn?.onReceive {
                    agentEvent?.let { agentIn.send(it) }
                    agentEvent = it
//                    agentIn.send(it)
                    debounceExecute()
                    !it.eos
                }
                envIn?.onReceive {
                    envEvent?.let { envIn.send(it) }
                    envEvent = it
//                    envIn.send(it)
                    debounceExecute()
                    !it.eos
                }
                if (agentIn == null && envIn == null) {
                    onTimeout(frameDuration) {
                        Log.d(TAG, "timeout")
                        debounceExecute()
                        isActive
                    }
                }
            }
            agentEvent?.let { agentIn?.send(it) }
            envEvent?.let { envIn?.send(it) }
        }
    }

    override suspend fun stop() {
        startJob?.join()
        val agentOut = channel(OUTPUT_AGENT)
        val envOut = channel(OUTPUT_ENV)

        agentOut?.receive()?.also {
            it.eos = true
            agentOut.send(it)
        }

        envOut?.receive()?.also {
            it.eos = true
            envOut.send(it)
        }
    }

    override suspend fun release() {
    }

    private suspend fun execute() {
        val agentOut = channel(OUTPUT_AGENT)
        val envOut = channel(OUTPUT_ENV)
//        Log.d(TAG, "execute")
    }

    private suspend fun debounceExecute() {
        if (debounce.compareAndSet(false, true)) {
            delay(frameDuration)
            execute()
            debounce.set(false)
        }
    }

    companion object {
        const val TAG = "PhysarumNode"
        val INPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("input_agent")
        val INPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("input_env")
        val OUTPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("output_agent")
        val OUTPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("output_env")
    }
}