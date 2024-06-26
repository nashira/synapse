package com.rthqks.flow.logic

class Node(
    val networkId: String,
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