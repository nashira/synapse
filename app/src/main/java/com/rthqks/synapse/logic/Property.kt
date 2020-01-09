package com.rthqks.synapse.logic

import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class Properties {
    private val map = mutableMapOf<Property.Key<*>, Property<*>>()
    private val converters = mutableMapOf<Property.Key<*>, Converter<*>>()
    val size: Int = map.size
    val keys = map.keys
    val values = map.values

    fun <T> put(key: Property.Key<T>, property: Property<T>, converter: Converter<T>) {
        map[key] = property
        converters[key] = converter
    }

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

    @Suppress("UNCHECKED_CAST")
    operator fun set(keyName: String, value: String?) {
        value ?: return

//        val key = keys.first { it.name == keyName }
        val key = Property.Key<Unit>(keyName)
        val converter = converters[key]

        (map[key] as? Property<Any?>)?.let { property ->
            converter?.let {
                property.value = it.fromString(value)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> toString(key: Property.Key<T>): String? {
        return (converters[key] as? Converter<T>)?.toString(this[key])
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> find(key: Property.Key<T>): Property<T>? {
        return map[key] as? Property<T>
    }

    operator fun plus(other: Properties): Properties = Properties().also {
        it += this
        it += other
    }

    operator fun plusAssign(properties: Properties) {
        map.putAll(properties.map)
        converters.putAll(properties.converters)
    }

    fun copyTo(properties: Properties): Properties = properties.also {
        map.mapValuesTo(it.map) { entry ->
            entry.value.copy()
        }
        properties.converters.putAll(converters)
    }
}

interface Converter<T> {
    fun toString(value: T): String
    fun fromString(value: String): T
}

object IntConverter: Converter<Int> {
    override fun toString(value: Int): String = value.toString()
    override fun fromString(value: String): Int = value.toInt()
}

object SizeConverter: Converter<Size> {
    override fun toString(value: Size): String = "${value.width}x${value.height}"
    override fun fromString(value: String): Size = Size.parseSize(value)
}

object FloatConverter: Converter<Float> {
    override fun toString(value: Float): String  = value.toString()
    override fun fromString(value: String): Float  = value.toFloat()
}

object BooleanConverter: Converter<Boolean> {
    override fun toString(value: Boolean): String = value.toString()
    override fun fromString(value: String): Boolean = value.toBoolean()
}

data class Property<T>(
    val key: Key<T>,
    val type: Type<T>,
    var value: T,
    val requiresRestart: Boolean = false
) {
    data class Key<T>(val name: String)

    abstract class Type<T>(
        @StringRes val title: Int = 0,
        @DrawableRes val icon: Int = 0
    )

    companion object {
        fun RangeType(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            range: ClosedFloatingPointRange<Float>
        ): FloatRangeType = FloatRangeType(title, icon, range)

        fun RangeType(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            range: IntRange
        ): IntRangeType = IntRangeType(title, icon, range)
    }
}

class ChoiceType<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : Property.Type<T>(title, icon)

data class Choice<T>(val item: T, @StringRes val label: Int)

class FloatRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: ClosedFloatingPointRange<Float>
) : Property.Type<Float>(title, icon)

class IntRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: IntRange
) : Property.Type<Int>(title, icon)

class ToggleType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    @StringRes val enabled: Int,
    @StringRes val disabled: Int
) : Property.Type<Boolean>(title, icon)