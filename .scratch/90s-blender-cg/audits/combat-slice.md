# 工单 06 战斗纵向切片审计

日期：2026-10-03  
范围：`app/src/main/java/com/steelfire/assault/GameView.kt` 与 `tools/verify_combat_slice.py`

## 已落地的可审计行为

| 目标 | 实现证据 | 验证边界 |
| --- | --- | --- |
| 三把主武器的职责差异 | `WeaponSpec` 记录 `role`、伤害、射速、弹匣、`reloadRisk`、投射物颜色；轨道卡宾/三连霰/电弧步枪分别对应持续压制、近距爆发、穿甲点杀 | 静态字段和编译已验证；多指切枪与实际反馈仍待真机 |
| 四类敌人读招 | `kind % 4` 稳定轮换步枪兵、掷弹兵、冲锋兵、哨戒兵；每类有独立 telegraph 时长、颜色、速度与攻击弹型 | 静态分支已验证；敌人读招是否清楚仍需设备试玩 |
| 载具生命周期 | `VehicleState` 覆盖 AVAILABLE → ENTERING → ACTIVE/DAMAGED → EXITING/DESTROYED；二关自动进场，跳跃键下车，受击扣 `vehicleHp`，失效后恢复为步行 | 状态转移和编译已验证；手感、受损动画、恢复体验仍待真机 |
| Boss 弱点阶段 | 三阶段各自改变攻击周期；攻击前短窗口打开 `bossWeakPointOpen`，只有窗口内的射击/炸弹造成 Boss 伤害，并绘制“核心暴露 · FIRE”提示 | 静态逻辑已验证；完整击杀路径、音画同步仍待真机 |

## 执行记录

```text
python tools/verify_combat_slice.py     PASS
gradle :app:assembleDebug               BUILD SUCCESSFUL
```

当前没有连接可用的 iQOO 13 运行会话，因此不把本工单标记为完成。最终验收仍需按 `AGENTS.md` 的冷启动、连续多指、载具、Boss、后台恢复、低性能与断网清单执行。
