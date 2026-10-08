# 钢火突袭（Steelfire Assault）技术设计

**版本**：0.3（50 关战役、UI 布局修复与素材包技术基线，2026-10-04）
**实现约束**：延续仓库现有 Kotlin + Android View/Canvas，不引入运行时引擎；50 个关卡以数据驱动并由独立工单交付，第 5 的倍数关为 Boss。本文覆盖已验证首关实现，并为后续实现和验收提供唯一技术基线。

## 1. 技术栈与交付边界

- 客户端：Kotlin 2.0.21，单 Activity，Android View + Canvas 自绘 2D 战斗。
- 输入：MotionEvent 多指触控；预留 KeyEvent 供手柄/键盘扩展。
- 音频：SoundPool 播放短音效；环境床作为循环音效加载。音频焦点丢失时暂停，恢复时遵守用户设置。
- 持久化：SharedPreferences 保存小型设置和成绩；静态关卡、参数和资源放入 assets/。
- 后端/数据库：无后端、无数据库（已确认）；运行时不访问网络。
- 渲染：逻辑画布 1280×720，等比映射到窗口，像素资源使用 nearest-neighbor；安全区由 WindowInsets 提供。
- 系统：最低 API 26，目标 API 35，默认横屏；目标设备为 iQOO 13，先以 arm64-v8a debug APK 验收。
- 构建：Gradle Wrapper、AGP 8.7.3、Kotlin 2.0.21、Gradle 8.10.2，锁定 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。当前工作环境曾检查到 JDK、SDK、adb 可能未安装，不能把本机缓存当作依赖；安装后再构建。
- 交付：构建脚本先生成 `dist/steelfire-assault-debug.apk`，再按版本复制到 `dist/steelfire-assault-v<version>/steelfire-assault-v<version>-debug.apk`，同目录保存 `SHA256SUMS.txt` 和 `INSTALL.txt`。release 签名不入仓库；当前增量包为 `v0.1.1`。

## 2. 四小时垂直切片计划（历史基线）

下表记录已验证 v0.1.0 首关的生产切片，不是 50 关的排期，也不限制第 17～21 节的数据与工单范围。

| 时间 | 产出 | 不做的事情 |
|---|---|---|
| 0:00–0:20 | 文档、素材清单、逻辑坐标和状态机边界 | 不扩展第二关 |
| 0:20–1:20 | 玩家移动/跳跃/射击、触控、安全区、固定步长 | 不做复杂装备系统 |
| 1:20–2:10 | 一关三段波次、四类敌人、检查点、HUD | 不做随机刷怪 |
| 2:10–2:50 | 载具片段、GR-4 三阶段 Boss、镜头和受击反馈 | 不做可自由驾驶地图 |
| 2:50–3:20 | 2D/3D 混合开场、音频层、暂停/存档 | 不做完整配音 |
| 3:20–3:50 | 统一美术替换、低性能模式、断网/损坏存档降级 | 不追求第二套皮肤 |
| 3:50–4:00 | 构建、哈希、冒烟验收、记录限制 | 不把未完成内容塞进 APK |

## 3. 项目结构与职责

    app/src/main/
    ├─ AndroidManifest.xml
    ├─ java/com/steelfire/assault/
    │  ├─ MainActivity.kt          # 横屏、WindowInsets、生命周期
    │  ├─ GameView.kt              # 循环、状态机、实体更新和 Canvas 绘制
    │  ├─ LevelDefinition.kt       # 关卡 JSON 严格解析与本地资源契约
    │  ├─ MagneticCoverMechanic.kt # level-01 固定步长磁吊箱体 reducer
    │  ├─ BridgeCollapseMechanic.kt # level-02 固定步长断桥承重 reducer
    │  ├─ ViewportTransform.kt     # WindowInsets 安全矩形与触摸逆映射
    │  ├─ ControlLayout.kt         # 动作坞视觉/命中几何
    │  ├─ CinematicRenderer.kt     # 固定步长三段开场 CG 时间线
    │  ├─ AssetRepository.kt       # 本地位图/音频读取与 fallback
    │  └─ AudioController.kt       # SoundPool、音量、音频焦点
    └─ assets/
       ├─ gfx/backgrounds/         # 低多边形港口与视差层
       ├─ gfx/characters/          # 岚、四类敌人、救援工程师
       ├─ gfx/vehicles/            # 犀虎机甲车、GR-4
        ├─ levels/                  # level_01..level_50 的配置与本地素材包
       ├─ cg/                      # 三张开场 CG 静帧
       ├─ audio/sfx/               # 事件短音效
       └─ audio/cg/                # 开场中文旁白

