// ScreenMaskState.kt
package com.example.purramid.purramidscreenshade.screen_mask

data class ScreenMaskState(
    val instanceId: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = -1, // -1 for default/match_parent initially
    val height: Int = -1, // -1 for default/match_parent initially
    val isLocked: Boolean = false,
    val isLockedByLockAll: Boolean = false,
    val billboardImageUri: String? = null, // Store URI as String
    val isBillboardVisible: Boolean = false,
    val isControlsVisible: Boolean = true // To manage visibility of control buttons on the mask
)