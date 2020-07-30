package com.rthqks.flow.exec

import com.rthqks.flow.logic.Property

class Properties(
    private val map: Map<String, Property>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any> get(key: Property.Key<T>): T = map[key.name]?.value as T
}