组织约定：

- GameStateMachine 只负责流程切换；WorldState 只保存可变运行状态。
- TouchInputMapper 将屏幕坐标映射为只读 InputSnapshot，不直接调用战斗逻辑。
- 更新线程按“输入 → AI → 运动 → 碰撞收集 → 事件结算 → 清理”顺序执行；onDraw 只消费 RenderSnapshot。
- 资源读取只经 AssetRepository，缺失位图使用规定的几何形状 fallback，缺失音效不阻断游戏。
- 关卡数据应能在常量表中描述波次、检查点、镜头边界和 Boss 阶段，不将脚本代码塞进 JSON。

## 4. 数据模型

### 4.1 持久化数据

| 键 | 类型 | 时机 | 降级 |
|---|---|---|---|
| save_version | Int | 每次写入 | 版本未知则迁移或默认；50 关扩展沿用当前 schema version 1 |
| unlocked_level / unlocked_levels | Int（旧）/ JSON 数组（新） | 首次通关 | 旧档迁移为 `{1}`，缺失默认为 `{1}` |
| best_scores | JSON 字符串（关卡到分数） | 结算 | 解析失败为空对象；未知关卡键丢弃 |
| settings_sound/music | Boolean | 设置变更 | 默认为 true |
| settings_low_performance | Boolean | 设置变更 | 默认为 false |
| control_scale | Float | 布局变更 | 截断到 0.8～1.4 |
| last_checkpoint | JSON 字符串或 null | 进入检查点/后台 | 校验 `level_id` 和版本，无效则回到该关卡入口 |

### 4.2 运行时状态

- Mode：BOOT、MENU、INTRO、PLAYING、PAUSED、CHECKPOINT、BOSS、RESULT。
- PlayerState：位置、速度、朝向、生命、无敌计时、当前武器、弹药、载具引用。
- WorldState：关卡进度、地形碰撞体、实体池、波次索引、检查点、镜头目标。
- EnemyState：类型、AI 子状态、生命、攻击冷却、预警计时、受击闪烁。
- ProjectileState：位置、速度、伤害、阵营、半径、寿命、碰撞层。
- EffectState：火花、烟尘、数字飘字、屏幕轻震、音效事件。
- InputSnapshot：每个 pointer 的坐标、按下/抬起/持续时长。
- RenderSnapshot：更新线程导出的只读快照，避免绘制时修改集合。

### 4.3 常量配置

- 物理：固定步长 1/60s、重力、最大速度、跳跃初速、摩擦。
- 武器：射速、伤害、弹速、散射、弹匣、备用弹药、后坐力。
- 敌人：生命、速度、攻击间隔、预警时间、掉落。
- 关卡：逻辑宽高、地形段、波次、检查点、Boss 阶段和镜头边界。
- 设备：安全区、最小触控尺寸、粒子池上限、低性能开关。

## 5. 关键技术方案

