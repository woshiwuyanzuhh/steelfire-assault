# 08: UI, progression feedback, and local save vertical slice

**What to build:** Make the product navigable and legible: title/menu, cinematic skip, level entry, combat HUD, settings, pause, result, unlock state, and corrupted-save recovery.

**Blocked by:** 07: Level pacing and checkpoint vertical slice

**Status:** implementation-ready; static slice complete, device acceptance pending

**Audit evidence:** `.scratch/90s-blender-cg/audits/ui-save-slice.md` and `tools/verify_ui_save.py`. The static slice covers the seams; ticket remains open until the device checklist is exercised.

- [x] All menu and combat actions have a 1280x720 logical layout with letterboxed landscape mapping; minimum touch target and readability still require iQOO 13 evidence.
- [x] HUD communicates health, weapon/ammo, objective, telegraphs, boss phase, vehicle state, and result scoring in the existing render path; visual device review remains open.
- [x] `SaveProfile` version 1 validates bounds, migrates legacy keys, rebuilds a default on malformed JSON, and persists continue/checkpoint data. Activity and focus lifecycle freeze fixed-step updates, stop input ownership, and pause audio.
- [ ] Offline launch and replay work with no network request or uncaught exception (device/logcat evidence required).

## Implementation evidence (2026-10-03)

- `app/src/main/java/com/steelfire/assault/SaveProfile.kt` owns schema, range validation, legacy migration, and corrupt-save fallback.
- `GameView` renders a letterboxed 1280x720 canvas, converts touches through the same transform, exposes Continue, and persists checkpoint state before lifecycle pause.
- `MainActivity.onPause/onResume` and `GameView.onWindowFocusChanged` reset the fixed-step clock so background time is never replayed.
- `tools/verify_ui_save.py` passes the offline structural audit. See `.scratch/90s-blender-cg/audits/ui-save-slice.md` for the exact command and remaining device gates.
