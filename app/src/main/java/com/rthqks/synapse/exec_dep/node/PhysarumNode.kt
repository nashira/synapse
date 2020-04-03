package com.rthqks.synapse.exec_dep.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec_dep.NodeExecutor
import com.rthqks.synapse.exec_dep.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class PhysarumNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private val numAgents = properties[NumAgents]
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var envSize = properties[VideoSize]
    private val agentSize = ceil(sqrt(numAgents.toDouble())).toInt().let { Size(it, it) }
    private val sensorAngle: Float get() = properties[SensorAngle]
    private val sensorDistance: Float get() = properties[SensorDistance]
    private val travelAngle: Float get() = properties[TravelAngle]
    private val travelDistance: Float get() = properties[TravelDistance]

    private val agentTexture1 = Texture2d(filter = GLES30.GL_NEAREST)
    private val agentTexture2 = Texture2d(filter = GLES30.GL_NEAREST)
    private val envTexture1 = Texture2d(repeat = GLES30.GL_REPEAT)
    private val envTexture2 = Texture2d(repeat = GLES30.GL_REPEAT)
    private val agentFramebuffer1 = Framebuffer()
    private val agentFramebuffer2 = Framebuffer()
    private val envFramebuffer1 = Framebuffer()
    private val envFramebuffer2 = Framebuffer()

    private val agentProgram = Program()
    private val envProgram = Program()
    private val agentMesh = Agent2D(numAgents)
    private val quadMesh = Quad()

    private var agentTexture = agentTexture1
    private var envTexture = envTexture1

    private var agentEvent: VideoEvent? = null
    private var envEvent: VideoEvent? = null

    private val debounce = AtomicBoolean()
    private var running = AtomicInteger()
    private var frameCount = 0
    private var lastExecutionTime = 0L

    private val agentConfig: VideoConfig by lazy {
        VideoConfig(
            GLES30.GL_TEXTURE_2D,
            agentSize.width,
            agentSize.height,
            GLES30.GL_RGBA16F,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            properties[FrameRate]
        )
    }

    private val envConfig: VideoConfig by lazy {
        VideoConfig(
            GLES30.GL_TEXTURE_2D,
            envSize.width,
            envSize.height,
            GLES30.GL_R8,
            GLES30.GL_RED,
            GLES30.GL_UNSIGNED_BYTE,
            properties[FrameRate]
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

    override suspend fun onCreate() {
        Log.d(TAG, "numAgents $numAgents agentSize $agentSize")
    }

    override suspend fun onInitialize() {
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
                val s = if (envIn?.config?.isOes == true) {
                    it.replace("//{ENV_EXT}", "#define ENV_EXT")
                } else {
                    it
                }
                if (agentIn?.config?.isOes == true) {
                    s.replace("//{AGENT_EXT}", "#define AGENT_EXT")
                } else {
                    s
                }
            }

            val envVertex = assetManager.readTextAsset("shader/physarum_env.vert")
            val envFrag = assetManager.readTextAsset("shader/physarum_env.frag").let {
                if (agentIn?.config?.isOes == true) {
                    it.replace("//{AGENT_EXT}", "#define AGENT_EXT")
                } else {
                    it
                }
            }

            agentMesh.initialize()
            quadMesh.initialize()

            // seed agent texture with random data
            Program().apply {
                val frag = assetManager.readTextAsset("shader/random.frag")
                initialize(agentVertex, frag)
                GLES30.glUseProgram(programId)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, agentFramebuffer1.id)
                GLES30.glViewport(0, 0, agentSize.width, agentSize.height)
                quadMesh.execute()
                release()
            }

            agentProgram.apply {
                initialize(agentVertex, agentFrag)
                addUniform(
                    Uniform.Type.Vec2,
                    "resolution",
                    floatArrayOf(envSize.width.toFloat(), envSize.height.toFloat())
                )

                addUniform(Uniform.Type.Mat4, "texture_matrix", GlesManager.identityMat())
                addUniform(Uniform.Type.Int, "agent_texture", AGENT_TEXTURE_LOCATION)
                addUniform(Uniform.Type.Int, "env_texture", ENV_TEXTURE_LOCATION)
                addUniform(Uniform.Type.Float, SENSOR_ANGLE, sensorAngle)
                addUniform(Uniform.Type.Float, SENSOR_DIST, sensorDistance)
                addUniform(Uniform.Type.Float, TRAVEL_ANGLE, travelAngle)
                addUniform(Uniform.Type.Float, TRAVEL_DIST, travelDistance)
            }

            envProgram.apply {
                initialize(envVertex, envFrag)
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
            }
        }
    }

    private fun initRenderTarget(
        framebuffer: Framebuffer,
        texture: Texture2d,
        config: VideoConfig
    ) {
        texture.initialize()
        texture.initData(
            0,
            config.internalFormat,
            config.width,
            config.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
        framebuffer.initialize(texture)
    }

    override suspend fun onStart() {
        frameCount = 0
        running.set(1)

        startJob = scope.launch {
            if (!hasConnection()) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var copyMatrix = true
            val agentIn = channel(INPUT_AGENT)
            val envIn = channel(INPUT_ENV)

            if (linked(INPUT_AGENT) && !cycle(INPUT_AGENT)) running.incrementAndGet()
            if (linked(INPUT_ENV) && !cycle(INPUT_ENV)) running.incrementAndGet()

            execute()
            whileSelect {
                if (agentIn != null)
                agentIn.onReceive {
                    //                    Log.d(TAG, "agent receive")
                    agentEvent?.release()
                    agentEvent = it
                    debounceExecute()
                    if (it.eos) running.decrementAndGet()
                    running.get() > 0
                }
                if (envIn != null)
                envIn.onReceive {
                    //                    Log.d(TAG, "env receive")
                    envEvent?.release()
                    envEvent = it
                    if (copyMatrix) {
                        copyMatrix = false
                        val uniform = agentProgram.getUniform(Uniform.Type.Mat4, "texture_matrix")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    debounceExecute()
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

            agentEvent?.release()
            envEvent?.release()
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

    override suspend fun onStop() {
        val count = running.decrementAndGet()
        Log.d(TAG, "stop running $count")
        startJob?.join()
        val agentOut = channel(OUTPUT_AGENT)
        val envOut = channel(OUTPUT_ENV)

        agentOut?.receive()?.also {
            it.eos = true
            it.queue()
        }

        envOut?.receive()?.also {
            it.eos = true
            it.queue()
        }

        // if there is a cycle, i expect to receive an EOS from the cycle
        val agentIn = channel(INPUT_AGENT)
        val envIn = channel(INPUT_ENV)

        val cycleAgent = cycle(INPUT_AGENT)
        val cycleEnv = cycle(INPUT_ENV)
        if (cycleAgent) running.incrementAndGet()
        if (cycleEnv) running.incrementAndGet()

        if (cycleAgent || cycleEnv)
            whileSelect {
                if (cycleAgent && agentIn != null)
                    agentIn.onReceive {
                        it.release()
                        if (it.eos) running.decrementAndGet()
                        running.get() > 0
                    }
                if (cycleEnv && envIn != null)
                    envIn.onReceive {
                        //                    Log.d(TAG, "env receive")
                        it.release()
                        if (it.eos) running.decrementAndGet()
                        running.get() > 0
                    }
            }

        Log.d(TAG, "done stop running $count")
    }

    override suspend fun onRelease() {
        glesManager.glContext {
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
            envProgram.release()
        }
    }

    private suspend fun execute() {
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

        agentProgram.getUniform(Uniform.Type.Float, SENSOR_ANGLE).set(sensorAngle)
        agentProgram.getUniform(Uniform.Type.Float, SENSOR_DIST).set(sensorDistance)
        agentProgram.getUniform(Uniform.Type.Float, TRAVEL_ANGLE).set(travelAngle)
        agentProgram.getUniform(Uniform.Type.Float, TRAVEL_DIST).set(travelDistance)

        glesManager.glContext {
            GLES30.glUseProgram(agentProgram.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, agentFramebuffer.id)
            GLES30.glViewport(0, 0, agentSize.width, agentSize.height)
            agentInTexture.bind(GLES30.GL_TEXTURE0)
            envInTexture.bind(GLES30.GL_TEXTURE1)
            agentProgram.bindUniforms()
            quadMesh.execute()

            GLES30.glUseProgram(envProgram.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, envFramebuffer.id)
            GLES30.glViewport(0, 0, envSize.width, envSize.height)
            agentOutTexture.bind(GLES30.GL_TEXTURE0)
            envProgram.bindUniforms()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            agentMesh.execute()
        }

        frameCount++

        agentOutEvent?.let {
            it.texture = agentOutTexture
            it.count = frameCount
            it.eos = false
            it.queue()
        }

        envOutEvent?.let {
            it.texture = envOutTexture
            it.count = frameCount
            it.eos = false
            it.queue()
        }
    }

    private suspend fun debounceExecute() {
        if (!debounce.getAndSet(true)) {
            scope.launch {
                while (debounce.getAndSet(false)) {
                    val delay = max(
                        0,
                        frameDuration - (SystemClock.elapsedRealtime() - lastExecutionTime)
                    )
                    delay(delay)
                    lastExecutionTime = SystemClock.elapsedRealtime()
                    execute()
                }
            }
        }
    }

    companion object {
        const val TAG = "PhysarumNode"
        const val AGENT_TEXTURE_LOCATION = 0
        const val ENV_TEXTURE_LOCATION = 1
        const val SENSOR_ANGLE = "sensor_angle"
        const val SENSOR_DIST = "sensor_distance"
        const val TRAVEL_ANGLE = "travel_angle"
        const val TRAVEL_DIST = "travel_distance"
        val INPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("input_agent")
        val INPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("input_env")
        val OUTPUT_AGENT = Connection.Key<VideoConfig, VideoEvent>("output_agent")
        val OUTPUT_ENV = Connection.Key<VideoConfig, VideoEvent>("output_env")
    }
}