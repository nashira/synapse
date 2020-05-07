package com.rthqks.synapse.ui

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class PropertyHolder<T>(
    @StringRes val title: Int,
    @DrawableRes val icon: Int
) {

}

data class Choice<T>(val item: T, @StringRes val label: Int, @DrawableRes val icon: Int)

class ValueHolder<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyHolder<T>(title, icon)

class ExpandedHolder<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : PropertyHolder<T>(title, icon)

class ToggleHolder<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : PropertyHolder<T>(title, icon)

class RadioHolder<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : PropertyHolder<T>(title, icon)

class FloatRangeHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: ClosedFloatingPointRange<Float>
) : PropertyHolder<Float>(title, icon)

class IntRangeHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: IntRange
) : PropertyHolder<Int>(title, icon)

class UriHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val mime: String
) : PropertyHolder<Uri>(title, icon)

class TextHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int
) : PropertyHolder<String>(title, icon)

fun RangeHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    range: ClosedFloatingPointRange<Float>
): FloatRangeHolder =
    FloatRangeHolder(title, icon, range)

fun RangeHolder(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    range: IntRange
): IntRangeHolder =
    IntRangeHolder(title, icon, range)