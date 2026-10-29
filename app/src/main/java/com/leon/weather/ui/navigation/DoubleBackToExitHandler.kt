package com.leon.weather.ui.navigation

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.leon.weather.R

internal class DoubleBackExitState(private val intervalMillis: Long = 2_000L) {
    private var previousPress: Long? = null

    fun shouldExit(nowMillis: Long): Boolean {
        val previous = previousPress
        if (previous != null && nowMillis - previous in 0..intervalMillis) {
            reset()
            return true
        }
        previousPress = nowMillis
        return false
    }

    fun reset() { previousPress = null }
}

@Composable
internal fun DoubleBackToExitHandler(enabled: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val message = stringResource(R.string.press_back_again_to_exit)
    val state = remember { DoubleBackExitState() }
    val toast = remember(context, message) { Toast.makeText(context, message, Toast.LENGTH_SHORT) }

    DisposableEffect(enabled, lifecycle, toast) {
        // A press on another screen or before backgrounding must never count toward exiting.
        state.reset()
        toast.cancel()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state.reset()
                toast.cancel()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.reset()
            toast.cancel()
        }
    }

    BackHandler(enabled = enabled) {
        if (state.shouldExit(SystemClock.elapsedRealtime())) {
            toast.cancel()
            onExit()
        } else {
            toast.show()
        }
    }
}