1. **固定步长**：累加器每次最多补算 4 个 1/60s；渲染帧率不改变战斗速度，落后过多时丢弃过期时间。
2. **生命周期**：onPause、onWindowFocusChanged(false) 和音频焦点丢失统一派发 PauseRequested；恢复时重置时间基准，不追补离开期间的逻辑。
3. **多指输入**：按 pointerId 建立方向、跳跃、射击、炸弹的归属；ACTION_POINTER_UP 只释放对应动作；越出按钮区域不偷换控制权。
4. **碰撞与事件**：碰撞阶段只收集事件，结算阶段按事件 ID 去重并扣血/销毁；实体清理延迟到帧末，禁止在 onDraw 删除对象。
5. **对象池**：子弹、粒子、伤害数字和敌人波次使用池，避免短生命周期对象造成 GC 抖动。
6. **镜头**：相机 X 追踪玩家并限制在 [levelStart, levelEnd - viewportWidth]；进入 Boss 区域时平滑减速，阶段转换使用 100～150ms 轻震。
7. **混合开场 CG**：低多边形港口背景作为预渲染位图或 Canvas 投影层，按深度做两组视差；2D 岚剪影、工程师、字幕和警报叠加。整个片头为时间轴事件，不启动战斗碰撞。
8. **音频**：首次用户手势后解锁 SoundPool；关键预警优先级高于脚步和环境床；音效总线分别受 sound/music/ambience 设置控制。
9. **资源与许可**：全部资源打包到 APK；每项在 ASSET_SOURCES.md 记录作者、来源、许可证和修改方式；构建前扫描缺失清单。
10. **存档容错**：JSON 字符串读取包裹异常处理，校验版本和数值范围；失败时只记录日志并返回默认 Profile。
11. **设备适配**：先将窗口映射到 1280×720，再把控件布局映射到安全矩形；高 DPI 不增加逻辑坐标，nearest-neighbor 保持像素边缘。
12. **降级策略**：低性能模式关闭远景动画、减少粒子池上限，不改变伤害、碰撞、Boss 阶段和音频预警。

## 6. 关卡数据契约

    LevelSpec(
      id = "level_01_rust_tide",
      width = 11520,
      checkpoints = [2400, 7200],
      waves = [
        {x: 960, type: "sentry", count: 3},
        {x: 2600, type: "armor_sentry", count: 2},
        {x: 4300, type: "drone", count: 4},
        {x: 6100, type: "vehicle_segment"},
        {x: 7800, type: "turret", count: 2}
      ],
      boss = "gr4_gatekeeper"
    )

字段只描述内容，不执行任意代码。x 为逻辑世界坐标；波次由镜头越过触发线触发一次，重试时从检查点重新建立固定波次。

## 7. 构建、APK 和验收

前置工具固定为 JDK 17、Android SDK Platform 35、Build Tools 35.0.0 与仓库内 Gradle Wrapper。当前机器若缺失其中任何一项，应先报告缺失，不修改 Kotlin 代码绕过构建。

    .\gradlew.bat assembleDebug
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item app\build\outputs\apk\debug\app-debug.apk dist\steelfire-assault-debug.apk
    (Get-FileHash dist\steelfire-assault-debug.apk -Algorithm SHA256).Hash |
      Set-Content -Encoding ascii dist\SHA256SUMS.txt

如有授权测试设备，可执行 adb install -r dist/steelfire-assault-debug.apk；交付不依赖设备连接。构建完成后检查 APK 存在、哈希文件与实际文件一致、安装后能离线启动。

## 8. 风险与预案

| 风险 | 影响 | 预案 |
|---|---|---|
| JDK/SDK/Gradle 缺失 | 无法确认 APK | 先交付源码和文档；按锁定版本安装后重跑构建 |
| 四小时范围膨胀 | 垂直切片不完整 | 只保留一关、四类敌人和一名 Boss；扩关列 P1 |
| Canvas 绘制复杂度过高 | 手感和性能下降 | 逻辑形状 fallback、对象池和固定波次优先 |
| 触控布局遮挡或误触 | 手机上无法通关 | 安全区映射、72dp 最小区域、控件缩放和实机验收 |
| 资源来源不清 | 法律/下架风险 | 构建前检查 ASSET_SOURCES.md，缺项即失败 |
| 音频焦点或格式异常 | 无预警或爆音 | 统一采样率/音量；视觉预警独立保底 |
| 存档损坏 | 进度丢失或无法启动 | 版本校验、默认 Profile、非阻塞重建 |
| 过多粒子导致掉帧 | 操作延迟 | 低性能模式、池上限和远景层降级 |

## 9. 已知限制（历史切片）

