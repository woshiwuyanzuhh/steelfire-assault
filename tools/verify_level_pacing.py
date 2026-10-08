#!/usr/bin/env python3
"""Static checks for the authored first-level pacing/checkpoint slice.

This intentionally does not claim device playthrough coverage. It checks that
the data contract is present, ordered, and wired into the fixed-step GameView
update/retry paths.
"""
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/java/com/steelfire/assault/LevelPacing.kt"
GAME = ROOT / "app/src/main/java/com/steelfire/assault/GameView.kt"

catalog = CATALOG.read_text(encoding="utf-8")
game = GAME.read_text(encoding="utf-8")
failed = []

def check(label: str, ok: bool) -> None:
    if ok:
        print(f"PASS {label}")
    else:
        failed.append(label)
        print(f"FAIL {label}", file=sys.stderr)


check("level pacing data classes", all(x in catalog for x in (
    "data class EncounterBeat", "data class CheckpointSpec", "data class LevelPacingSpec")))
check("first level catalog", "level_01_rust_tide" in catalog and "LEVEL_01_RUST_TIDE" in catalog)
check("ordered authored triggers", bool(re.search(
    r"360f.*860f.*1450f.*2050f.*2800f.*3550f.*4300f", catalog, re.S)))
check("vehicle and boss beats", "vehicle = true" in catalog and "bossArena = true" in catalog)
check("two documented checkpoints", "lift_exit" in catalog and "elite_gate_exit" in catalog)
check("runtime catalog wiring", "levelPacing = LevelPacingCatalog.forLevel(index)" in game)
check("fixed-step authored trigger loop", "while (encounterIndex < authoredPacing.beats.size" in game)
check("deterministic authored spawn", "val x = 1320f + offset * 112f" in game and
      "val phase = encounterIndex * 0.75f + offset * 0.35f" in game)
check("checkpoint captures beat index", "checkpointEncounterIndex = encounterIndex" in game)
check("retry restores beat index", "encounterIndex = savedEncounterIndex" in game)
check("retry avoids cinematic", "mode = Mode.PLAYING" in game and "retryCheckpoint()" in game)
check("first-level vehicle wiring", "if (level != 1 && level != 2)" in game and
      "val vehicleBeat = levelPacing?.beats?.firstOrNull { it.vehicle }" in game)
check("authored boss trigger", "val bossTrigger = authoredPacing?.bossTriggerX ?: 4700f" in game)
check("result uses authored boss trigger", "progress >= (levelPacing?.bossTriggerX ?: 4700f)" in game)

if failed:
    raise SystemExit("Level pacing static verification failed")
print("Level pacing static verification passed; device route and frame-time evidence remain pending.")
