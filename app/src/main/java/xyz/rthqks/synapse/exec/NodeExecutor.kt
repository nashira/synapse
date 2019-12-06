package xyz.rthqks.synapse.exec

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.exec.edge.*

abstract class NodeExecutor {
    abstract suspend fun create()
    abstract suspend fun initialize()
    abstract suspend fun start()
    abstract suspend fun stop()
    abstract suspend fun release()

    private val connections = mutableMapOf<Connection.Key<*, *>, Connection<*, *>>()
    private val channels = mutableMapOf<Connection.Key<*, *>, Channel<*>>()
    private val configs = mutableMapOf<Connection.Key<*, *>, Config>()
    private val connectMutex = Mutex()

    open suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        error("makeConfig not implemented")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <C : Config, E : Event> output(key: Connection.Key<C, E>): Connection<C, E> {
        connectMutex.withLock {
            val existing = connections[key] as Connection<C, E>?
            return when (existing) {
                null -> {
                    val config = getConfig(key)
                    SingleConsumer<C, E>(config).also {
                        connections[key] = it
                        channels[key] = it.producer()
                    }
                }
                is SingleConsumer -> MultiConsumer<C, E>(existing.config).also {
                    it.consumer(existing.duplex)
                    connections[key] = it
                    channels[key] = it.producer()
                }
                else -> existing
            }
        }
    }

    fun <C : Config, E : Event> input(key: Connection.Key<C, E>, connection: Connection<C, E>) {
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
        configs[key] = config
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> connection(key: Connection.Key<C, E>): Connection<C, E>? {
        return connections[key] as Connection<C, E>?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> channel(key: Connection.Key<C, E>): Channel<E>? {
        return channels[key] as Channel<E>?
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <C : Config, E : Event> config(key: Connection.Key<C, E>): C? {
        return configs[key] as C?
    }

    companion object {
        const val TAG = "NodeExecutor"
    }
}