本方案不引入真正的运行时 3D 引擎；片头的“3D”是自制低多边形预渲染/Canvas 投影与 2D 覆盖层的混合。以下限制针对历史单关 APK，50 关扩展的技术约束以第 17～21 节为准。当前环境若没有 JDK 17、Android SDK 和可用 Gradle Wrapper，不能声称 APK 已验证；安装后必须重新执行第 7 节命令和 AGENTS.md 测试清单。


## 10. 已落地的 CG 资源与 Canvas 播放方案

本轮资源已经生成并放入项目：

- app/src/main/assets/cg/cg_01_hangar_3d.png：Blender 5.2.2 生成的低多边形机库透视背景。
- app/src/main/assets/cg/cg_02_operative_2d.png：原创 2D 岚与救援目标图层。
- app/src/main/assets/cg/cg_03_core_title.png：晶核标题、警报和进入战斗提示。
- app/src/main/assets/audio/cg/intro_voiceover.wav：开场旁白，可单独静音或跳过。
- art/blender/steelfire_cg_scene.blend：可复查的 Blender 工程。
- tools/blender/build_steelfire_cg.py：生成/更新 CG 的脚本。
- dist/cg/frames/frame_0001.png 至 frame_0144.png：144 帧审核序列，用于回归检查；`dist/cg/steelfire_intro_preview.gif` 是缩略预览，均不作为 APK 运行时输入。

APK 采用 Canvas 三段 CG，不封装视频播放器：INTRO 状态按时间轴依次绘制三张 PNG，并用 Canvas 完成淡入、平移、缩放、字幕和警报叠加。第一张提供 3D 透视感，第二张叠加 2D 角色，第三张落到标题和可操作提示。渲染帧率变化不会影响时间轴；时间轴使用单调的固定逻辑时间，并在后台暂停时冻结。

旁白在片头时间线开始后由 SoundPool 播放。用户点击跳过、返回键、系统切后台或关闭声音时立即停止旁白并释放流；跳过后直接转入 PLAYING，不能让旁白在战斗中继续。PNG 或 WAV 缺失时不阻断 INTRO：AssetRepository 使用几何标题/静音 fallback，并记录日志。

资源管线约定：

1. 修改 Blender 工程后运行 tools/blender/build_steelfire_cg.py，重新生成三张运行时 PNG 和审核帧。
2. 构建前验证三张 PNG 位于 app/src/main/assets/cg/，旁白位于 app/src/main/assets/audio/cg/。
3. dist/cg/frames 只用于人工/自动回归，不复制进 APK；其帧数、命名和连续性应写入构建日志。
4. ASSET_SOURCES.md 记录 Blender 版本、脚本、生成日期、许可证和每个资源的 SHA-256；任何来源缺失都阻止交付。


## 11. 重做技术基线：90 秒 Blender CG 与全产品工单

本节覆盖前文的 10.8 秒 Canvas 片头方案。最终 CG 仅由 Blender 工程生成；Canvas 片头保留为旧 APK 兼容路径，不能作为新 CG 交付证据。

- Blender 5.2.2 headless 生成可编辑 `.blend`，24 fps、2160 帧、90.0 秒；Eevee 用于可重复预览，材质、灯光、相机和动画均保留在工程内。
- 渲染阶段先输出带编号的 PNG 帧序列，再由本地 FFmpeg 编码 H.264/AAC；旁白和声场通过 Blender VSE/本地混音脚本绑定，检查起始、结束和 cue 偏移。
- Blender 场景不读取 APK PNG、商业游戏模型、远程贴图或运行时网络资源；旧 `app/src/main/assets/cg/` 只服务历史 APK。
- 90 秒音频母带由固定 cue 表生成，旁白和无线电为独立层，混音在 -1 dBFS 峰值以内；Android 运行时仍使用短版旁白，90 秒母带只属于 MP4/Blender 交付。
- 工单采用 `.scratch/90s-blender-cg/issues/` 的本地 Markdown 追踪。每张工单必须有一个可演示路径、阻塞关系和可复核证据；未通过不能闭单。

## 12. 工单 05 实现落地（2026-10-03）

