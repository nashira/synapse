package com.rthqks.synapse.logic

class Node(
    val networkId: Int,
    val id: Int,
    val type: String
) {
    val ports = mutableMapOf<String, Port>()
    val properties = mutableMapOf<String, Property>()

    override fun toString(): String {
        return "Node(type=${type}, id=$id, networkId=$networkId)"
    }

    companion object {
    }
}