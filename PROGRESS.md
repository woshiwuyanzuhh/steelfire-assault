# 当前交付进度

更新时间：2026-10-08

以可交付 APK 为 100% 的口径：

本轮自评完成度：**85%**（见 `MARKET_REVIEW.md`；设备回归完成后再更新）。

- 文档先行（PRD、技术设计、代理规则、素材清单）：100%
- CC0 素材检索、下载、SHA-256 校验与归档：100%
- 原创军事插画（港区、角色、敌人、载具/Boss）接入：100%
- 音频（原创枪声/环境底床 + CC0 命中/爆炸/UI/脚步 + Kenney 科幻音效 + 中文旁白）：100%
- 枪械、弹匣、换弹、敌人和 Boss 数值设计与实现：90%（Boss 已加入三阶段读数与攻击模式）
- Blender 次世代风格 CG 工程、三张运行时 CG 和 144 帧审核序列：100%
- Debug APK 构建、包体和资产路径静态校验：100%
- iQOO 13 真机重装与回归：待本轮设备重新授权（当前 adb 未发现设备；历史启动记录不作为本轮 Level-02 证据）

当前 APK：`dist/steelfire-assault-v0.1.1/steelfire-assault-v0.1.1-debug.apk`

当前 SHA-256：`1489117340f637b9aa752cf3bd795536d08e98a6e5404ef99a9a517150e90908`

本轮收尾修复了继续游戏的检查点位置持久化：存档现在同时恢复世界进度与玩家屏幕位置；构建脚本也会同步更新通用和 `v0.1.1` 版本目录 APK 及哈希文件。

设备未连接，以下命令用于测试机重新授权后的复现安装：

```powershell
.tools/android-sdk/platform-tools/adb.exe install -r dist/steelfire-assault-v0.1.1/steelfire-assault-v0.1.1-debug.apk
.tools/android-sdk/platform-tools/adb.exe shell monkey -p com.steelfire.assault 1
```

## 全产品重做状态（90 秒 Blender 方案）

- 工单系统：已切换为本地 Markdown，工单目录为 `.scratch/90s-blender-cg/issues/`。
- 01 产品基线：已完成，证据为 PRD/TECH_DESIGN/GAME_DESIGN、GLOSSARY 和验收矩阵。
- 02 Blender 视觉开发：进行中；第二轮 QA 已加入分层曲面、液压线、标识、景深和尘埃，等待完整工程复核。
- 03 90 秒镜头：进行中；第二轮 QA 已通过，2160 帧全量渲染已重新启动，尚未闭单。
- 04 自然配音：90 秒母带已 mux 到 MP4，cue 对齐与时长验证通过；手机/耳机主观听感复核待补。
- 05 人物交互：实现、固定步长反馈与静态构建完成，设备验收待 iQOO 13；当前整合 APK SHA-256 为 `b0525e2b7c6cdbc6173c9591951084d95ad1a9565ed182fbc98fd2027c2d1284`。
- 06 战斗切片：武器职责、四类敌人读招、载具生命周期和 Boss 弱点已实现并通过静态 verifier；设备验收待 iQOO 13。
- 07 关卡节奏：7 个固定 authored beats、载具/Boss 节点和 1650/3300 检查点已接入并通过静态 verifier；设备录像与帧率证据待补。
- 08 UI/存档：版本化 SaveProfile、损坏恢复、Continue/暂停/结果导航、生命周期暂停和等比安全区映射已实现并通过静态 verifier；本轮新增 VisualTokens 视觉精修、玻璃面板 HUD、渐变按钮、触控层级和环境扫描线；真机损坏存档/后台/断网证据待补。
- 09–10：等待前置工单通过，禁止把旧 APK 当作本次重做交付。

当前整合 APK SHA-256：`1489117340f637b9aa752cf3bd795536d08e98a6e5404ef99a9a517150e90908`（v0.1.1，旧 v0.1.0 保留为历史回归样本）。

90 秒 CG 已完成最终尾段返工：2160 帧连续、片名安全区通过、MP4 时长 `90.000s`，SHA-256 `5B8EDEABBB22AA4C3E661E2FB73724F3F418BA621EA4613F1C011081DF8A641B`。CG 工单 02/03 已闭单；工单 04 保留主观听感复核待办。

### 产品审计追加（2026-10-03）

`.scratch/90s-blender-cg/audits/product-audit.md` 已完成静态审计：旧 Android 原型可以构建，但人物动作、检查点、多指所有权、敌人/载具/Boss 状态机、首关 authored beats、安全区、后台暂停和损坏存档兜底均未达到工单门槛；ADB 当前无设备，因此没有真机通过项。该审计结论不会被旧 APK 或旧录屏覆盖。

