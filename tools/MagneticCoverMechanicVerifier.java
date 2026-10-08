import com.steelfire.assault.MagneticBoxSpec;
import com.steelfire.assault.MagneticBoxState;
import com.steelfire.assault.MagneticCoverCommand;
import com.steelfire.assault.MagneticCoverEvent;
import com.steelfire.assault.MagneticCoverMechanic;
import com.steelfire.assault.MagneticCoverSpec;
import com.steelfire.assault.MagneticCoverState;
import com.steelfire.assault.MagneticCoverTransition;

import java.util.Arrays;
import java.util.Collections;

/**
 * Small JVM harness for the pure reducer. It deliberately uses no test framework so it can run
 * from a freshly built Android project without adding a runtime dependency to the app.
 */
public final class MagneticCoverMechanicVerifier {
    private static int assertions;

    public static void main(String[] args) {
        MagneticBoxSpec boxA = new MagneticBoxSpec(
                "cargo_box_a", "mag_lock_a", 1460, 318, -96, 64, 32, "service_gate_a");
        MagneticBoxSpec boxB = new MagneticBoxSpec(
                "cargo_box_b", "mag_lock_b", 2080, 286, -64, 96, 32, "escape_gate");
        MagneticCoverSpec spec = new MagneticCoverSpec(
                "magnetic_cover", 1, 12L, Arrays.asList(boxA, boxB));

        MagneticCoverState initial = MagneticCoverMechanic.INSTANCE.initialState(spec);
        check(initial.getSimulationTick() == 0L, "initial tick is zero");
        check(initial.getBoxes().size() == 2, "all configured boxes have state");
        checkBox(initial.getBoxes().get(0), "cargo_box_a", -96, 0, 0L, false);
        checkBox(initial.getBoxes().get(1), "cargo_box_b", -64, 0, 0L, false);

        // No-op always advances exactly one fixed step and emits no event.
        MagneticCoverTransition noop = step(initial, MagneticCoverCommand.Noop.INSTANCE);
        check(noop.getState().getSimulationTick() == 1L, "noop advances one fixed tick");
        check(noop.getEvents().isEmpty(), "noop has no events");
        check(noop.getState().getBoxes().equals(initial.getBoxes()), "noop preserves boxes");

        // Unknown locks and valid locks with missing snapshots are rejected without mutation.
        MagneticCoverTransition unknown = step(initial, new MagneticCoverCommand.ShootLock("missing", 1));
        assertRejected(unknown, "missing", "unknown_lock", 1L);
        MagneticCoverState noBoxState = new MagneticCoverState(0L, Collections.emptyList());
        MagneticCoverTransition missingState = MagneticCoverMechanic.INSTANCE.step(
                noBoxState, spec, new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        assertRejected(missingState, "mag_lock_a", "missing_box_state", 1L);

        // A regular hit moves by one step, records a hit, and establishes the cooldown boundary.
        MagneticCoverTransition firstHit = step(initial, new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        check(firstHit.getState().getSimulationTick() == 1L, "hit advances one fixed tick");
        MagneticBoxState afterFirstHit = box(firstHit, "cargo_box_a");
        checkBox(afterFirstHit, "cargo_box_a", -64, 1, 13L, false);
        assertMoved(firstHit, "cargo_box_a", -64);

        // The cooldown is strict: tick 2 is rejected, while tick 13 is accepted.
        MagneticCoverTransition cooldown = step(firstHit.getState(), new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        assertRejected(cooldown, "mag_lock_a", "cooldown", 2L);
        MagneticCoverState atCooldownBoundary = cooldown.getState();
        for (int tick = 3; tick < 13; tick++) {
            atCooldownBoundary = step(atCooldownBoundary, MagneticCoverCommand.Noop.INSTANCE).getState();
        }
        check(atCooldownBoundary.getSimulationTick() == 12L, "cooldown setup reaches tick 12");
        MagneticCoverTransition boundaryHit = step(
                atCooldownBoundary, new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        check(boundaryHit.getState().getSimulationTick() == 13L, "cooldown boundary advances to tick 13");
        check(box(boundaryHit, "cargo_box_a").getOffset() == -32, "cooldown expires at the boundary");

        // A large delta clamps to max and presses the associated door exactly once.
        MagneticCoverState readyForMax = new MagneticCoverState(
                20L, Arrays.asList(
                        new MagneticBoxState("cargo_box_a", 32, 0, 0L, false),
                        new MagneticBoxState("cargo_box_b", -64, 0, 0L, false)));
        MagneticCoverTransition maxHit = MagneticCoverMechanic.INSTANCE.step(
                readyForMax, spec, new MagneticCoverCommand.ShootLock("mag_lock_a", 99));
        check(box(maxHit, "cargo_box_a").getOffset() == 64, "positive delta clamps to max offset");
        check(box(maxHit, "cargo_box_a").getLockHits() == 1, "clamped hit counts once");
        check(box(maxHit, "cargo_box_a").getPressedDoor(), "max offset marks door pressed");
        check(maxHit.getEvents().size() == 2, "max hit emits movement and door events");
        check(maxHit.getEvents().get(1) instanceof MagneticCoverEvent.DoorPressed,
                "second max-hit event is DoorPressed");
        MagneticCoverEvent.DoorPressed door = (MagneticCoverEvent.DoorPressed) maxHit.getEvents().get(1);
        check("service_gate_a".equals(door.getDoorId()), "door event carries configured door id");

        // Once at a limit, a shot is rejected and does not increment lockHits.
        MagneticCoverTransition atMax = MagneticCoverMechanic.INSTANCE.step(
                maxHit.getState(), spec, new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        assertRejected(atMax, "mag_lock_a", "cooldown", 22L);
        MagneticCoverState maxAfterCooldown = atMax.getState().copy(34L, atMax.getState().getBoxes());
        MagneticCoverTransition atLimit = MagneticCoverMechanic.INSTANCE.step(
                maxAfterCooldown, spec, new MagneticCoverCommand.ShootLock("mag_lock_a", 1));
        assertRejected(atLimit, "mag_lock_a", "at_limit", 35L);
        check(box(atLimit, "cargo_box_a").getLockHits() == box(maxHit, "cargo_box_a").getLockHits(),
                "at-limit shot does not increment hits");

        // Negative deltas clamp to min; the other box remains untouched.
        MagneticCoverState readyForMin = new MagneticCoverState(
                40L, Arrays.asList(
                        new MagneticBoxState("cargo_box_a", 0, 0, 0L, false),
                        new MagneticBoxState("cargo_box_b", 32, 4, 0L, true)));
        MagneticCoverTransition minHit = MagneticCoverMechanic.INSTANCE.step(
                readyForMin, spec, new MagneticCoverCommand.ShootLock("mag_lock_a", -99));
        check(box(minHit, "cargo_box_a").getOffset() == -96, "negative delta clamps to min offset");
        check(box(minHit, "cargo_box_b").getOffset() == 32, "other box offset is preserved");
        check(box(minHit, "cargo_box_b").getLockHits() == 4, "other box hit count is preserved");

        // A pressed door is sticky, and a later hit cannot clear it.
        MagneticCoverState pressed = new MagneticCoverState(
                60L, Arrays.asList(
                        new MagneticBoxState("cargo_box_a", 64, 2, 0L, true),
                        new MagneticBoxState("cargo_box_b", -64, 0, 0L, false)));
        MagneticCoverTransition moveAfterDoor = MagneticCoverMechanic.INSTANCE.step(
                pressed, spec, new MagneticCoverCommand.ShootLock("mag_lock_a", -1));
        check(!moveAfterDoor.getEvents().stream().anyMatch(event -> event instanceof MagneticCoverEvent.DoorPressed),
                "moving away from max does not repeat door event");
        check(box(moveAfterDoor, "cargo_box_a").getPressedDoor(), "pressed door flag remains sticky");

        System.out.println("MagneticCoverMechanicVerifier: PASS (" + assertions + " assertions)");
    }

    private static MagneticCoverTransition step(MagneticCoverState state, MagneticCoverCommand command) {
        MagneticBoxSpec box = new MagneticBoxSpec(
                "cargo_box_a", "mag_lock_a", 1460, 318, -96, 64, 32, "service_gate_a");
        MagneticBoxSpec other = new MagneticBoxSpec(
                "cargo_box_b", "mag_lock_b", 2080, 286, -64, 96, 32, "escape_gate");
        MagneticCoverSpec spec = new MagneticCoverSpec(
                "magnetic_cover", 1, 12L, Arrays.asList(box, other));
        return MagneticCoverMechanic.INSTANCE.step(state, spec, command);
    }

    private static MagneticBoxState box(MagneticCoverTransition transition, String id) {
        return box(transition.getState(), id);
    }

    private static MagneticBoxState box(MagneticCoverState state, String id) {
        for (MagneticBoxState box : state.getBoxes()) {
            if (id.equals(box.getId())) return box;
        }
        throw new AssertionError("missing box " + id);
    }

    private static void checkBox(
            MagneticBoxState box, String id, int offset, int lockHits, long cooldownUntil, boolean pressedDoor) {
        check(id.equals(box.getId()), id + " id");
        check(offset == box.getOffset(), id + " offset");
        check(lockHits == box.getLockHits(), id + " lock hits");
        check(cooldownUntil == box.getCooldownUntilTick(), id + " cooldown");
        check(pressedDoor == box.getPressedDoor(), id + " pressed-door flag");
    }

    private static void assertMoved(MagneticCoverTransition transition, String boxId, int offset) {
        check(transition.getEvents().size() == 1, "movement emits one event");
        check(transition.getEvents().get(0) instanceof MagneticCoverEvent.CoverMoved,
                "movement event is CoverMoved");
        MagneticCoverEvent.CoverMoved moved = (MagneticCoverEvent.CoverMoved) transition.getEvents().get(0);
        check(boxId.equals(moved.getBoxId()), "movement event carries box id");
        check(offset == moved.getOffset(), "movement event carries offset");
    }

    private static void assertRejected(
            MagneticCoverTransition transition, String lockId, String reason, long tick) {
        check(transition.getState().getSimulationTick() == tick, reason + " advances fixed tick");
        check(transition.getEvents().size() == 1, reason + " emits one event");
        check(transition.getEvents().get(0) instanceof MagneticCoverEvent.LockRejected,
                reason + " emits LockRejected");
        MagneticCoverEvent.LockRejected rejected = (MagneticCoverEvent.LockRejected) transition.getEvents().get(0);
        check(lockId.equals(rejected.getLockId()), reason + " carries lock id");
        check(reason.equals(rejected.getReason()), reason + " carries reason");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
