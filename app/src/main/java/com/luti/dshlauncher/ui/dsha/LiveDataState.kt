package com.luti.dshlauncher.ui.dsha

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * LiveData → Compose State 桥接，语义同官方 runtime-livedata 的 observeAsState，
 * 本地实现以免为几处 DSHA 引擎页面额外引入依赖。
 */
@Composable
fun <T> LiveData<T>.observeAsState(): State<T?> = observeAsState(value)

@Composable
fun <R, T : R> LiveData<T>.observeAsState(initial: R): State<R> {
    val owner = LocalLifecycleOwner.current
    val state = remember(this) {
        @Suppress("UNCHECKED_CAST")
        mutableStateOf(if (isInitialized) value as R else initial)
    }
    DisposableEffect(this, owner) {
        val observer = Observer<T> { state.value = it }
        observe(owner, observer)
        onDispose { removeObserver(observer) }
    }
    return state
}