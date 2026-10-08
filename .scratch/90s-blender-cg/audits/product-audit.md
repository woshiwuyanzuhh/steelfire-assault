# Android 产品审计：工单 05–09

**审计时间**：2026-10-03  
**审计对象**：当前 `app/src/main/java/com/steelfire/assault` 原型、APK 构建产物和已发布本地工单 05–09  
**方法**：代码与文档静态审计、离线构建冒烟；未连接真机，未把静态证据当作设备验收。

## 结论

当前产品仍是旧的单 `GameView` 原型，不能关闭工单 05–09。可以成功构建 debug APK，但关键纵向链路（蹲下/瞄准、检查点、真实载具状态、四类敌人、可学习 Boss 弱点、后台暂停、损坏存档降级、安全区）没有实现或没有验收证据。旧 APK 只能作为可安装原型，不能作为重做版交付证据。

## 证据摘要

| 检查项 | 结果 | 证据 |
|---|---|---|
| Debug APK 构建 | **PASS（仅构建）** | `powershell -ExecutionPolicy Bypass -File .\\build-apk.ps1` 返回 `BUILD SUCCESSFUL`；产物 `dist/steelfire-assault-debug.apk`，SHA-256 `f0a8303b86da12ecfd91735c6c7e2715442c8d6830647d424978162466d07fe9`。 |
| 真机/模拟器验收 | **BLOCKED** | `.tools/android-sdk/platform-tools/adb.exe devices -l` 只有表头，无设备；因此不能声称启动、帧率、音频焦点或多指通过。 |
| 固定步长 | **PARTIAL** | `GameView.kt:87-101` 有累加器和 `1/60` 步长，但没有“最多补算 4 帧”的上限；掉帧时可能追算过多。 |
| 绘制纯读状态 | **FAIL** | `onDraw` 的绘制路径会通过 `AssetRepository.bitmap()` 懒加载并写入缓存；同时整个运行态散落在 `GameView` 可变字段，没有设计文档要求的 `RenderSnapshot`。 |

## 工单 05：人物交互（未通过）

1. **动作不完整**：输入只分配移动、跳跃、射击三类 pointer；没有蹲下、瞄准、落地/快速落地或姿态状态。`GameView.kt:622-692` 的触控分支没有下蹲动作，`drawPlayer` 也只有固定站立图帧。
2. **死亡不支持检查点重试**：`update` 在 `GameView.kt:307` 直接切到 `RESULT`；结果按钮在 `GameView.kt:674-679` 调用 `startLevel(level)`，会从关卡入口重置，未保存/重建检查点，也没有“从检查点重试”层。
3. **多指控制存在所有权错误**：`shootHeld` 是全局布尔值；多个射击 pointer 同时存在时，任意一个 `ACTION_POINTER_UP` 都会把它清零（`handleUp`）。方向 pointer 也只保留单个 `moveAxis`，没有输入快照或动作所有权。
4. **反馈缺口**：没有伤害数字、命中停顿、屏幕轻震、相机跟随或受击动画。粒子在更新阶段生成，但不能替代工单要求的同步反馈。
5. **真机和低性能证据缺失**：没有可复核录像、日志、帧时间或 iQOO 13 安全区测试。

### 可实施补丁计划

- 先拆出 `InputSnapshot`/`TouchInputMapper`，每个动作记录 pointerId 集合和按下边沿；更新线程只消费快照。
- 新增 `PlayerState`（站立/蹲下/跳跃/受击/死亡/载具）和检查点快照；死亡进入独立 `CHECKPOINT` 状态，重建波次、武器和补给。
- 将命中反馈事件化（HitStop、DamageNumber、CameraShake、MuzzleFlash），由固定步长产生、渲染快照消费。
- 设备验证前不得勾选工单 05。

## 工单 06：武器、敌人、载具、Boss（未通过）

1. **武器与设计不一致**：代码里有 6 个 `WeaponSpec`（`GameView.kt:72-85`），但没有拾取、备用弹药、后坐力、榴弹爆炸半径或独立换枪反馈；弹匣耗尽后自动补满，不能体现补给/ reload 风险。规格中“线圈狙击”等额外武器也没有对应关卡来源。
2. **敌人只有两种行为**：`Enemy.kind` 只有 `0/1`，波次只在 `GameView.kt:168-171` 生成普通/重型两类；没有无人机和履带炮塔，未实现背部弱点或炮口读招。
3. **载具不是可进入状态机**：`inVehicle` 在 `GameView.kt:164-165` 由关卡编号和进度区间自动切换；没有进入、驾驶、受损、过热、下车、失败和恢复输入。车辆只在 level 2 绘制（`drawWorld:376`），与首关设计不一致。
4. **Boss 只有血量阈值换招**：`GameView.kt:259-285` 按血量选择弹道，任意命中区域都扣 Boss 血（`GameView.kt:224`）；没有阶段弱点窗口、核心/散热片判定、竞技场封锁、召唤无人机等可学习反制。虽有预警圆环，但没有独立强攻击事件和录制证据。

### 可实施补丁计划

- 以数据表定义 3 把首关武器、4 类敌人和载具状态，所有攻击事件包含 `telegraphStart`、`impact`、`counterplay`。
- 建立 `VehicleStateMachine` 与受损/过热/下车恢复路径，首关固定放置载具交互点。
- Boss 使用阶段脚本和弱点碰撞层；阶段切换冻结输入 100ms，记录警报、灯光、HUD 和音频事件。
- 对每种武器/敌人/Boss 行为写一段可录制的最短验证路径，再在设备上回归。

