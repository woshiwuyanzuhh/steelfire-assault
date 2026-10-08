package com.steelfire.assault

/** Authored geometry and deterministic thresholds for the level-02 bridge rule. */
data class BridgeSegmentSpec(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val capacity: Int,
    val warningTicks: Long,
    val collapseTicks: Long,
    val winchId: String,
    val plankId: String?
)

data class BridgeCollapseSpec(
    override val id: String,
    override val version: Int,
    val warningTicks: Long,
    val collapseTicks: Long,
    val segments: List<BridgeSegmentSpec>
) : LevelMechanicSpec

data class BridgeSegmentState(
    val id: String,
    val occupants: Int = 0,
    val warningTicksRemaining: Long = 0L,
    val collapseTicksRemaining: Long = 0L,
    val collapsed: Boolean = false,
    val plankDeployed: Boolean = false,
    val edgeGrabbed: Boolean = false,
    val winchCut: Boolean = false
)

data class BridgeCollapseState(
    val simulationTick: Long = 0L,
    val segments: List<BridgeSegmentState> = emptyList()
)

sealed interface BridgeCollapseCommand {
    val segmentId: String?
    data class SetOccupancy(override val segmentId: String, val occupants: Int) : BridgeCollapseCommand
    data class DeployPlank(override val segmentId: String) : BridgeCollapseCommand
    data class GrabEdge(override val segmentId: String) : BridgeCollapseCommand
    data class ReleaseEdge(override val segmentId: String) : BridgeCollapseCommand
    data class CutWinch(override val segmentId: String) : BridgeCollapseCommand
    data object Noop : BridgeCollapseCommand { override val segmentId: String? = null }
}

sealed interface BridgeCollapseEvent {
    data class OverloadWarning(val segmentId: String, val occupants: Int, val capacity: Int, val ticks: Long) : BridgeCollapseEvent
    data class CollapseStarted(val segmentId: String, val ticks: Long) : BridgeCollapseEvent
    data class BridgeCollapsed(val segmentId: String) : BridgeCollapseEvent
    data class WarningCleared(val segmentId: String) : BridgeCollapseEvent
    data class PlankDeployed(val segmentId: String, val plankId: String) : BridgeCollapseEvent
    data class EdgeGrabbed(val segmentId: String) : BridgeCollapseEvent
    data class WinchCut(val segmentId: String, val winchId: String) : BridgeCollapseEvent
    data class CommandRejected(val segmentId: String, val reason: String) : BridgeCollapseEvent
}

data class BridgeCollapseTransition(val state: BridgeCollapseState, val events: List<BridgeCollapseEvent>)

/** Fixed-step reducer; renderer consumes only the returned immutable snapshot. */
object BridgeCollapseMechanic {
    const val ID = "bridge_collapse"
    const val FIXED_STEP_TICKS = 1L

    fun initialState(spec: BridgeCollapseSpec): BridgeCollapseState = BridgeCollapseState(
        segments = spec.segments.map { BridgeSegmentState(it.id) }
    )

    fun step(previous: BridgeCollapseState, spec: BridgeCollapseSpec, command: BridgeCollapseCommand): BridgeCollapseTransition {
        require(spec.id == ID) { "Unsupported bridge mechanic: ${spec.id}" }
        val nextTick = previous.simulationTick + FIXED_STEP_TICKS
        val events = mutableListOf<BridgeCollapseEvent>()
        val targetSpec = command.segmentId?.let { id -> spec.segments.firstOrNull { it.id == id } }
        val targetState = command.segmentId?.let { id -> previous.segments.firstOrNull { it.id == id } }
        if (command !is BridgeCollapseCommand.Noop && (targetSpec == null || targetState == null)) {
            return advance(previous.copy(simulationTick = nextTick), spec, mutableListOf(BridgeCollapseEvent.CommandRejected(command.segmentId ?: "", "unknown_segment")))
        }
        var states = previous.segments
        if (targetSpec != null && targetState != null) {
            var updated = targetState
            when (command) {
                is BridgeCollapseCommand.SetOccupancy -> updated = updated.copy(occupants = command.occupants.coerceAtLeast(0))
                is BridgeCollapseCommand.DeployPlank -> {
                    when {
                        targetSpec.plankId == null -> events += BridgeCollapseEvent.CommandRejected(command.segmentId, "no_plank")
                        !updated.collapsed -> events += BridgeCollapseEvent.CommandRejected(command.segmentId, "bridge_not_collapsed")
                        !updated.plankDeployed -> {
                            updated = updated.copy(plankDeployed = true)
                            events += BridgeCollapseEvent.PlankDeployed(command.segmentId, targetSpec.plankId)
                        }
                    }
                }
                is BridgeCollapseCommand.GrabEdge -> {
                    if (!updated.collapsed || updated.plankDeployed) events += BridgeCollapseEvent.CommandRejected(command.segmentId, "edge_unavailable")
                    else if (!updated.edgeGrabbed) {
                        updated = updated.copy(edgeGrabbed = true)
                        events += BridgeCollapseEvent.EdgeGrabbed(command.segmentId)
                    }
                }
                is BridgeCollapseCommand.ReleaseEdge -> updated = updated.copy(edgeGrabbed = false)
                is BridgeCollapseCommand.CutWinch -> if (!updated.winchCut) {
                    updated = updated.copy(winchCut = true)
                    events += BridgeCollapseEvent.WinchCut(command.segmentId, targetSpec.winchId)
                }
                BridgeCollapseCommand.Noop -> Unit
            }
            states = states.map { if (it.id == updated.id) updated else it }
        }
        return advance(previous.copy(simulationTick = nextTick, segments = states), spec, events)
    }

    private fun advance(previous: BridgeCollapseState, spec: BridgeCollapseSpec, events: MutableList<BridgeCollapseEvent>): BridgeCollapseTransition {
        val states = previous.segments.map { state ->
            val segment = spec.segments.firstOrNull { it.id == state.id } ?: return@map state
            if (state.collapsed || state.plankDeployed) return@map state
            val overloaded = state.occupants > segment.capacity
            var warning = state.warningTicksRemaining
            var countdown = state.collapseTicksRemaining
            if (countdown > 0L) {
                countdown -= FIXED_STEP_TICKS
                if (countdown == 0L) {
                    events += BridgeCollapseEvent.BridgeCollapsed(state.id)
                    return@map state.copy(warningTicksRemaining = 0L, collapseTicksRemaining = 0L, collapsed = true)
                }
                return@map state.copy(collapseTicksRemaining = countdown)
            }
            if (!overloaded) {
                if (warning > 0L) events += BridgeCollapseEvent.WarningCleared(state.id)
                return@map state.copy(warningTicksRemaining = 0L)
            }
            if (warning == 0L) {
                warning = segment.warningTicks.takeIf { it > 0L } ?: spec.warningTicks
                events += BridgeCollapseEvent.OverloadWarning(state.id, state.occupants, segment.capacity, warning)
            } else {
                warning -= FIXED_STEP_TICKS
                if (warning == 0L) {
                    countdown = segment.collapseTicks.takeIf { it > 0L } ?: spec.collapseTicks
                    events += BridgeCollapseEvent.CollapseStarted(state.id, countdown)
                }
            }
            state.copy(warningTicksRemaining = warning, collapseTicksRemaining = countdown)
        }
        return BridgeCollapseTransition(previous.copy(segments = states), events)
    }
}
