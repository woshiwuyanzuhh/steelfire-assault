#!/usr/bin/env python3
"""Static acceptance checks for the combat vertical slice.

The verifier deliberately checks authored data and state transitions rather than
pretending that an APK build proves touch, audio, or device behavior.
"""
from pathlib import Path
import re
import sys


SOURCE = Path("app/src/main/java/com/steelfire/assault/GameView.kt")
text = SOURCE.read_text(encoding="utf-8")
checks = {
    "three authored weapon roles": ("持续压制", "近距爆发", "穿甲点杀"),
    "weapon feedback fields": ("reloadRisk", "projectileColor", "role"),
    "four enemy kinds": ("0 -> 56f", "1 -> 44f", "2 -> if", "else -> 25f"),
    "enemy telegraph reads": ("telegraph = when (enemy.kind)", "enemy.warning"),
    "vehicle lifecycle": ("enum class VehicleState", "VehicleState.ENTERING", "VehicleState.DAMAGED", "VehicleState.EXITING", "VehicleState.DESTROYED"),
    "vehicle damage and recovery": ("vehicleHp -=", "updateVehicleState(dt)"),
    "boss weak point": ("bossWeakPointOpen", "弱点窗口" if "弱点窗口" in text else "核心暴露"),
    "fixed-step grenade arc": ("var gravity: Float = 0f", "detonateGrenade", "grenade = true"),
    "muzzle anchored projectile": ("playerMuzzleAnchor()", "enemy.y - 82f", "val aimDy = if (aimHeld) -105f else 0f"),
}

bomb_start = text.find("if (bombQueued)")
bomb_end = text.find("var magneticCommand", bomb_start)
bomb_block = text[bomb_start:bomb_end] if bomb_start >= 0 and bomb_end > bomb_start else ""
if "grenade = true" not in bomb_block or "gravity = if" not in bomb_block or "enemies.forEach" in bomb_block:
    checks["bomb button launches before blast"] = ("__missing_ballistic_bomb_contract__",)
else:
    print("PASS bomb button launches before blast")

failed = []
for label, needles in checks.items():
    missing = [needle for needle in needles if needle not in text]
    if missing:
        failed.append(f"{label}: missing {missing}")
    else:
        print(f"PASS {label}")

# Ensure the four enemy authored kinds are represented by the spawn rotation.
if not re.search(r"val kind = \(spawnWave - 1\) % 4", text):
    failed.append("enemy spawn rotation: missing deterministic %4 authored rotation")
else:
    print("PASS deterministic enemy spawn rotation")

if failed:
    for item in failed:
        print(f"FAIL {item}", file=sys.stderr)
    raise SystemExit(1)

print("Combat slice static verification passed; device acceptance remains pending.")
