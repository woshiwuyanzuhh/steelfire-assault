# Level 01：锈港起航

**章节：** 锈港余波
**工单类型：** 单关卡可演示纵向切片
**目标与可演示边界：** 将“锈港外环”做成一个可从关卡选择页进入、可在单次运行中完成的横版动作射击纵向切片。该切片必须使用固定步长、状态快照/事件传递和本地 AssetRepository，并遵守安全区与右下角控制台的统一契约。

**Blocked by：** None (requires program-00-baseline, ui-01-safe-area, and ui-02-control-deck to be complete)

**状态：** in_progress（JSON/manifest、固定步长 reducer、运行时磁锁/箱体/闸门演出和检查点偏移恢复已完成；设备验收待继续）

## 新玩法契约

- 核心新玩法：磁吊箱体：玩家可射击磁锁，让悬吊货箱改变掩体位置或压住装甲门。
- 玩家动作必须覆盖移动、跳跃、下蹲、瞄准、射击、换武器、投掷；新玩法只扩展一个可验证的规则，不改变基础输入映射。
- 关卡入口、检查点和结算点都要在横屏安全区内可触达；右下角操作按钮不能遮挡关键机关、敌人预警或 Boss 弱点。
- 新玩法失败后可从最近检查点重试，不能因实体删除、镜头边界或错误的状态回调造成软锁。

## LevelSpec / manifest 契约

关卡配置写入 `app/src/main/assets/levels/level_01.json`，资源清单写入 `levels/level_01/manifest.json`；`story_cue` 只引用本地字幕/音频表，`asset_refs` 只能指向 APK 内路径。`new_mechanic.id` 需要在 `MechanicController` 白名单和离线静态校验中登记。

```json
{
  "id": "level_01_rust_harbor_departure",
  "index": 1,
  "act": 1,
  "title": "锈港起航",
  "world_width": 11520,
  "story_cue": "story_l01_intro",
  "new_mechanic": {"id": "magnetic_cover", "version": 1},
  "checkpoints": [{"x": 2400, "id": "l01_checkpoint"}, {"x": 7200, "id": "l01_clear"}],
  "beats": [{
    "id": "l01_b01",
    "x": 360,
    "purpose": "teach_magnetic_cover",
    "enemy_kinds": ["regular", "elite"],
    "mechanic_params": {"id": "magnetic_cover"},
    "story_cue": "story_l01_mid",
    "asset_refs": ["levels/level_01/background_a", "levels/level_01/mechanic"]
  }],
  "boss": null,
  "asset_manifest": "levels/level_01/manifest.json"
}
```

解析器只接受白名单字段；重试恢复 `beat_index`、`mechanic_state`（箱体偏移和闸门状态）和 `story_cue_index`，不可重复发放已结算剧情或奖励。
## 剧情与状态

- 开场：侦察员岚在锈港外环接到撤离艇信号，发现港口自动起重系统被未知协议接管。
- 中段：玩家从吊臂下方穿过并回收第一枚航线密钥；广播透露敌人正在搜集反应堆零件。
- 结尾：打开临时航道，岚把密钥交给远程指挥官，确定后续目标是夺回沙脊中继站。
- 剧情通过原创中文短字幕、广播或 NPC 对话呈现；每段不超过两行，必须支持跳过并在存档中记录已读状态。

## 敌人、节奏与检查点

- 敌人配置：锈港步兵、盾牌搬运工、短距跳跃无人机；每组不超过 3 个射击单位，第二段加入一次货箱压制教学。
- 节奏分段：90 秒教学切片：安全区出生→磁锁教学→两波交叉火力→货箱机关→检查点→撤离门。
- 至少设置一个中段检查点和一个结算检查点；检查点保存玩家位置、生命、武器、弹药、玩法状态和剧情节点。
- 视野外生成必须有方向提示；所有高伤害攻击提前至少 0.25 秒显示视觉和音频预警。低性能模式下保留预警、弱点和检查点反馈。

## Boss / 非 Boss 约束

- 非 Boss 关；只允许一名重甲搬运工作为精英，不能出现阶段条或 Boss 结算。
- 非 Boss 关不得出现 Boss 阶段条或 Boss 结算；最多放置一名教学型精英，且高伤害动作必须提前 0.25 秒预警。

## 资源清单与素材边界

- 2 套原创港口背景瓦片、磁吊臂和货箱程序化图形、3 个敌人变体、磁锁/压砸特效、航线密钥图标、中文广播文本。
- 所有图片、音频、字体和台词来源必须同步登记到 ASSET_SOURCES.md；优先使用程序化 Canvas 图形或项目原创素材，不得引用商业游戏的角色、地图、商标、字体、音频或可识别构图。
- 新增资源使用 snake_case 命名；关卡配置文件命名为 level_01.json，并由 AssetRepository 离线加载。

## 验收标准

- [ ] 冷启动后从关卡选择页进入本关，横屏安全区与 UI 对齐，无错位、裁切或重叠。
- [ ] 在默认与低性能模式下完成基础动作和“磁吊箱体”教学，输入不丢失、不重复扣弹、不崩溃。
- [ ] 完成开场、中段、结尾剧情，字幕可跳过，重新进入后从最近检查点恢复正确状态。
- [ ] 验证敌人受击、死亡、掉落、玩家死亡、检查点重试与结算返回；非 Boss 关无 Boss 阶段条，Boss 关三阶段均可到达。
- [ ] 切后台再返回会自动暂停，恢复后不追补离线时间；断网运行无远程请求、异常或阻塞。
- [ ] 右下角操作区在本关最密集战斗中不遮挡机关、预警、弱点和结算按钮，触控命中区域与视觉按钮一致。
- [ ] 设备验收记录包含设备型号、横屏分辨率、帧率目标、录屏时间戳和已知限制。

## 实现与证据

运行时实现：`GameView` 在固定步长中将射击投射物命中磁锁转换为 `MagneticCoverCommand.ShootLock`，消费 `CoverMoved`/`DoorPressed` 事件，并用同一快照绘制吊臂、货箱、磁锁和开启闸门；敌方投射物会被当前箱体位置遮挡。`SaveProfile` 保存受限的箱体偏移数组，继续行动和检查点重试会恢复该状态。

纯 reducer 回归：`tools/verify_magnetic_cover_mechanic.ps1` → `MagneticCoverMechanicVerifier: PASS (65 assertions)`。

```powershell
./gradlew.bat :app:assembleDebug
rg -n "level_01|rust-harbor-departure|磁吊箱体" app src PRD.md TECH_DESIGN.md
adb shell am force-stop com.steelfire.assault
adb shell monkey -p com.steelfire.assault 1
adb shell settings put global airplane_mode_on 1
```

手工路线：冷启动 → 关卡选择 → 本关入口 → 检查点 → 结算 → 返回关卡选择；再执行切后台/恢复、死亡/重试、低性能模式和断网路线。若连接真机，保存 adb logcat、横屏录屏和 APK SHA-256。

**证据目录：** .scratch/50-level-expansion/audits/level-01/（建议包含 route.mp4、screenshots/、static-check.txt、apk-sha256.txt、device.txt 与 known-limitations.md）。

当前静态证据：`python tools/verify_50_level_assets.py` 与 `python tools/verify_campaign_runtime.py` 已通过；当前 APK 为 v0.1.1，设备验收仍因 ADB 未连接而待补。
