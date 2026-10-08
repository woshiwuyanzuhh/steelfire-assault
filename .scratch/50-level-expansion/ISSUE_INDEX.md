# 50 关工单索引

本索引把 50 个关卡工单、3 个 UI 工单和 1 个架构基线按依赖顺序串起来。每个 `level-XX-*.md` 都是一个可以单独演示、单独回滚、单独验收的纵向切片；本索引不能替代关卡工单正文。

## 执行顺序

1. `program-00-baseline.md` 冻结编号、章节、存档、固定步长、资源 manifest 和证据格式。
2. `ui-01-safe-area.md`、`ui-02-control-deck.md`、`ui-03-regression.md` 先关闭，避免后续 50 关重复制造错位和遮挡问题。
3. 依次完成 10 个五关批次：每批前四关引入并练习规则，第五关用三阶段 Boss 检验本批玩法，并写入下一批剧情线索。
4. 关卡工单关闭前，必须把 `level_XX.json`、资源 manifest、`ASSET_SOURCES.md` 变更、固定步长录屏、断网日志、三比例 UI 证据和 APK 哈希一起提交。

## 工单与章节映射

| 批次 | 关卡工单 | 篇章目标 | Boss |
| --- | --- | --- | --- |
| 01 | [L01](issues/level-01-rust-harbor-departure.md) · [L02](issues/level-02-broken-bridge-echo.md) · [L03](issues/level-03-tide-signal.md) · [L04](issues/level-04-silent-warehouse.md) · [L05](issues/level-05-grey-tide-excavator.md) | 锈港余波：建立磁性货运、掩体和轨道规则 | L05 灰潮掘进机 |
| 02 | [L06](issues/level-06-sand-ridge-entry.md) · [L07](issues/level-07-mirage-false-line.md) · [L08](issues/level-08-relay-cipher.md) · [L09](issues/level-09-sandstorm-escort.md) · [L10](issues/level-10-sand-ridge-beacon-carrier.md) | 沙脊通信战：信标干扰、假目标和护送节奏 | L10 沙脊信标母舰 |
| 03 | [L11](issues/level-11-salt-fog-landing.md) · [L12](issues/level-12-pump-station-dive.md) · [L13](issues/level-13-reverse-current-gate.md) · [L14](issues/level-14-sonar-blind-spot.md) · [L15](issues/level-15-salt-fog-submersible.md) | 盐雾潮汐：水位、潜伏、声呐和反冲 | L15 盐雾潜航兽 |
| 04 | [L16](issues/level-16-furnace-threshold.md) · [L17](issues/level-17-rising-furnace-shaft.md) · [L18](issues/level-18-molten-bridge-cut.md) · [L19](issues/level-19-overheat-protocol.md) · [L20](issues/level-20-furnace-forged-warden.md) | 极夜熔炉：热量、熔池、散热和过热窗口 | L20 熔炉铸卫 |
| 05 | [L21](issues/level-21-cloudport-boarding.md) · [L22](issues/level-22-antigravity-lift.md) · [L23](issues/level-23-skycar-chase.md) · [L24](issues/level-24-gravity-inversion.md) · [L25](issues/level-25-sky-interceptor-ring.md) | 高空转运：重力方向、空中平台和追击 | L25 天穹拦截环 |
| 06 | [L26](issues/level-26-magnetic-freight.md) · [L27](issues/level-27-thermal-boots.md) · [L28](issues/level-28-signal-switch.md) · [L29](issues/level-29-bridge-escort.md) · [L30](issues/level-30-frostline-cannon-boss.md) | 冻原列车：磁轨货柜、热靴、信号切换和追车火力 | L30 霜线列车炮 |
| 07 | [L31](issues/level-31-glass-refraction.md) · [L32](issues/level-32-spore-purifier.md) · [L33](issues/level-33-vine-bridge.md) · [L34](issues/level-34-seed-convoy.md) · [L35](issues/level-35-spore-core-boss.md) | 荒原温室：折光、孢子扩散、净化路线和种子护送 | L35 绿幕孢核 |
| 08 | [L36](issues/level-36-counterweight-elevator.md) · [L37](issues/level-37-pressure-valves.md) · [L38](issues/level-38-rotary-tunnel.md) · [L39](issues/level-39-deepwell-rescue.md) · [L40](issues/level-40-drill-emperor-boss.md) | 深井城：配重、电压/压力阀、旋转井壁和救援笼 | L40 深井钻皇 |
| 09 | [L41](issues/level-41-reflection-gates.md) · [L42](issues/level-42-mimic-counterplay.md) · [L43](issues/level-43-memory-sequence.md) · [L44](issues/level-44-dual-world.md) · [L45](issues/level-45-mirror-entity-boss.md) | 黑潮镜库：反射门、拟态反制、记忆序列和双界切换 | L45 黑潮镜像体 |
| 10 | [L46](issues/level-46-coolant-routing.md) · [L47](issues/level-47-civilian-convoy.md) · [L48](issues/level-48-collapse-rhythm.md) · [L49](issues/level-49-overdrive-weapon.md) · [L50](issues/level-50-reactor-throne-boss.md) | 终局撤离：冷却分配、编队保护、坍塌节奏和过载武装 | L50 终局反应堆王座 |

## 完成定义

- 50 个编号连续且唯一；L05、L10、L15、L20、L25、L30、L35、L40、L45、L50 各有独立 Boss、至少三个阶段、弱点窗口和阶段结算。
- 每关工单正文都有目标、`Blocked by`、独有玩法契约、至少三个剧情节点、敌人/节奏/检查点、资源和许可边界、验收标准、验证命令与证据目录。
- 新玩法必须改变可观察的状态、输入或空间规则，不能只换颜色、贴图或增加敌人数；剧情必须产生新的本地 cue 和可恢复的存档状态。
- 所有关卡沿用固定步长、同一安全区快照和动作坞命中映射；低性能模式只降低表现层，断网时仍能从 APK 内资源完整运行。

## 校验命令

```powershell
python tools/verify_50_level_plan.py
python tools/verify_50_level_assets.py
python tools/verify_campaign_runtime.py
```

通过后再按 `AGENTS.md` 的 Android 设备清单逐批构建、安装、实测和归档证据。

## 相关基线

- [50 关扩展计划](README.md)
- [素材包策略](ASSET_STRATEGY.md)
- [50 关架构基线](issues/program-00-baseline.md)
- [UI 安全区修正](issues/ui-01-safe-area.md)
- [右下角动作坞重做](issues/ui-02-control-deck.md)
- [UI 回归验收](issues/ui-03-regression.md)
