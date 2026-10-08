package com.steelfire.assault

/** Data-only mechanic profile shared by authored rules that use the same fixed-step reducer shape. */
data class GenericMechanicSpec(
    override val id: String,
    override val version: Int,
    val params: Map<String, String>
) : LevelMechanicSpec

data class GenericMechanicProfile(
    val id: String,
    val label: String,
    val cycleTicks: Long,
    val severity: Int,
    val visualVariant: Int
)

data class GenericMechanicState(
    val simulationTick: Long = 0L,
    val phase: Int = 0,
    val interactions: Int = 0,
    val meter: Int = 0,
    val hazardActive: Boolean = false
)

sealed interface GenericMechanicCommand {
    data object Tick : GenericMechanicCommand
    data object Interact : GenericMechanicCommand
}

sealed interface GenericMechanicEvent {
    data class PhaseChanged(val phase: Int) : GenericMechanicEvent
    data class InteractionAccepted(val total: Int) : GenericMechanicEvent
    data class HazardChanged(val active: Boolean, val label: String) : GenericMechanicEvent
}

data class GenericMechanicTransition(
    val state: GenericMechanicState,
    val events: List<GenericMechanicEvent>
)

/** Fixed-step reducer that gives every authored rule a distinct, deterministic progression signal. */
object GenericMechanic {
    const val FIXED_STEP_TICKS = 1L

    fun initialState(): GenericMechanicState = GenericMechanicState()

    /** Resolves authored scalar parameters and a stable visual family from the mechanic id. */
    fun profile(spec: GenericMechanicSpec): GenericMechanicProfile {
        val id = spec.id
        val label = when {
            id.contains("thermal") || id.contains("overheat") || id.contains("molten") -> "热量窗口"
            id.contains("gravity") || id.contains("zero_g") || id.contains("lift") -> "重力窗口"
            id.contains("tide") || id.contains("current") || id.contains("deepwell") || id.contains("coolant") -> "流体窗口"
            id.contains("stealth") || id.contains("mirage") || id.contains("reflection") || id.contains("mimic") -> "识别窗口"
            id.contains("bridge") || id.contains("convoy") || id.contains("escort") || id.contains("civilian") -> "护送窗口"
            id.contains("signal") || id.contains("frequency") || id.contains("sonar") || id.contains("memory") -> "信号窗口"
            id.contains("magnetic") || id.contains("rail") || id.contains("rotary") || id.contains("counterweight") -> "结构窗口"
            id.contains("spore") || id.contains("vine") || id.contains("seed") -> "生物窗口"
            else -> "战术窗口"
        }
        val defaultCycle = 72L + (id.hashCode().toLong().absoluteValue % 90L)
        val cycle = spec.params["cycle_ticks"]?.toLongOrNull()?.coerceIn(36L, 240L) ?: defaultCycle
        val severity = spec.params["severity"]?.toIntOrNull()?.coerceIn(1, 3)
            ?: (id.hashCode().toUInt().toInt() and 0x7fffffff) % 3 + 1
        val visualVariant = spec.params["visual_variant"]?.toIntOrNull()?.coerceIn(0, 5)
            ?: (id.hashCode().toUInt().toInt() and 0x7fffffff) % 6
        return GenericMechanicProfile(id, label, cycle, severity, visualVariant)
    }

    fun step(
        previous: GenericMechanicState,
        command: GenericMechanicCommand,
        phase: Int
    ): GenericMechanicTransition = step(
        previous,
        GenericMechanicSpec("generic_rule", 1, emptyMap()),
        command,
        phase
    )

    fun step(
        previous: GenericMechanicState,
        spec: GenericMechanicSpec,
        command: GenericMechanicCommand,
        phase: Int
    ): GenericMechanicTransition {
        val profile = profile(spec)
        val nextTick = previous.simulationTick + FIXED_STEP_TICKS
        val cyclePosition = nextTick % profile.cycleTicks
        val meter = ((cyclePosition * 100L) / profile.cycleTicks).toInt()
        val hazardActive = cyclePosition >= profile.cycleTicks / 2L
        var next = previous.copy(simulationTick = nextTick, meter = meter, hazardActive = hazardActive)
        val events = mutableListOf<GenericMechanicEvent>()
        val clampedPhase = phase.coerceIn(0, 3)
        if (clampedPhase != previous.phase) {
            next = next.copy(phase = clampedPhase)
            events += GenericMechanicEvent.PhaseChanged(clampedPhase)
        }
        if (hazardActive != previous.hazardActive) {
            events += GenericMechanicEvent.HazardChanged(hazardActive, profile.label)
        }
        if (command is GenericMechanicCommand.Interact) {
            next = next.copy(interactions = previous.interactions + 1)
            events += GenericMechanicEvent.InteractionAccepted(next.interactions)
        }
        return GenericMechanicTransition(next, events)
    }
}

private val Long.absoluteValue: Long
    get() = if (this == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(this)
