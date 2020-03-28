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

class ValueType<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyType<T>(title, icon)

class ExpandedType<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : PropertyType<T>(title, icon)

class ToggleType<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : PropertyType<T>(title, icon)

data class Choice<T>(val item: T, @StringRes val label: Int, @DrawableRes val icon: Int)

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

//class ToggleType(
//    @StringRes title: Int,
//    @DrawableRes icon: Int,
//    @StringRes val enabled: Int,
//    @StringRes val disabled: Int
//) : PropertyType<Boolean>(title, icon)

class UriType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val mime: String
) : PropertyType<Uri>(title, icon)

class TextType(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyType<String>(title, icon)
