package xyz.rthqks.synapse.core.node

import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node

class LutNode : Node() {
    override suspend fun initialize() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun stop() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun release() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun output(key: String): Connection<*>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}