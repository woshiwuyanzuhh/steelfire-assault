# 03: Blender 90 秒完整镜头与动画

**What to build:** 在 Blender 5.2.2 中完成可编辑的原创 90 秒 CG 场景与镜头动画。镜头使用固定 24 fps、2160 帧，全部模型由本地 Blender 脚本生成；交付 `.blend` 工程、可复现构建脚本、镜头/旁白同步清单和审核关键帧。

**Blocked by:** 01: Product baseline and acceptance protocol; 02: Blender 90-second CG visual development片

**Status:** complete

## Acceptance criteria

- [x] `tools/blender/build_steelfire_cg.py` 在 Blender 5.2.2 headless 模式运行无异常，并生成 `art/blender/steelfire_cg_scene.blend`。
- [x] 工程时间线为 24 fps、90.0 秒、2160 帧；渲染设置固定为 960×540、Eevee、PNG 帧序列，未依赖网络资源。
- [x] 场景至少包含原创六轮装甲载具、悬挂/轮毂/履带块/装甲筋/螺栓/炮塔/天线、机库梁柱/线缆槽/湿地板、反应堆环与核心、维护无人机和可识别操作员。
- [x] 镜头按旁白 cue 对齐：8/18/28/38/49/59/69/78/86 秒；清单写入 `dist/cg/steelfire_cg_shots.json`。
- [x] 动画包含操作员进入、反应堆能量脉冲、无人机位移、炮塔跟踪、探照灯与反应堆灯光变化；每个动画均为确定性关键帧。
- [x] `dist/cg/qa2/final_contact_sheet.png` 记录关键帧 1/192/432/672/912/1176/1416/1656/1873/2160 的最终渲染结果，作为视觉验收证据；已检查材质层次、景深、主体曲面、人物细节、尘雾尺度和安全区标题卡。
- [x] 完整 2160 帧序列已由 Blender 5.2.2 渲染完成，并与 90 秒音频母带离线 mux 为 H.264/AAC MP4：`dist/cg/steelfire_intro_90s.mp4`；`python tools/blender/verify_cg_render.py` 通过，报告 `frames=2160 movie_duration=90.000s`。每个 cue 的音频内容和偏移由工单 04 的母带/cue 表负责复核。

## Verification commands

```powershell
& 'D:\SteamLibrary\steamapps\common\Blender\blender.exe' --background --python tools/blender/build_steelfire_cg.py
STEELFIRE_RENDER=1 & 'D:\SteamLibrary\steamapps\common\Blender\blender.exe' --background --python tools/blender/build_steelfire_cg.py
```

The first command is the deterministic scene/build check. The second command renders the complete sequence and may run for several minutes on a desktop CPU/GPU. Existing frames can be reused by the offline encoder when a render is interrupted.
