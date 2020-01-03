package xyz.rthqks.synapse.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class Properties {
    private val map = mutableMapOf<Property.Key<*>, Property<*>>()
    val size: Int = map.size
    val keys = map.keys
    val values = map.values

    @Suppress("UNCHECKED_CAST")
    operator fun <T> set(key: Property.Key<T>, value: T) {
        (map[key] as? Property<T>)?.let {
            it.value = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Property.Key<T>): T {
        return map[key]?.value as T
    }

    fun <T> put(key: Property.Key<T>, property: Property<T>) {
        map[key] = property
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> find(key: Property.Key<T>): Property<T>? {
        return map[key] as? Property<T>
    }

    operator fun plus(other: Properties): Properties {
        return Properties().also {
            it.putAll(this)
            it.putAll(other)
        }
    }

    fun putAll(properties: Properties) {
        map.putAll(properties.map)
    }
}

class Property<T>(
    val type: Type<T>,
    var value: T
) {

    fun copy(value: T = this.value): Property<T> = Property(type, value)

    data class Key<T>(val name: String)

    sealed class Type<T>(
        @StringRes val title: Int = 0,
        @DrawableRes val icon: Int = 0
    ) {

        class ChoiceType<T>(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            vararg val choices: Choice<T>
        ): Type<T>(title, icon)

        class RangeType<T: Comparable<T>>(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            val range: ClosedRange<T>
        ): Type<T>(title, icon)
    }
}

class Choice<T>(item: T, @StringRes label: Int)