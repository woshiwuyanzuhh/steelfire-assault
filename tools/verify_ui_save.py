"""Static audit for the UI/save vertical slice.

This check is intentionally offline and deterministic. It proves that the defensive
save contract and safe-area/lifecycle seams are present; device interaction remains
an explicit acceptance item in the Markdown ticket.
"""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/steelfire/assault/GameView.kt"
SAVE_PROFILE = ROOT / "app/src/main/java/com/steelfire/assault/SaveProfile.kt"
ACTIVITY = ROOT / "app/src/main/java/com/steelfire/assault/MainActivity.kt"
CONTROL_LAYOUT = ROOT / "app/src/main/java/com/steelfire/assault/ControlLayout.kt"


def require(path: Path, pattern: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if not re.search(pattern, text, flags=re.MULTILINE | re.DOTALL):
        raise SystemExit(f"FAIL {label}: {path}")
    print(f"PASS {label}")


require(SAVE_PROFILE, r"data class SaveProfile\(", "versioned SaveProfile")
require(SAVE_PROFILE, r"fun loadOrDefault\(prefs: SharedPreferences\)", "defensive loadOrDefault")
require(SAVE_PROFILE, r"catch \(error: Exception\).*?save\(prefs, fallback\)", "corrupt-save fallback and rebuild")
require(SAVE_PROFILE, r"schemaVersion.*?CURRENT_VERSION", "schema version validation")
require(SAVE_PROFILE, r"checkpointPlayerX.*?checkpointPlayerX", "checkpoint position persistence")
require(GAME_VIEW, r"SaveProfileStore\.loadOrDefault\(prefs\)", "GameView loads profile")
require(GAME_VIEW, r"fun continueFromSave\(\).*?Mode\.PLAYING", "continue navigation")
require(GAME_VIEW, r"playerX = saved\.checkpointPlayerX", "continue restores checkpoint position")
require(GAME_VIEW, r"playerX = saved\.checkpointPlayerX.*?persistContinueState\(\)", "continue rewrites restored snapshot")
require(GAME_VIEW, r"fun pauseForLifecycle\(\).*?mode = Mode\.PAUSED", "lifecycle pause state")
require(GAME_VIEW, r"val scale = viewport\.scale.*?canvas\.translate\(offsetX, offsetY\)", "inset-aware safe-area transform")
require(GAME_VIEW, r"fun logicalPoint\(index: Int\): Pair<Float, Float>\?.*?viewport\.toLogical", "inverse touch safe-area mapping")
require(GAME_VIEW, r"fun setWindowInsets\(insets: ViewportTransform\.Insets\)", "window inset propagation")
require(ACTIVITY, r"setOnApplyWindowInsetsListener.*?setWindowInsets", "Activity applies window insets")
require(GAME_VIEW, r"Mode\.PAUSED.*?继续.*?退出关卡", "pause navigation labels")
require(GAME_VIEW, r"Mode\.RESULT.*?进入下一关.*?返回关卡选择", "result navigation labels")
require(ACTIVITY, r"override fun onPause\(\).*?pauseForLifecycle", "Activity forwards onPause")
require(ACTIVITY, r"override fun onResume\(\).*?resumeFromLifecycle", "Activity forwards onResume")

# UI-02 geometry contract: the fixed 70% x 70% combat observation window is
# x=192..1088, y=108..612 in logical coordinates. Parse the shared layout
# source so the check follows the actual hit rectangles instead of a duplicate
# set of coordinates in this verifier.
layout_text = CONTROL_LAYOUT.read_text(encoding="utf-8")
rects = {
    name: tuple(float(v) for v in values)
    for name, *values in re.findall(
        r"val\s+(\w+)\s*=\s*RectF\(([0-9.]+)f,\s*([0-9.]+)f,\s*([0-9.]+)f,\s*([0-9.]+)f\)",
        layout_text,
    )
}
observation = (192.0, 108.0, 1088.0, 612.0)
for name in ("moveHit", "crouchHit", "jumpHit", "shootHit", "aimHit", "bombHit", "weaponHit"):
    if name not in rects:
        raise SystemExit(f"FAIL missing shared control rect: {name}")
    left, top, right, bottom = rects[name]
    if right - left < 72.0 or bottom - top < 72.0:
        raise SystemExit(f"FAIL control hit target below 72 logical px: {name}")
    if not (right <= observation[0] or left >= observation[2] or bottom <= observation[1] or top >= observation[3]):
        raise SystemExit(f"FAIL control intersects combat observation window: {name}")
    print(f"PASS {name} hit target and observation clearance")

if not re.search(r"RectF\(1090f, 460f, 1279f, 719f\)", GAME_VIEW.read_text(encoding="utf-8")):
    raise SystemExit("FAIL compact action dock geometry")
print("PASS compact action dock geometry")

print("UI_SAVE_SLICE_STATIC_AUDIT=PASS")
