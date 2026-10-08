# 工单 05 验收记录：角色交互纵向切片

日期：2026-10-03

## 已完成的实现

- 新增 `InputSnapshot` 只读快照，区分一次性按下事件和持续按住状态。
- 新增 `TouchInputMapper`，用 `pointerId -> Control` 所有权表处理移动、射击、蹲下、精瞄、跳跃、炸弹、换枪和暂停。释放一个指针不会清掉其他指针的动作。
- `GameView` 在固定 1/60 步长开始处消费快照；蹲下改变移动速度和角色碰撞高度，精瞄改变弹道并绘制准星反馈。
- 生命归零进入 `CHECKPOINT`；1650m/3300m 处保存位置和分数，重试时清理实体并从保存点重新建立波次。
- 新增 `tools/verify_gameplay_slice.ps1` 静态验收脚本。

## 证据与结果

| 检查 | 结果 | 证据 |
|---|---|---|
| Kotlin 编译 | 通过 | `powershell -ExecutionPolicy Bypass -File .\\build-apk.ps1`，Gradle `BUILD SUCCESSFUL` |
| APK 产物 | 通过 | `dist/steelfire-assault-debug.apk`，SHA-256 `4676c26d6cc8ca45a2e7f5424928dba074e26a9a121b8368f61879183dbc74d3` |
| mapper 静态约束 | 通过 | `tools/verify_gameplay_slice.ps1` 全部 PASS |
| 真机冷启动与关卡流程 | 待验收 | iQOO 13 当前未在本工单执行完整复测 |
| 多指快速按压 | 待验收 | 需要真机触控回放确认 |
| 低性能模式与后台恢复 | 待验收 | 需要真机验收清单 |

未完成的设备项保持未勾选，不能以编译结果替代真机验收。




