package com.steelfire.assault

/** Immutable state for the level-01 magnetic suspended-crate mechanic. */
data class MagneticBoxState(
    val id: String,
    val offset: Int,
    val lockHits: Int,
    val cooldownUntilTick: Long,
    val pressedDoor: Boolean
)

data class MagneticCoverState(
    val simulationTick: Long = 0L,
    val boxes: List<MagneticBoxState> = emptyList()
)

sealed interface MagneticCoverCommand {
    val lockId: String?

    data class ShootLock(override val lockId: String, val deltaSteps: Int = 1) : MagneticCoverCommand
    data object Noop : MagneticCoverCommand { override val lockId: String? = null }
}

sealed interface MagneticCoverEvent {
    data class CoverMoved(val boxId: String, val offset: Int) : MagneticCoverEvent
    data class DoorPressed(val boxId: String, val doorId: String) : MagneticCoverEvent
    data class LockRejected(val lockId: String, val reason: String) : MagneticCoverEvent
}

data class MagneticCoverTransition(
    val state: MagneticCoverState,
    val events: List<MagneticCoverEvent>
)

/**
 * Deterministic fixed-step reducer. The render thread must consume only the returned snapshot;
 * it must never call this reducer from onDraw.
 */
object MagneticCoverMechanic {
    const val ID = "magnetic_cover"
    const val FIXED_STEP_TICKS = 1L

    fun initialState(spec: MagneticCoverSpec): MagneticCoverState = MagneticCoverState(
        boxes = spec.boxes.map { box ->
            MagneticBoxState(box.id, box.minOffset, lockHits = 0, cooldownUntilTick = 0L, pressedDoor = false)
        }
    )

    fun step(
        previous: MagneticCoverState,
        spec: MagneticCoverSpec,
        command: MagneticCoverCommand
    ): MagneticCoverTransition {
        val nextTick = previous.simulationTick + FIXED_STEP_TICKS
        val events = mutableListOf<MagneticCoverEvent>()
        if (command is MagneticCoverCommand.Noop) {
            return MagneticCoverTransition(previous.copy(simulationTick = nextTick), emptyList())
        }
        val shoot = command as MagneticCoverCommand.ShootLock
        val boxSpec = spec.boxes.firstOrNull { it.lockId == shoot.lockId }
        if (boxSpec == null) {
            events += MagneticCoverEvent.LockRejected(shoot.lockId, "unknown_lock")
            return MagneticCoverTransition(previous.copy(simulationTick = nextTick), events)
        }
        val current = previous.boxes.firstOrNull { it.id == boxSpec.id }
        if (current == null) {
            events += MagneticCoverEvent.LockRejected(shoot.lockId, "missing_box_state")
            return MagneticCoverTransition(previous.copy(simulationTick = nextTick), events)
        }
        if (nextTick < current.cooldownUntilTick) {
            events += MagneticCoverEvent.LockRejected(shoot.lockId, "cooldown")
            return MagneticCoverTransition(previous.copy(simulationTick = nextTick), events)
        }
        val target = (current.offset + boxSpec.step * shoot.deltaSteps)
            .coerceIn(boxSpec.minOffset, boxSpec.maxOffset)
        if (target == current.offset) {
            events += MagneticCoverEvent.LockRejected(shoot.lockId, "at_limit")
            return MagneticCoverTransition(previous.copy(simulationTick = nextTick), events)
        }
        val pressedDoor = target == boxSpec.maxOffset && boxSpec.pressesDoorId != null
        val updated = current.copy(
            offset = target,
            lockHits = current.lockHits + 1,
            cooldownUntilTick = nextTick + spec.lockCooldownTicks,
            pressedDoor = pressedDoor || current.pressedDoor
        )
        val boxes = previous.boxes.map { if (it.id == updated.id) updated else it }
        events += MagneticCoverEvent.CoverMoved(updated.id, updated.offset)
        if (pressedDoor) events += MagneticCoverEvent.DoorPressed(updated.id, boxSpec.pressesDoorId!!)
        return MagneticCoverTransition(previous.copy(simulationTick = nextTick, boxes = boxes), events)
    }
}