本轮没有把旧的 10.8 秒 Canvas CG 或旧 APK 标为完成证据。

> 说明：文件顶部的 85% 是旧原型阶段的历史快照；90 秒重做交付以本节工单状态和 `docs/90S_ACCEPTANCE_MATRIX.md` 为唯一口径。

市场自评已追加 Iteration 2：当前 79/100。未连接 iQOO 13 和未完成手机/耳机主观听感，因此整体仍保持未完成状态。

可复核的阶段交付清单见 `docs/FINAL_DELIVERY_REPORT.md`；它列出 APK、90 秒 MP4、Blender 工程、哈希和剩余阻塞项。

## 50 关扩展与 UI 修复规划（2026-10-04）

本轮已按用户确认的产品范围更新 `PRD.md`、`TECH_DESIGN.md`、`GAME_DESIGN.md`：战役目标调整为 50 个可独立选择、重试和结算的关卡，第 5 的倍数关为 Boss；每关必须有新的可观察玩法规则和新的剧情 cue。UI 错位、安全区映射和右下角动作坞遮挡列为 P0，沿用固定步长、Canvas 渲染、本地资源和离线运行约束。

- 50 个独立关卡工单：`.scratch/50-level-expansion/issues/level-01-*.md` 至 `level-50-*.md`；50 份 JSON、50 份 manifest 和本地资源契约已落地并通过全量校验。
- 基线与 UI 工单：`program-00-baseline.md`、`ui-01-safe-area.md`、`ui-02-control-deck.md`、`ui-03-regression.md`。
- 工单总索引：`.scratch/50-level-expansion/ISSUE_INDEX.md`；素材来源、生成、哈希、许可证和 fallback 规则：`.scratch/50-level-expansion/ASSET_STRATEGY.md`。
- 静态规划检查：`python tools/verify_50_level_plan.py` 已通过 50 个工单和 10 个 Boss 槽位；`verify_50_level_assets.py` 已通过 50 关 JSON、manifest、哈希、离线资源和 Boss 契约。

### 第一批实现进展（2026-10-04）

- UI-01/UI-02 已落地：`ViewportTransform` 统一 WindowInsets、安全矩形、letterbox 绘制和触摸逆映射；`ControlLayout` 统一视觉矩形与命中矩形。右下角动作坞改为右侧窄轨两列三行，中心 70%×70% 观察窗保持无遮挡，命中区域仍满足最小尺寸。
- Level-01 数据契约和运行时演出已落地：`level_01.json`、本地 `manifest.json`、严格白名单解析器和固定步长 `MagneticCoverMechanic` 已通过独立校验；`GameView` 已接入磁锁射击、箱体移动、投射物遮挡、闸门事件、视觉提示和检查点偏移恢复。设备路线仍待重新连接测试机。
- Level-02 数据契约和运行时演出已接入：`level_02.json`、本地 `manifest.json`、严格白名单解析器和固定步长 `BridgeCollapseMechanic` 已通过独立校验；`GameView` 已接入桥段超载、裂纹预警、坍落、绞盘切断、临时桥板、检查点恢复和撤离结算。设备路线仍待重新连接测试机。
- Level-03–50 已接入通用离线运行时：JSON 派生 authored beats、检查点、章节机制阶段和五关 Boss/非 Boss 结算；关卡选择、解锁存档和 50 格选择界面已扩展到全战役。每个新机制 id 通过严格白名单和固定步长 `GenericMechanic` 阶段反馈。
- 本轮战斗表现修正已接入：`drawTerrain` 采用按关卡材质调色的复合地面层与角色/敌人/载具接触阴影；手榴弹按钮和 L-3 榴弹都通过固定步长重力形成可见抛物线，落地或命中后才结算爆炸；步行、蹲下和载具射击统一从 `playerMuzzleAnchor` 枪管端点生成。
- L03–L50 的四段机制 beats 已铺到完整世界宽度，第二检查点和终局线可达；`cycle_ticks`、`severity`、`visual_variant` 进入 `GenericMechanic`，每个 beat 触发交互和剧情节点反馈，Boss phase/weakpoint/warning 数据接入 Boss 显示。
- 新 debug 包版本为 `0.1.1`，成品目录：`dist/steelfire-assault-v0.1.1/`。本轮静态检查和构建通过；当前 ADB 未连接设备，实机截图回归待设备重新连接。










