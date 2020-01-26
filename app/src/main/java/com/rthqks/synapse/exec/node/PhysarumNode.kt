package com.rthqks.synapse.exec.node

import android.opengl.GLES32
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.NumAgents
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class PhysarumNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val numAgents = properties[NumAgents]
    private val frameDuration = 33L
    private var envSize = Size(1024, 1024)
    private val agentSize = ceil(sqrt(numAgents.toDouble())).toInt().let { Size(it, it) }


    private val agentTexture1 = Texture(filter = GLES32.GL_NEAREST)
    private val agentTexture2 = Texture(filter = GLES32.GL_NEAREST)
    private val envTexture1 = Texture(repeat = GLES32.GL_REPEAT)
    private val envTexture2 = Texture(repeat = GLES32.GL_REPEAT)
    private val agentFramebuffer1 = Framebuffer()
    private val agentFramebuffer2 = Framebuffer()
    private val envFramebuffer1 = Framebuffer()
    private val envFramebuffer2 = Framebuffer()

    private val agentProgram = Program()
    private val agentMesh = Agent2D(numAgents)
    private val quadMesh = Quad()

    private var agentTexture = agentTexture1
    private var envTexture = envTexture1

    private var agentEvent: VideoEvent? = null
    private var envEvent: VideoEvent? = null

    private val debounce = AtomicInteger()
    private var running = AtomicInteger()
    private var frameCount = 0
    private var lastExecutionTime = 0L

    private val agentConfig: VideoConfig by lazy {
        VideoConfig(
            GLES32.GL_TEXTURE_2D,
            agentSize.width,
            agentSize.height,
            GLES32.GL_RGBA16F,
            GLES32.GL_RGBA,
            GLES32.GL_FLOAT
        )
    }

    private val envConfig: VideoConfig by lazy {
        VideoConfig(
            GLES32.GL_TEXTURE_2D,
            envSize.width,
            envSize.height,
            GLES32.GL_R8,
            GLES32.GL_RED,
            GLES32.GL_UNSIGNED_BYTE
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT_AGENT -> agentConfig as C
            OUTPUT_ENV -> {
                Log.d(TAG, "before output $envSize")

                if (linked(INPUT_ENV) && !cycle(INPUT_ENV)) {
                    val config = configAsync(INPUT_ENV).await()
                    envSize = config.size
                }
                Log.d(TAG, "after output $envSize")
                envConfig as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun create() {
        Log.d(TAG, "numAgents $numAgents agentSize $agentSize")
    }

    override suspend fun initialize() {
        val agentIn = connection(INPUT_AGENT)
        val agentOut = connection(OUTPUT_AGENT)
        val envIn = connection(INPUT_ENV)
        val envOut = connection(OUTPUT_ENV)
        Log.d(TAG, "$agentIn\n$agentOut\n\n$envIn\n$envOut")

        agentOut?.prime(VideoEvent(agentTexture2), VideoEvent(agentTexture1))
        envOut?.prime(VideoEvent(envTexture2), VideoEvent(envTexture1))

        glesManager.glContext {
            initRenderTarget(agentFramebuffer1, agentTexture1, agentConfig)
            initRenderTarget(agentFramebuffer2, agentTexture2, agentConfig)
            initRenderTarget(envFramebuffer1, envTexture1, envConfig)
            initRenderTarget(envFramebuffer2, envTexture2, envConfig)

            val agentVertex = assetManager.readTextAsset("shader/physarum_agent.vert")
            val agentFrag = assetManager.readTextAsset("shader/physarum_agent.frag").let {
                if (envIn?.config?.isOes == true) {
                    it.replace("//{ENV_EXT}", "#define ENV_EXT")
                        .replace("//{AGENT_EXT}", "#define AGENT_EXT")
                } else {
                    it
                }
            }

            agentMesh.initialize()
            quadMesh.initialize()
            agentProgram.apply {
                initialize(agentVertex, agentFrag)
                addUniform(
                    Uniform.Type.Vec2,
                    "resolution",
                    floatArrayOf(agentSize.width.toFloat(), agentSize.height.toFloat())
                )
                addUniform(
                    Uniform.Type.Int,
                    "agent_texture",
                    AGENT_TEXTURE_LOCATION
                )

                addUniform(
                    Uniform.Type.Int,
                    "env_texture",
                    ENV_TEXTURE_LOCATION
                )

            }
        }
    }

    private fun initRenderTarget(framebuffer: Framebuffer, texture: Texture, config: VideoConfig) {
        texture.initialize()
        texture.initData(
            0,
            config.internalFormat,
            config.width,
            config.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES32.glGetError()}")
        framebuffer.initialize(texture.id)
    }

    override suspend fun start() = coroutineScope {
        frameCount = 0
        running.set(1)


        startJob = launch {
            // timer based
//            launch {
//                while (running.get() > 0) {
//                    execute()
//                    delay(frameDuration)
//                }
//            }

            if (!hasConnection()) {
                Log.d(TAG, "no connection")
                return@launch
            }
            val agentIn = channel(INPUT_AGENT)
            val envIn = channel(INPUT_ENV)

            if (linked(INPUT_AGENT) && !cycle(INPUT_AGENT)) running.incrementAndGet()
            if (linked(INPUT_ENV) && !cycle(INPUT_ENV)) running.incrementAndGet()

//            if (linked(INPUT_AGENT) || linked(INPUT_ENV))
            whileSelect {
                agentIn?.onReceive {
                    //                    Log.d(TAG, "agent receive")
                    agentEvent?.let { agentIn.send(it) }
                    agentEvent = it
                    debounceExecute(this@launch)
                    if (it.eos) running.decrementAndGet()
                    running.get() > 0
                }
                envIn?.onReceive {
                    //                    Log.d(TAG, "env receive")
                    envEvent?.let { envIn.send(it) }
                    envEvent = it
                    debounceExecute(this@launch)
                    if (it.eos) running.decrementAndGet()
                    running.get() > 0
                }
                if (agentIn == null && envIn == null) {
                    onTimeout(frameDuration) {
                        //                        Log.d(TAG, "timeout")
                        execute()
                        running.get() > 0
                    }
                }
            }
            agentEvent?.let { Log.d(TAG, "got ${it.count} agent events") }
            envEvent?.let { Log.d(TAG, "got ${it.count} env events") }

            agentEvent?.let { agentIn?.send(it) }
            envEvent?.let { envIn?.send(it) }
            agentEvent = null
            envEvent = null
        }
    }

    private fun hasConnection(): Boolean {
        return (linked(INPUT_AGENT)
                || linked(INPUT_ENV)
                || linked(OUTPUT_AGENT)
                || linked(OUTPUT_ENV))
    }

    override suspend fun stop() {
        running.decrementAndGet()
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
        agentTexture1.release()
        agentTexture2.release()
        envTexture1.release()
        envTexture2.release()
        agentFramebuffer1.release()
        agentFramebuffer2.release()
        envFramebuffer1.release()
        envFramebuffer2.release()
        agentMesh.release()
        quadMesh.release()
        agentProgram.release()
    }

    val r = Random(123)

    private suspend fun execute() {
        lastExecutionTime = SystemClock.elapsedRealtimeNanos()
        val agentOut = channel(OUTPUT_AGENT)
        val envOut = channel(OUTPUT_ENV)
        val agentOutEvent = agentOut?.receive()
        val envOutEvent = envOut?.receive()

        val agentInTexture = agentEvent?.texture ?: agentTexture
        val envInTexture = envEvent?.texture ?: envTexture

        val agentFramebuffer: Framebuffer
        if (agentTexture == agentTexture1) {
            agentTexture = agentTexture2
            agentFramebuffer = agentFramebuffer2
        } else {
            agentTexture = agentTexture1
            agentFramebuffer = agentFramebuffer1
        }

        val envFramebuffer: Framebuffer
        if (envTexture == envTexture1) {
            envTexture = envTexture2
            envFramebuffer = envFramebuffer2
        } else {
            envTexture = envTexture1
            envFramebuffer = envFramebuffer1
        }

        val agentOutTexture = agentTexture
        val envOutTexture = envTexture

        glesManager.glContext {
            GLES32.glUseProgram(agentProgram.programId)
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, agentFramebuffer.id)
            GLES32.glViewport(0, 0, agentSize.width, agentSize.height)
            agentInTexture.bind(GLES32.GL_TEXTURE0)
            envInTexture.bind(GLES32.GL_TEXTURE1)
            agentProgram.bindUniforms()
            quadMesh.execute()

//            GLES32.glClearColor(r.nextFloat(), r.nextFloat(), r.nextFloat(), 1f)
//            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)

            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, envFramebuffer.id)
            GLES32.glViewport(0, 0, envSize.width, envSize.height)
            GLES32.glClearColor(r.nextFloat(), r.nextFloat(), r.nextFloat(), 1f)
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
        }

        frameCount++

        agentOutEvent?.let {
            it.texture = agentOutTexture
            it.count = frameCount
            it.eos = false
            agentOut.send(it)
        }

        envOutEvent?.let {
            it.texture = envOutTexture
            it.count = frameCount
            it.eos = false
            envOut.send(it)
        }
    }

    private suspend fun debounceExecute(scope: CoroutineScope) {
        if (debounce.compareAndSet(0, 1)) {
            scope.launch {
                do {
                    val delay = max(
                        0,
                        frameDuration - (SystemClock.elapsedRealtimeNanos() - lastExecutionTime) / 1_000_000
                    )
                    delay(delay)
                    execute()
                } while (debounce.compareAndSet(2, 1))
                debounce.set(0)
            }
        } else {
            debounce.set(2)
        }
    }

    companion object {
        const val TAG = "PhysarumNode"
        const val AGENT_TEXTURE_LOCATION = 0
        const val ENV_TEXTURE_LOCATION = 1
        val INPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("input_agent")
        val INPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("input_env")
        val OUTPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("output_agent")
        val OUTPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("output_env")
    }
}