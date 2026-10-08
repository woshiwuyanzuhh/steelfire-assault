# 工单 07：首关节奏与检查点静态审计

**审计时间**：2026-10-03  
**审计对象**：`LevelPacing.kt`、`GameView.kt`、`tools/verify_level_pacing.py`  
**边界**：只证明数据契约与固定步长接线；不把源码检查当作真机或完整路线验收。

## 已验证

- `LevelPacingCatalog.LEVEL_01_RUST_TIDE` 明确声明 7 个有序 beat：安全区教学、交叉火力、补给、载具突破、精英闸门、反应堆桥和 Boss 广场。
- beat 触发线为 360/860/1450/2050/2800/3550/4300m；敌人 kind、掩体、补给、载具和 Boss 标记都在不可变数据表中。
- `GameView.update` 在固定步长中按 `encounterIndex` 越过触发线逐次消费；首关敌人位置和 phase 由 beat/index 确定，不用随机数推进战斗逻辑。
- 检查点在 1650m 和 3300m 由数据表提交；快照保存进度、玩家位置、分数、已消费 beat 索引和旧波次索引。
- `retryCheckpoint` 先完整初始化，再恢复上述索引和 `Mode.PLAYING`；因此不会重播片头，已消费 beat 不会再次生成。
- 首关载具由 `vehicle=true` beat 接线到既有 `VehicleState` 生命周期；旧关卡仍保留兼容路径。

## 命令证据

```text
python tools/verify_level_pacing.py
PASS level pacing data classes
PASS first level catalog
PASS ordered authored triggers
PASS vehicle and boss beats
PASS two documented checkpoints
PASS runtime catalog wiring
PASS fixed-step authored trigger loop
PASS deterministic authored spawn
PASS checkpoint captures beat index
PASS retry restores beat index
PASS retry avoids cinematic
PASS first-level vehicle wiring
PASS authored boss trigger
Level pacing static verification passed; device route and frame-time evidence remain pending.

.tools/gradle-8.10.2/bin/gradle.bat :app:compileDebugKotlin --no-daemon
BUILD SUCCESSFUL
```

## 未关闭项

必须等工单 09 在 iQOO 13 上完成冷启动、完整首关路线、死亡到检查点重试、音频/帧率和离线验证，才能勾选体验与录屏条目。当前审计不声称关卡已经达到设备交付标准。
