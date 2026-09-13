package com.example.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    data class ResourceString(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is ResourceString -> context.getString(resId, *args.toTypedArray())
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is ResourceString -> stringResource(resId, *args.toTypedArray())
    }
}
