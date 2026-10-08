# Level 15：盐雾潜航兽

**章节：** 盐雾潮汐
**工单类型：** 单关卡可演示纵向切片
**目标与可演示边界：** 将“盐雾潮汐深井”做成一个可从关卡选择页进入、可在单次运行中完成的横版动作射击纵向切片。该切片必须使用固定步长、状态快照/事件传递和本地 AssetRepository，并遵守安全区与右下角控制台的统一契约。

**Blocked by：** level-11 through level-14 in this chapter

**状态：** in_progress（JSON/manifest、通用固定步长运行时入口已接入；设备验收待继续）

## 新玩法契约

- 核心新玩法：水位、潜伏和反冲：Boss 在高低水位间潜伏，玩家利用反冲炮把其逼出弱点区。
- 玩家动作必须覆盖移动、跳跃、下蹲、瞄准、射击、换武器、投掷；新玩法只扩展一个可验证的规则，不改变基础输入映射。
- 关卡入口、检查点和结算点都要在横屏安全区内可触达；右下角操作按钮不能遮挡关键机关、敌人预警或 Boss 弱点。
- 新玩法失败后可从最近检查点重试，不能因实体删除、镜头边界或错误的状态回调造成软锁。

## LevelSpec / manifest 契约

关卡配置写入 `app/src/main/assets/levels/level_15.json`，资源清单写入 `levels/level_15/manifest.json`；`story_cue` 只引用本地字幕/音频表，`asset_refs` 只能指向 APK 内路径。`new_mechanic.id` 需要在 `MechanicController` 白名单和离线静态校验中登记。

```json
{
  "id": "level_15_salt_fog_submersible",
  "index": 15,
  "act": 3,
  "title": "盐雾潜航兽",
  "world_width": 11520,
  "story_cue": "story_l15_intro",
  "new_mechanic": {"id": "submersible_recoil", "version": 1},
  "checkpoints": [{"x": 2400, "id": "l15_checkpoint"}, {"x": 7200, "id": "l15_clear"}],
  "beats": [{
    "id": "l15_b01",
    "x": 360,
    "purpose": "teach_submersible_recoil",
    "enemy_kinds": ["regular", "elite"],
    "mechanic_params": {"id": "submersible_recoil"},
    "story_cue": "story_l15_mid",
    "asset_refs": ["levels/level_15/background_a", "levels/level_15/mechanic"]
  }],
  "boss": { "id": "boss_h_15", "phases": ["low_water_stalk", "high_water_recoil", "core_exposure"], "weakpoint": "tide_core", "warning_seconds": 0.25 },
  "asset_manifest": "levels/level_15/manifest.json"
}
```

解析器只接受白名单字段；重试必须恢复 `beat_index`、`mechanic_state` 和 `story_cue_index`，不可重复发放已结算剧情或奖励。
## 剧情与状态

- 开场：潜航兽 H-15 撞开深井泵站，准备把潮汐能量输送给敌方熔炉。
- 中段：阶段一低水位追踪潜伏点；阶段二水位上升，玩家用反冲炮把 H-15 推入电弧网；阶段三护甲裂开后进行核心爆发。
- 结尾：H-15 失去动力，深井地图显示能量最终流向极夜熔炉。
- 剧情通过原创中文短字幕、广播或 NPC 对话呈现；每段不超过两行，必须支持跳过并在存档中记录已读状态。

## 敌人、节奏与检查点

- 敌人配置：Boss 外仅有维修鳗和漂浮水雷；每次水位切换前必须停火并显示方向。
- 节奏分段：潜伏提示→阶段一水位追踪→检查点→阶段二反冲教学→阶段三核心窗口与结算。
- 至少设置一个中段检查点和一个结算检查点；检查点保存玩家位置、生命、武器、弹药、玩法状态和剧情节点。
- 视野外生成必须有方向提示；所有高伤害攻击提前至少 0.25 秒显示视觉和音频预警。低性能模式下保留预警、弱点和检查点反馈。

## Boss / 非 Boss 约束

- Boss 关：至少 3 阶段、潜伏位置提示、反冲弱点、阶段 HUD、重击预警 ≥0.25 秒。
- Boss关必须有 3 个可读阶段、阶段 HUD、弱点窗口，以及所有高伤害动作至少提前 0.25 秒的视觉与音频预警。

## 资源清单与素材边界

- H-15 原创潜航兽、深井水位层、反冲炮、潜伏扫描圈、电弧网、章节三结算动画。
- 所有图片、音频、字体和台词来源必须同步登记到 ASSET_SOURCES.md；优先使用程序化 Canvas 图形或项目原创素材，不得引用商业游戏的角色、地图、商标、字体、音频或可识别构图。
- 新增资源使用 snake_case 命名；关卡配置文件命名为 level_15.json，并由 AssetRepository 离线加载。

## 验收标准

- [ ] 冷启动后从关卡选择页进入本关，横屏安全区与 UI 对齐，无错位、裁切或重叠。
- [ ] 在默认与低性能模式下完成基础动作和“水位、潜伏和反冲”教学，输入不丢失、不重复扣弹、不崩溃。
- [ ] 完成开场、中段、结尾剧情，字幕可跳过，重新进入后从最近检查点恢复正确状态。
- [ ] 验证敌人受击、死亡、掉落、玩家死亡、检查点重试与结算返回；非 Boss 关无 Boss 阶段条，Boss 关三阶段均可到达。
- [ ] 切后台再返回会自动暂停，恢复后不追补离线时间；断网运行无远程请求、异常或阻塞。
- [ ] 右下角操作区在本关最密集战斗中不遮挡机关、预警、弱点和结算按钮，触控命中区域与视觉按钮一致。
- [ ] 设备验收记录包含设备型号、横屏分辨率、帧率目标、录屏时间戳和已知限制。

## 实现与证据

```powershell
./gradlew.bat :app:assembleDebug
rg -n "level_15|salt-fog-submersible|水位、潜伏和反冲" app src PRD.md TECH_DESIGN.md
adb shell am force-stop com.steelfire.assault
adb shell monkey -p com.steelfire.assault 1
adb shell settings put global airplane_mode_on 1
```

手工路线：冷启动 → 关卡选择 → 本关入口 → 检查点 → 结算 → 返回关卡选择；再执行切后台/恢复、死亡/重试、低性能模式和断网路线。若连接真机，保存 adb logcat、横屏录屏和 APK SHA-256。

**证据目录：** .scratch/50-level-expansion/audits/level-15/（建议包含 route.mp4、screenshots/、static-check.txt、apk-sha256.txt、device.txt 与 known-limitations.md）。

当前静态证据：`python tools/verify_50_level_assets.py` 与 `python tools/verify_campaign_runtime.py` 已通过；当前 APK 为 v0.1.1，设备验收仍因 ADB 未连接而待补。
