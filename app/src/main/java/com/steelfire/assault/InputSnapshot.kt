package com.steelfire.assault

/**
 * Immutable input state consumed by the fixed-step simulation.
 * Pressed fields are edge-triggered and are cleared by [TouchInputMapper.consumeTransient].
 */
data class InputSnapshot(
    val moveAxis: Float = 0f,
    val jumpPressed: Boolean = false,
    val shootHeld: Boolean = false,
    val bombPressed: Boolean = false,
    val weaponNextPressed: Boolean = false,
    val crouchHeld: Boolean = false,
    val aimHeld: Boolean = false,
    val pausePressed: Boolean = false,
    val ownedPointerIds: Set<Int> = emptySet()
)
