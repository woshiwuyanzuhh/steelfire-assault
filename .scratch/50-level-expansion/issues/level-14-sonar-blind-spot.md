# Level 14：声呐盲区

**章节：** 盐雾潮汐
**工单类型：** 单关卡可演示纵向切片
**目标与可演示边界：** 将“盐雾海底仓”做成一个可从关卡选择页进入、可在单次运行中完成的横版动作射击纵向切片。该切片必须使用固定步长、状态快照/事件传递和本地 AssetRepository，并遵守安全区与右下角控制台的统一契约。

**Blocked by：** level-13 (previous level vertical slice)

**状态：** in_progress（JSON/manifest、通用固定步长运行时入口已接入；设备验收待继续）

## 新玩法契约

- 核心新玩法：声呐节奏：玩家在声呐扫描间隙移动，射击会增加噪声并吸引潜伏敌人。
- 玩家动作必须覆盖移动、跳跃、下蹲、瞄准、射击、换武器、投掷；新玩法只扩展一个可验证的规则，不改变基础输入映射。
- 关卡入口、检查点和结算点都要在横屏安全区内可触达；右下角操作按钮不能遮挡关键机关、敌人预警或 Boss 弱点。
- 新玩法失败后可从最近检查点重试，不能因实体删除、镜头边界或错误的状态回调造成软锁。

## LevelSpec / manifest 契约

关卡配置写入 `app/src/main/assets/levels/level_14.json`，资源清单写入 `levels/level_14/manifest.json`；`story_cue` 只引用本地字幕/音频表，`asset_refs` 只能指向 APK 内路径。`new_mechanic.id` 需要在 `MechanicController` 白名单和离线静态校验中登记。

```json
{
  "id": "level_14_sonar_blind_spot",
  "index": 14,
  "act": 3,
  "title": "声呐盲区",
  "world_width": 11520,
  "story_cue": "story_l14_intro",
  "new_mechanic": {"id": "sonar_rhythm", "version": 1},
  "checkpoints": [{"x": 2400, "id": "l14_checkpoint"}, {"x": 7200, "id": "l14_clear"}],
  "beats": [{
    "id": "l14_b01",
    "x": 360,
    "purpose": "teach_sonar_rhythm",
    "enemy_kinds": ["regular", "elite"],
    "mechanic_params": {"id": "sonar_rhythm"},
    "story_cue": "story_l14_mid",
    "asset_refs": ["levels/level_14/background_a", "levels/level_14/mechanic"]
  }],
  "boss": null,
  "asset_manifest": "levels/level_14/manifest.json"
}
```

解析器只接受白名单字段；重试必须恢复 `beat_index`、`mechanic_state` 和 `story_cue_index`，不可重复发放已结算剧情或奖励。
## 剧情与状态

- 开场：仓门后的运输线被声呐网保护，岚必须关闭三台监听器。
- 中段：玩家利用掩体和安静移动穿过扫描扇区，发现潜航兽正在蓄能。
- 结尾：监听器关闭后，潜航兽 H-15 发出深水呼号并锁定泵站。
- 剧情通过原创中文短字幕、广播或 NPC 对话呈现；每段不超过两行，必须支持跳过并在存档中记录已读状态。

## 敌人、节奏与检查点

- 敌人配置：潜伏鳗、声呐守卫、静音炮台；声呐扫描扇区必须以半透明扇形显示。
- 节奏分段：安静移动教学→单扇区→双扇区交叠→检查点→监听器同步关闭。
- 至少设置一个中段检查点和一个结算检查点；检查点保存玩家位置、生命、武器、弹药、玩法状态和剧情节点。
- 视野外生成必须有方向提示；所有高伤害攻击提前至少 0.25 秒显示视觉和音频预警。低性能模式下保留预警、弱点和检查点反馈。

## Boss / 非 Boss 约束

- 非 Boss 关；允许潜航兽远景触手作为环境威胁，不得直接进入 Boss 战。
- 非 Boss 关不得出现 Boss 阶段条或 Boss 结算；最多放置一名教学型精英，且高伤害动作必须提前 0.25 秒预警。

## 资源清单与素材边界

- 声呐扇区、扫描音频提示、监听器、潜伏鳗材质、静音炮台、呼号字幕。
- 所有图片、音频、字体和台词来源必须同步登记到 ASSET_SOURCES.md；优先使用程序化 Canvas 图形或项目原创素材，不得引用商业游戏的角色、地图、商标、字体、音频或可识别构图。
- 新增资源使用 snake_case 命名；关卡配置文件命名为 level_14.json，并由 AssetRepository 离线加载。

## 验收标准

- [ ] 冷启动后从关卡选择页进入本关，横屏安全区与 UI 对齐，无错位、裁切或重叠。
- [ ] 在默认与低性能模式下完成基础动作和“声呐节奏”教学，输入不丢失、不重复扣弹、不崩溃。
- [ ] 完成开场、中段、结尾剧情，字幕可跳过，重新进入后从最近检查点恢复正确状态。
- [ ] 验证敌人受击、死亡、掉落、玩家死亡、检查点重试与结算返回；非 Boss 关无 Boss 阶段条，Boss 关三阶段均可到达。
- [ ] 切后台再返回会自动暂停，恢复后不追补离线时间；断网运行无远程请求、异常或阻塞。
- [ ] 右下角操作区在本关最密集战斗中不遮挡机关、预警、弱点和结算按钮，触控命中区域与视觉按钮一致。
- [ ] 设备验收记录包含设备型号、横屏分辨率、帧率目标、录屏时间戳和已知限制。

## 实现与证据

```powershell
./gradlew.bat :app:assembleDebug
rg -n "level_14|sonar-blind-spot|声呐节奏" app src PRD.md TECH_DESIGN.md
adb shell am force-stop com.steelfire.assault
adb shell monkey -p com.steelfire.assault 1
adb shell settings put global airplane_mode_on 1
```

手工路线：冷启动 → 关卡选择 → 本关入口 → 检查点 → 结算 → 返回关卡选择；再执行切后台/恢复、死亡/重试、低性能模式和断网路线。若连接真机，保存 adb logcat、横屏录屏和 APK SHA-256。

**证据目录：** .scratch/50-level-expansion/audits/level-14/（建议包含 route.mp4、screenshots/、static-check.txt、apk-sha256.txt、device.txt 与 known-limitations.md）。

当前静态证据：`python tools/verify_50_level_assets.py` 与 `python tools/verify_campaign_runtime.py` 已通过；当前 APK 为 v0.1.1，设备验收仍因 ADB 未连接而待补。