## 工单 07：关卡节奏与检查点（未通过）

1. 当前 `progress` 每帧增加固定速度（`GameView.kt:145-146`），世界只有约 5200 逻辑像素和按进度随机位置刷出的波次（`GameView.kt:167-171`）；没有设计文档中的 A/B/C/D authored 区段、掩体、高低路、补给、救援目标、精英和 Boss 广场。
2. 波次使用 `Random.nextFloat()`，且没有按检查点重建的 wave seed/状态；重试会重置整个关卡，不能验证“波次不重复、奖励不污染”。
3. Level 1 不包含载具段；载具条件硬编码为 level 2 进度区间，无法完成首关“教学→载具→Boss”路线。
4. 没有检查点数据字段或 checkpoint 运行态；没有完整 playthrough 的帧率和录像证据。

### 可实施补丁计划

- 把首关拆为固定 `LevelSpec`/encounter beat，触发线只消费一次并持久化 `encounterId`。
- 在 2400/7200 等节点保存 `CheckpointSnapshot`，重试时重建固定波次、补给和镜头位置。
- 首关把载具交互放入 authored beat，Boss 入口前明确补给与安全区。
- 录制从冷启动到结算、死亡到检查点重试两条路径，并附设备帧时间。

## 工单 08：UI、进度和本地存档（未通过）

1. **菜单不完整**：`drawMenu` 只有“开始突袭/关卡选择/设置”（`GameView.kt:341-348`），没有 PRD 要求的“继续”及无存档置灰态；`unlocked_level` 是唯一进度字段。
2. **安全区未实现**：`MainActivity` 只设置沉浸式 flag；`GameView.onDraw` 直接以 `width/W`、`height/H` 缩放，没有 WindowInsets 或安全矩形。控件按逻辑像素绘制，未证明 72dp 最小触控尺寸。
3. **HUD 信息不足**：有生命、武器/弹药、Boss 血条和车辆文字，但没有当前目标、掩体/补给状态、弱点窗口、伤害数字或可读的四类敌人预警。
4. **后台不会暂停模拟**：`onWindowFocusChanged` 只调用 `audio.setEnabled(hasWindowFocus)`（`GameView.kt:720-722`）；`tick` 在 `Mode.PLAYING` 仍继续调用 `update`，失焦期间会继续推进战斗。`MainActivity` 也没有 `onPause`/`onResume` 分发。
5. **损坏存档可能阻断启动**：`prefs.getInt`/`getBoolean` 在字段类型损坏时可能抛 `ClassCastException`，没有版本校验、异常兜底或重建；只保存 `unlocked_level` 和 `low_performance`。
6. **暂停层缺口**：手动暂停可切换，但未保存“暂停前模式/输入清理”快照；后台返回预期的暂停层未实现。
7. **离线静态可行，运行未证实**：代码无网络 API；但 `AudioController` 初始化直接 `context.assets.openFd`（`AudioController.kt:38-40`），资源缺失时会在启动抛异常，未做非阻塞降级。

### 可实施补丁计划

- 先实现 `SaveProfile` 版本化 JSON/校验和 `loadOrDefault`，任何字段异常只回退默认并重建。
- 在 `MainActivity` 统一派发生命周期和 WindowInsets；GameView 映射到安全矩形，保存失焦前状态并停止逻辑 tick。
- 增加“继续/检查点重试/返回菜单”按钮和可读目标、弱点、补给 HUD；动作按钮按 dp 计算并在超宽屏保持安全边距。
- 音频资源加载改为逐项 try/catch，缺失时记录并继续启动，视觉预警不依赖音频。

## 工单 09：设备、性能、离线验收（阻塞）

- 当前审计环境 `adb devices -l` 无设备，故冷启动、横屏安全区、音频焦点、后台、断网、损坏存档、多指和 Boss 路径均是 **未测**。
- 没有帧时间/内存采样、低性能对照或压力场景数据；`lowPerformance` 仅减少火花数量，未证明目标帧率，也没有“只改变表现层”的证据。
- 没有失败→修复→重跑链接；工单 09 不能关闭，也不能把已有截图当作新重做证据。

### 进入设备验收的前置条件

1. 工单 05–08 的代码链路先实现并通过本地脚本/单元测试。
2. 真机重新出现于 `.tools/android-sdk/platform-tools/adb.exe devices`，记录型号、Android 版本和横屏分辨率。
3. 按 `docs/90S_ACCEPTANCE_MATRIX.md` 逐项录屏/日志；每个失败关联修复提交后重跑。
4. 最后才更新工单状态；本报告不替代真机验收。

## 推荐执行顺序

1. **05 人物输入与检查点基础**：先解决蹲下、瞄准、多指所有权、死亡重试和后台暂停底座。
2. **06 战斗状态机**：再接武器/敌人/载具/Boss 数据与反馈事件。
3. **07 首关 authored beats**：把战斗能力放入固定节奏和 checkpoint。
4. **08 UI/存档/安全区**：对整条流程加导航、恢复和损坏降级。
5. **09 设备回归**：设备出现后按矩阵测量、修复、重跑。

## 不应继续沿用的原型假设

- “自动按进度进入载具”不能替代载具交互。
- “血量分段换弹道”不能替代 Boss 弱点和可学习阶段。
- “结果按钮重开关卡”不能替代检查点重试。
- “失焦只暂停音频”不能替代后台暂停逻辑。
- “能构建 APK”不能替代 iQOO 13 真机验收。