- `InputSnapshot` 为固定步长消费的只读输入快照；跳跃、炸弹、换枪和暂停为一次性按下事件，移动、射击、蹲下和精瞄为持续状态。
- `TouchInputMapper` 按 Android `pointerId` 保留动作所有权。ACTION_POINTER_UP 只释放对应指针，移动指针不能偷换射击指针；取消或重开关卡会清空所有所有权。
- 横屏逻辑坐标仍为 1280×720。新增蹲下区（640..805×590..720）和精瞄区（1165..1275×545..720）；蹲下降低移动速度与角色碰撞高度，精瞄抬高弹道并显示准星反馈。
- 玩家生命归零后进入 CHECKPOINT 覆盖层，保留最近 1650m/3300m 检查点、位置和分数；“从检查点重试”重建波次并回到固定步长 PLAYING，不能直接跳过检查点状态。
- 设备验收仍需在 iQOO 13 上完成，编译成功不等同于工单闭单。

## 13. 工单 06 战斗切片技术基线（2026-10-03）

- `WeaponSpec` 是不可变调参表，至少记录职责、伤害、射速、弹匣、换弹风险和投射物颜色；运行时只按当前索引消费，不从绘制回调改写战斗状态。
- 敌人行为使用固定步长和四种稳定 kind 轮换；每类有独立预警时长、攻击弹型和移动速度。预警只读 `warning`，实际伤害在预警结束的更新步提交。
- 载具状态集中在 `VehicleState`，覆盖 AVAILABLE、ENTERING、ACTIVE、DAMAGED、EXITING、DESTROYED。二关由进度触发登车，跳跃边沿触发下车；受击扣 `vehicleHp`，失效后回到步行态。
- Boss 三阶段按生命阈值切换攻击周期。`bossWeakPointOpen` 由固定步长的攻击周期计算，弱点关闭时命中只反馈装甲火花，不扣 Boss 生命；弱点开启时显示“核心暴露 · FIRE”。
- `tools/verify_combat_slice.py` 只做静态结构检查，不能替代触控、多指、音频或 iQOO 13 真机验收。

## 14. 工单 07 首关 authored beat 实现约束

- `LevelPacing.kt` 是首关节奏的唯一数据源：`EncounterBeat` 描述触发线、敌人 kind、掩体、补给、载具和 Boss 广场；`CheckpointSpec` 描述可提交的恢复点。
- `GameView.update` 在固定步长内按触发线消费 beat；首关敌人生成使用 beat 中的固定 kind 和确定性位置/相位，不调用 `Random` 生成战斗波次。粒子随机只属于表现层，不影响关卡逻辑。
- 检查点提交保存 `encounterIndex` 与旧随机波次索引。重试执行完整初始化后恢复这些索引，因此已结算 beat 不会重新生成；未消费的 beat 仍按原触发线继续。
- 首关载具段由数据表的 `vehicle=true` beat 开启，保留 `VehicleState` 的进入、激活、受损、下车和摧毁状态；Level 2 的旧兼容路径继续使用原有区间。
- `tools/verify_level_pacing.py` 只检查静态契约与代码接线，不能替代 iQOO 13 的路线录屏、帧时间或多指验收。

## 15. 工单 08 UI、生命周期与存档切片

- `SaveProfileStore` 使用 Android `SharedPreferences` 保存 JSON schema version 1；字段经过范围裁剪，解析异常记录日志后返回默认 Profile 并用 `apply` 重建。
- `GameView` 从 Profile 初始化解锁状态和低性能设置，检查点提交时保存 Continue 所需的关卡、进度、分数和确定性波次索引；Boss 结算清除 Continue 标记。
- `MainActivity` 与 `GameView.onWindowFocusChanged` 统一调用 `pauseForLifecycle/resumeFromLifecycle`。暂停时丢弃累加器中过期时间并清空输入，恢复时重置单调时钟基线，不追补离开期间的逻辑。
- 绘制使用 `scale=min(viewWidth/1280, viewHeight/720)` 加居中偏移，触摸使用同一矩阵的逆变换；因此超宽横屏会出现安全黑边而不会拉伸交互坐标。
- `tools/verify_ui_save.py` 是离线结构检查；真机异常、音频焦点和无网络日志必须按 `.scratch/90s-blender-cg/audits/ui-save-slice.md` 逐项取证。

