package com.rthqks.synapse.logic

import android.net.Uri
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class PropertyType<T>(
    @StringRes val title: Int = 0,
    @DrawableRes val icon: Int = 0
) {
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
) : PropertyType<T>(title, icon)

data class Choice<T>(val item: T, @StringRes val label: Int)

class FloatRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: ClosedFloatingPointRange<Float>
) : PropertyType<Float>(title, icon)

class IntRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: IntRange
) : PropertyType<Int>(title, icon)

class ToggleType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    @StringRes val enabled: Int,
    @StringRes val disabled: Int
) : PropertyType<Boolean>(title, icon)

class UriType(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyType<Uri>(title, icon)

class TextType(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyType<String>(title, icon)

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

object UriConverter: Converter<Uri> {
    override fun toString(value: Uri): String = value.toString()
    override fun fromString(value: String): Uri = Uri.parse(value)
}

object TextConverter: Converter<String> {
    override fun toString(value: String): String = value
    override fun fromString(value: String): String  = value
}