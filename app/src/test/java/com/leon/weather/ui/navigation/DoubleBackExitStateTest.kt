package com.leon.weather.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackExitStateTest {
    @Test fun `first press prompts and a second within two seconds exits once`() {
        val state = DoubleBackExitState()
        assertFalse(state.shouldExit(0))
        assertTrue(state.shouldExit(2_000))
        assertFalse(state.shouldExit(2_100))
    }

    @Test fun `expired confirmation starts a new window instead of exiting`() {
        val state = DoubleBackExitState()
        assertFalse(state.shouldExit(1_000))
        assertFalse(state.shouldExit(3_001))
        assertTrue(state.shouldExit(4_000))
    }

    @Test fun `reset prevents an earlier press from causing an accidental exit`() {
        val state = DoubleBackExitState()
        assertFalse(state.shouldExit(1_000))
        state.reset()
        assertFalse(state.shouldExit(1_100))
        assertTrue(state.shouldExit(1_200))
    }
}