## 16. 本轮 UI 视觉精修实现约束（2026-10-03）

视觉精修保持现有 Kotlin + Canvas 管线，不增加引擎、布局框架或网络资源。`VisualTokens.kt` 是新增/调整 UI 语义色、圆角和描边 token 的唯一入口，按角色提供背景、面板、钢蓝、锈红、橙色高亮、沙金、警示、友军、文字和危险色，以及面板/控件圆角和描边宽度。PRD 第 4 节中的色板是产品契约；实现可以增加明暗层级，但不得改变语义颜色或在新 UI 代码中散落另一套语义色。

Canvas 层级固定为：背景与远景 → 场景实体 → HUD 面板 → 触控控件 → 模态层（暂停/检查点/结算）。各层只读取当前状态；视觉补间、脉冲和闪烁使用固定步长计时，不能在 `onDraw` 写回战斗状态。菜单与模态按钮沿用同一 `button` 视觉参数，禁用项需同时降低明度和对比度，危险操作需提供独立的危险色与文案。

视觉回归约束：

1. 以 1280×720 逻辑画布绘制，再做等比 letterbox；标题、按钮、HUD 和预警文字在安全矩形内完整显示，触摸仍使用同一矩阵逆映射。
2. 角色、敌人、载具和 Boss 的资产继续由 `AssetRepository` 读取并采用 nearest-neighbor 规则；缺失资产只能走既有几何 fallback，不以滤镜或远程素材补齐。
3. 低性能模式只关闭远景动画、降低粒子池和装饰性脉冲；敌人预警、Boss 阶段提示、命中闪烁、生命/弹药条和精瞄反馈必须保留。
4. 视觉验收需覆盖冷启动菜单、关卡选择、设置、暂停、检查点、结算以及一段含敌人预警和 Boss 阶段切换的战斗；仅通过静态编译不能关闭视觉工单。

地面采用 `GameView.drawTerrain` 的程序化复合层：接触高光边、倾斜面板、底层材质渐变、拼缝、警示切口和关卡色相共同构成脚下视觉；`drawGroundContactShadows` 在角色、敌人和载具下方绘制确定性的椭圆接触影，跳跃时按当前高度缩放。两者只读取 `progress`、实体坐标和低性能设置，不在 `onDraw` 修改战斗状态，也不引入远程或新增运行时纹理资源。
- 投射物由固定步长更新。`Bullet.gravity` 只对榴弹启用，手榴弹从角色投掷点以固定初速度进入抛物线，落地/命中后才通过 `detonateGrenade` 结算范围伤害和爆炸反馈；绘制层只显示弹体和速度方向尾迹。
- 玩家枪弹与枪口共用 `playerMuzzleAnchor`：站立、蹲下和载具分别使用姿态端点，枪口闪光复用同一锚点，避免弹道从身体中心或脚边出现。

## 17. 50 关战役数据与固定步长扩展（2026-10-04）

本节是旧 `level_01_rust_tide` 数据契约的向后兼容扩展。旧关卡配置仍可加载，但新内容必须遵循以下 schema；关卡工单不能把任意 Kotlin 逻辑塞进 JSON。

### 17.1 目录与命名

每关使用 `snake_case` 目录和独立素材包：

    app/src/main/assets/levels/
    ├─ level_01_rust_tide/level_01.json
    ├─ level_02_crane_yard/level_02.json
    ├─ ...
    └─ level_50_core_dawn/level_50.json

配置中的 `id`、目录名和工单 slug 必须一致。第 5 的倍数关的 `boss` 字段必须非空，其他关必须显式写 `boss: null`，避免遗漏 Boss 节点。

