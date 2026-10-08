# 钢火突袭：重做阶段交付报告

## 2026-10-08 收尾构建

- 修复继续游戏在进程重启后的检查点位置恢复，`SaveProfile` 现在保存并校验 `checkpointPlayerX`。
- `build-apk.ps1` 现在同时刷新 `dist/steelfire-assault-debug.apk` 和 `dist/steelfire-assault-v0.1.1/`，两份 SHA-256 均为 `1489117340f637b9aa752cf3bd795536d08e98a6e5404ef99a9a517150e90908`。
- 静态契约、50 关资产、节奏、UI/存档和 APK 构建已通过；ADB 当前仍无设备，真机路线保持待验收。

## 2026-10-04 增量构建

- APK：[dist/steelfire-assault-v0.1.1/steelfire-assault-v0.1.1-debug.apk](../dist/steelfire-assault-v0.1.1/steelfire-assault-v0.1.1-debug.apk)，SHA-256 `1489117340f637b9aa752cf3bd795536d08e98a6e5404ef99a9a517150e90908`。
- 本包包含 UI-01/UI-02 的安全区、右侧动作坞和几何命中修复，以及 Level-01/02 的专用机制和 Level-03–50 的 JSON/manifest、通用固定步长机制阶段、检查点、50 格关卡选择和五关 Boss/非 Boss 结算路径。
- 本轮 ADB 未发现设备，实机截图、多指和离线设备路线待重新连接测试机后补齐；50 关数据与运行时入口已落地，设备路线仍待验收。

更新时间：2026-10-08

## 已完成并可复核

- APK：[dist/steelfire-assault-debug.apk](../dist/steelfire-assault-debug.apk)，SHA-256 `1489117340f637b9aa752cf3bd795536d08e98a6e5404ef99a9a517150e90908`。
- 90 秒 CG：[dist/cg/steelfire_intro_90s.mp4](../dist/cg/steelfire_intro_90s.mp4)，H.264/AAC、960×540、24 fps、90.000 秒，SHA-256 `5B8EDEABBB22AA4C3E661E2FB73724F3F418BA621EA4613F1C011081DF8A641B`。
- Blender 工程：[art/blender/steelfire_cg_scene.blend](../art/blender/steelfire_cg_scene.blend) 与配音绑定副本 `steelfire_cg_scene_voiced.blend`。
- CG 审片：[dist/cg/qa2/final_contact_sheet.png](../dist/cg/qa2/final_contact_sheet.png)。末段标题已通过 16:9 安全区检查。
- 旁白母带：[app/src/main/assets/audio/cg/intro_voiceover_90s.wav](../app/src/main/assets/audio/cg/intro_voiceover_90s.wav)，90.0 秒 PCM，cue 与镜头切点对齐。

## 已执行验证

```text
python tools/blender/verify_cg_render.py                         PASS
powershell -ExecutionPolicy Bypass -File tools/verify_gameplay_slice.ps1 PASS
python tools/verify_combat_slice.py                              PASS
python tools/verify_level_pacing.py                              PASS
python tools/verify_ui_save.py                                   PASS
powershell -ExecutionPolicy Bypass -File tools/verify_magnetic_cover_mechanic.ps1 PASS (65 assertions)
python tools/verify_level_01_contract.py                         PASS
python tools/verify_level_02_contract.py                         PASS
python tools/verify_50_level_assets.py                           PASS (50 levels, full-route beats)
python tools/verify_campaign_runtime.py                         PASS
python tools/verify_combat_slice.py                             PASS (grenade arc, muzzle anchor, generic interactions)
powershell -ExecutionPolicy Bypass -File build-apk.ps1            BUILD SUCCESSFUL
```

本轮 UI 视觉精修已接入 `VisualTokens.kt`：菜单、关卡选择、设置、HUD、Boss 条、暂停/结算按钮和触控控件共享面板与语义色层级；新增环境扫描线、暗角、准星和检查点提示。Level-02 增加桥板裂纹、坍落、绞盘和临时桥板反馈；Level-03–50 的机制参数、四段路线、交互事件、剧情节点、章节色相和 Boss phase 数据均已接入运行时。战斗表现同步修正为复合地面与接触阴影、固定步长手榴弹抛物线，以及从枪管锚点生成的玩家子弹。静态检查与 APK 哈希已按上方命令重新验证。

CG 复核结果为 `frames=2160 movie_duration=90.000s`；Blender 工程重新打开检查通过，包含 413 个对象和 15 个材质。

## 尚未关闭

工单 05–09 的设备项仍未关闭。当前 `.tools/android-sdk/platform-tools/adb.exe devices -l` 没有列出设备，所以没有虚构 iQOO 13 冷启动、多指、Boss 完整击杀、后台返回、损坏存档、断网、低性能或 60 FPS 结果。工单 04 还需要手机扬声器与耳机的主观听感记录；工单 10 因此保持阻塞。

复测时以 `.scratch/90s-blender-cg/issues/` 工单和 `docs/90S_ACCEPTANCE_MATRIX.md` 为准，验证完成后再更新状态和 APK 哈希。









