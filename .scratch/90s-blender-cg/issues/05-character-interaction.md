# 05: Character interaction vertical slice

**What to build:** Make one original operator feel responsive on the Android device: locomotion, jump, crouch, aiming, firing, hit reaction, death, checkpoint recovery, animation feedback, and independent multitouch controls.

**Blocked by:** 01: Product baseline and acceptance protocol

**Status:** implementation-complete-device-pending

**Audit evidence:** `.scratch/90s-blender-cg/audits/gameplay-slice.md` — Kotlin implementation and static build checks are recorded; iQOO 13 replay remains open.

**Implementation evidence:** `app/src/main/java/com/steelfire/assault/InputSnapshot.kt`, `TouchInputMapper.kt`, `GameView.kt`; `tools/verify_gameplay_slice.ps1`; debug APK build `4676c26d6cc8ca45a2e7f5424928dba074e26a9a121b8368f61879183dbc74d3`.

- [ ] A fresh install can enter a playable room and complete movement, jump, crouch, aim, shoot, damage, death, and retry without a crash.
- [ ] A left-hand movement pointer and at least two right-hand action pointers work simultaneously; rapid press/release does not duplicate shots or strand controls.
- [x] Input, crouch/aim trajectory, checkpoint transition and retry are synchronized to fixed-step simulation rather than drawing side effects.
- [ ] Animation, muzzle flash, hit stop/flash, damage feedback, and camera response are synchronized to fixed-step simulation rather than drawing side effects.
- [ ] The same flow passes on the target iQOO 13 landscape safe area and in low-performance mode.