### 17.2 `LevelSpec` 最小字段

    {
      "id": "level_01_rust_tide",
      "index": 1,
      "act": 1,
      "title": "锈潮港外廊",
      "world_width": 11520,
      "story_cue": "radio_01_gate",
      "new_mechanic": {"id": "cover_height", "version": 1},
      "checkpoints": [{"x": 1650, "id": "lift_exit"}],
      "beats": [{
        "id": "l01_b01",
        "x": 360,
        "purpose": "teach_cover_height",
        "enemy_kinds": ["sentry"],
        "mechanic_params": {"cover_height": 96},
        "story_cue": "mya_signal_01",
        "asset_refs": ["levels/level_01/background_a"]
      }],
      "boss": null,
      "asset_manifest": "levels/level_01/manifest.json"
    }

解析器只接受白名单字段和 `mechanic.id` 枚举。`story_cue` 只引用本地字幕/音频表，不执行脚本；`asset_refs` 只能指向 APK 内的路径。每个 level 至少有一个 `new_mechanic`，Boss 关还必须声明 `boss.id`、阶段数组、弱点窗口和预警参数。

### 17.3 固定步长与玩法扩展

- 所有新玩法都实现为 `MechanicController` 的纯数据驱动状态机，在 1/60 秒更新中消费 `InputSnapshot`、计时器和事件；禁止以墙钟时间、线程睡眠或 `onDraw` 时间推进机制。
- 玩法触发由 `BeatEvent` 按世界坐标和确定性顺序提交。重试从检查点恢复 `beat_index`、`mechanic_state` 和 `story_cue_index`，已结算的剧情或奖励不得重复发放。
- L01 的 `magnetic_cover` 已接入 `GameView`：固定步长消费射击锁点事件，箱体位置由 reducer 快照驱动，敌方投射物与箱体发生遮挡碰撞；`SaveProfile` 额外保存受限的箱体偏移数组，进程重建后恢复到最近检查点。绘制层只读取 `MagneticCoverState`，不在 `onDraw` 修改状态。
- L02 的 `bridge_collapse` 已接入 `GameView`：桥段承重、裂纹预警、坍落、绞盘切断、临时桥板和撤离结算均由固定步长 reducer 事件驱动；`BridgeCollapseState` 在检查点快照中恢复，绘制层只读取桥段状态并保留低性能模式下的预警颜色。
- L03–L50 使用受白名单约束的 `GenericMechanicSpec`：每个关卡保留独立 `mechanic.id`、`cycle_ticks`、`severity`、`visual_variant`、四段 authored beats 和本地剧情 cue。`GenericMechanic` 在固定步长中消费这些参数，产生阶段、交互计数、周期 meter、危险/安全窗口和对应的 Canvas 视觉变体；每个 beat 触发一次交互事件，不能只靠换标题伪装成新关卡。
- L03–L50 的世界宽度、第二检查点和最终 Boss/撤离线从 JSON 派生，非 Boss 使用 `LevelPacingSpec.clearX`，Boss 在最终撤离线开启；四个 authored beats 分布在教学、组合、压力和终局路段，确保路线后半段可达。
- 玩法枚举初始包含 `cover_height`、`water_level`、`cable_cut`、`fog_beacon`、`rail_switch`、`wind_force`、`rotor_timing`、`filter_charge`、`gravity_flip`、`thermal_zone`、`mirror_beam`、`escort_route`、`minefield`、`sandstorm_window`、`zero_g_thrust`、`airlock_seal`、`drone_hijack`、`rotating_gravity`、`seismic_collapse`、`crystal_resonance`、`resource_triage`、`countdown_escape` 等；后续枚举必须先更新本文并补充离线静态校验。
- 每关同屏实体、投射物和粒子仍受对象池上限控制；加载下一关时释放上一关专属纹理和音频，不把 50 关资源一次性常驻内存。

### 17.4 Boss 节奏

Boss 关索引由 `index % 5 == 0` 计算并在加载时校验，不依赖 UI 文案。每个 Boss 至少有 3 个阶段；每阶段声明攻击组合、预警时长（不得小于 0.25 秒）、弱点窗口、阶段转换提示和结束事件。Boss 的生命、攻击周期、弱点和剧情触发均在固定步长中更新，RenderSnapshot 只读展示。

## 18. UI 错位与右下角操作区技术方案（P0）

### 18.1 单一布局快照

