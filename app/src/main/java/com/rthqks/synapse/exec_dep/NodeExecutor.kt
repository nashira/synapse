package com.rthqks.synapse.exec_dep

import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.Executor
import com.rthqks.synapse.exec_dep.link.Config
import com.rthqks.synapse.exec_dep.link.Connection
import com.rthqks.synapse.exec_dep.link.Event
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel

abstract class NodeExecutor(
    context: ExecutionContext
) : Executor(context) {
    abstract suspend fun onCreate()
    abstract suspend fun onInitialize()
    abstract suspend fun onStart()
    abstract suspend fun onStop()
    abstract suspend fun onRelease()

    suspend fun create() = async(this::onCreate)
    suspend fun inititalize() = async(this::onInitialize)
    suspend fun start() = async(this::onStart)
    suspend fun stop() = async(this::onStop)
    override suspend fun release() {
        exec { onRelease() }
        super.release()
    }

    private val connectionSet = mutableSetOf<Connection.Key<*, *>>()
    private val cycleSet = mutableSetOf<Connection.Key<*, *>>()
    private val connections = mutableMapOf<Connection.Key<*, *>, Connection<*, *>>()
    private val channels = mutableMapOf<Connection.Key<*, *>, ReceiveChannel<*>>()
    private val configs = mutableMapOf<Connection.Key<*, *>, Config>()
    private val waitingConfigs =
        mutableMapOf<Connection.Key<*, *>, MutableSet<CompletableDeferred<Config>>>()

    open suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        error("makeConfig not implemented")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <C : Config, E : Event> output(key: Connection.Key<C, E>): Connection<C, E> = await {
        when (val existing = connections[key] as Connection<C, E>?) {
            null -> {
                val config = getConfig(key)
                Connection<C, E>(config).also {
                    connections[key] = it
                    channels[key] = it.producer()
                }
            }
            else -> existing
        }
    }

    suspend fun <C : Config, E : Event> input(key: Connection.Key<C, E>, connection: Connection<C, E>) = await {
        connections[key] = connection
        channels[key] = connection.consumer()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <C : Config, E : Event> getConfig(key: Connection.Key<C, E>): C {
        return configs.getOrPut(key) {
            makeConfig(key)
        } as C
    }

    open suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        synchronized(configs) {
            configs[key] = config
            waitingConfigs.remove(key)?.forEach { it.complete(config) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> connection(key: Connection.Key<C, E>): Connection<C, E>? {
        return connections[key] as Connection<C, E>?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> channel(key: Connection.Key<C, E>): ReceiveChannel<E>? {
        return channels[key] as ReceiveChannel<E>?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> config(key: Connection.Key<C, E>): C? {
        return configs[key] as C?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> configAsync(key: Connection.Key<C, E>): Deferred<C> {
        synchronized(configs) {
            configs[key]?.let { return@configAsync CompletableDeferred(it as C) }

            val deferred = CompletableDeferred<C>()
            waitingConfigs.getOrPut(key) { mutableSetOf() }
                .add(deferred as CompletableDeferred<Config>)

            return deferred
        }
    }

    fun <C : Config, E : Event> setLinked(key: Connection.Key<C, E>) {
        connectionSet += key
    }

    fun <C : Config, E : Event> linked(key: Connection.Key<C, E>): Boolean = key in connectionSet

    fun <C : Config, E : Event> setCycle(key: Connection.Key<C, E>) {
        cycleSet += key
    }

    fun <C : Config, E : Event> cycle(key: Connection.Key<C, E>): Boolean = key in cycleSet

    companion object {
        const val TAG = "NodeExecutor"
    }
}
