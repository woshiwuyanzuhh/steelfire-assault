#!/usr/bin/env python3
"""Static contract checks for the 1..50 campaign runtime path."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
GAME = (ROOT / "app/src/main/java/com/steelfire/assault/GameView.kt").read_text(encoding="utf-8")
SAVE = (ROOT / "app/src/main/java/com/steelfire/assault/SaveProfile.kt").read_text(encoding="utf-8")
LEVEL = (ROOT / "app/src/main/java/com/steelfire/assault/LevelDefinition.kt").read_text(encoding="utf-8")
PACING = (ROOT / "app/src/main/java/com/steelfire/assault/LevelPacing.kt").read_text(encoding="utf-8")
MECHANIC = (ROOT / "app/src/main/java/com/steelfire/assault/GenericMechanic.kt").read_text(encoding="utf-8")
failed = []

def check(label, ok):
    print(f"{'PASS' if ok else 'FAIL'} {label}")
    if not ok:
        failed.append(label)

check("campaign loader range", "in 3..SaveProfileStore.MAX_LEVEL" in GAME and "LevelDefinitionLoader.load(context.assets, index)" in GAME)
check("campaign max level", "const val MAX_LEVEL = 50" in SAVE and "1..MAX_LEVEL" in SAVE)
check("fifty level selector", "for (index in 1..SaveProfileStore.MAX_LEVEL)" in GAME)
check("generic mechanic reducer", all(x in MECHANIC for x in ("GenericMechanicSpec", "GenericMechanicState", "FIXED_STEP_TICKS", "fun step")))
check("generic mechanic runtime", "GenericMechanic.step" in GAME and "genericState" in GAME and "GenericMechanic.profile" in GAME)
check("generic interaction route", "GenericMechanicCommand.Interact" in GAME and "genericState.interactions < encounterIndex" in GAME)
check("generic hazard feedback", "HazardChanged" in MECHANIC and "hazardActive" in GAME and "drawGenericMechanicOverlay" in GAME)
check("definition pacing bridge", "LevelPacingCatalog.fromDefinition" in GAME and "fun fromDefinition" in PACING)
check("boss cadence", "level % 5 == 0" in GAME and "definition.index % 5 == 0" in PACING)
check("non-boss extraction", "!isBossLevel() && mode == Mode.PLAYING" in GAME)
check("authored clear route", "levelPacing?.clearX" in GAME and "val clearX = (width - 900f)" in PACING)
check("strict mechanic whitelist", "GENERIC_MECHANIC_IDS" in LEVEL and "unsupported generic mechanic id" in LEVEL)
check("local asset fallback", "assetManager.open(\"levels/level_%02d.json\".format(index))" in LEVEL)
check("invalid authored data is not generic fallback", "catch (_: java.io.FileNotFoundException)" in LEVEL and "catch (_: Exception)" in LEVEL and "return null" in LEVEL)

if failed:
    raise SystemExit("Campaign runtime verification failed")
print("Campaign runtime contract verification passed; device route remains pending.")