新增 `UiLayoutSnapshot`（或等价不可变结构）作为菜单、HUD、触控和模态层的唯一几何来源。它在窗口尺寸、WindowInsets、控件缩放或布局模式变化时重建，不在 `onDraw` 修改。快照至少包含 `safeRect`、`hudRects`、`actionDockRect`、每个按钮的 `drawRect`/`hitRect`、文字基线和 `combatObservationRect`。

逻辑坐标仍为 1280×720。窗口绘制变换为 `scale=min(availableWidth/1280, availableHeight/720)` 加 letterbox 偏移；触摸先减偏移再除以 scale，之后通过快照命中。每个 `hitRect` 由对应 `drawRect` 向外同心扩展，最大偏差 8 逻辑像素，并以 72dp 的设备最小面积为下限。

### 18.2 自适应动作坞

- `ActionDockLayout` 默认放在安全矩形右下角，边界不超过逻辑宽度的 34% 和高度的 42%；`combatObservationRect` 固定覆盖画布中部 70%×70%，按钮不得与其相交。
- 紧凑/标准两种布局由同一布局算法产出，按钮间距至少 12 逻辑像素。跳跃、射击、炸弹、换武器的图标、中文标签、冷却环和按下态描边使用 `VisualTokens.kt`，不散落颜色或圆角常量。
- 按钮空闲 alpha 为 0.42～0.58，按下 alpha 不高于 0.86；当预警、Boss 弱点、掉落物或剧情字幕进入动作坞区域时，降低装饰性阴影和填充，但不能关闭可操作层或视觉预警。
- PAUSED、CHECKPOINT、RESULT 模态层把动作坞标记为 disabled 并阻止命中；恢复时从同一快照重建，不能留下旧 pointerId 所有权。

### 18.3 UI 回归证据

每个 UI 工单至少保留三种宽高比（16:9、20:9、带挖孔横屏）的截图和触控坐标日志。离线校验应检查：所有绘制/命中矩形在 safeRect 内、偏差 ≤ 8 逻辑像素、动作坞与 `combatObservationRect` 不相交、中文文本测量后不溢出。静态脚本可以发现契约错误，但不能替代 iQOO 13 真机多指和可见性验收。

## 19. 素材包与离线加载管线

每个关卡目录必须包含 `manifest.json`，列出背景、碰撞可视化、玩法道具、敌人/Boss、粒子、音频和字幕的相对路径、字节数、SHA-256、作者、生成工具和许可证。`AssetRepository` 只接受 manifest 中的路径，构建前扫描路径存在性、哈希和许可证字段；任意一项缺失则失败。

素材优先由项目自制 Canvas 几何、Blender/Krita/程序生成图形提供；可再分发第三方素材需记录许可证和修改方式。运行时不访问网络、CDN、远程字体或热链图片，缺失可选素材只走既有几何/静音 fallback 并记录日志。每个工单需提供离线启动日志，证明断网仍能进入该关卡。

## 20. 存档迁移与性能约束

- `SaveProfileStore` 当前使用 schema version 1：`unlockedLevel` 表示连续解锁上限，`continueLevel`、检查点进度/位置、已消费 beat 索引和关卡机制快照共同恢复继续游戏；未知版本、损坏字段或越界值回退到默认档，不阻塞菜单。
- 每次关卡结算以原子方式写入解锁集合、最高分和剧情标记；未知关卡或越界值被丢弃并记录日志。存档不能用真实时间推进冷却或剧情。
- 关卡切换释放上一关资源，普通战斗维持原有 10 敌人/20 粒子目标；低性能模式只降低远景动画、粒子池和装饰脉冲，不改变玩法、Boss 阶段或字幕触发。

## 21. 关卡工单的技术完成定义

每个 `level_XX` 工单必须附带：`level_XX.json`、素材 manifest 与 `ASSET_SOURCES.md` 更新、固定步长录屏、剧情 cue 表、检查点重试证据、UI 三比例截图、断网日志、性能摘要和可安装 APK/哈希。第 5 的倍数关额外附 Boss 阶段/弱点/预警录屏。缺任一证据不得标记完成；50 个工单全部完成后才能把战役状态从“开发中”改为“可发布候选”。
