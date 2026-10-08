# 素材来源与许可记录

本项目使用原创生成素材与明确标注为 CC0 的公共素材。所有外部资源在导入前应保留原始许可证文件；素材只用于原创《钢火突袭》的角色、场景和音频，不复制任何商业游戏的角色、商标、音乐、关卡布局或素材。

## 已加入工程的原创素材

| 本地路径 | 内容 | 来源/许可 |
| --- | --- | --- |
| `app/src/main/assets/gfx/backgrounds/industrial_port.png` | 锈港工业港背景，16:9 | 本项目原创生成插画；无第三方素材依赖 |
| `app/src/main/assets/gfx/characters/operative_sheet.png` | 灰脊小队角色四帧动作表 | 本项目原创生成插画；无第三方素材依赖 |
| `app/src/main/assets/gfx/characters/enemy_sheet.png` | 四类敌人剪影表 | 本项目原创生成插画；无第三方素材依赖 |
| `app/src/main/assets/gfx/vehicles/vehicle_boss_sheet.png` | 犀虎机甲车与 ARK-9 Boss | 本项目原创生成插画；无第三方素材依赖 |
| `app/src/main/assets/audio/sfx/*.wav` | 枪声、命中、爆炸、受伤、跳跃、UI、Boss | 本项目用程序合成的原创 PCM 音效 |
| `app/src/main/assets/audio/industrial_loop.wav` | 工业环境循环底床 | 本项目用程序合成的原创 PCM 音效 |
| `app/src/main/assets/cg/cg_01_hangar_3d.png` | 3D 风格机库与原创装甲车开场镜头 | 原创生成图像；SHA-256 `A7FFA6997EE6481F31F6F4C0C09D99A272F99986BD73787F1B90F235976A44B1` |
| `app/src/main/assets/cg/cg_02_operative_2d.png` | 2D 手绘风格灰脊队员与锈港天际线镜头 | 原创生成图像；SHA-256 `B0ED0EA12FBF3977A712A4D5287307F1716874572B70955B66AB5691B1571044` |
| `app/src/main/assets/cg/cg_03_core_title.png` | 阿克九核心反应堆标题镜头 | 原创生成图像；SHA-256 `CE0CA2C7208D8CB9CDD97D2FDCA77648BA1EC67A84CAE947AE522966813E8F93` |
| `app/src/main/assets/audio/cg/intro_voiceover.wav` | 开场中文旁白（北线坠毁后的第七十二小时……） | 本机 Microsoft Huihui Desktop SAPI 生成；SHA-256 `8636F2865E86A38F00D6F08A577FE27A64358058FB38E31576F933D1D94CDC29` |
| `app/src/main/assets/audio/kenney/*.ogg` | 激光射击、金属命中、爆炸、力场和控制台 UI 音效 | Kenney Sci-fi Sounds，CC0 1.0；保留原始 `License.txt` 于候选目录 |

### 50 关程序化素材契约

`level_03` 至 `level_50` 的每个目录都在 `manifest.json` 中登记本关机制几何、碰撞层、粒子配置、剧情字幕和（需要时）Boss 几何；这些是由 `GameView` 的 Canvas 绘制器离线生成的原创程序化资源，不依赖远程文件。背景、角色、敌人和 UI 音效复用上方已登记的本地文件，manifest 同时记录路径、大小、SHA-256、生成器、许可证和 fallback。生成入口为 `tools/generate_level_assets.py`，机制阶段由 `GenericMechanic` 在固定步长中消费 `cycle_ticks`、`severity` 和 `visual_variant` 参数。

| `art/blender/steelfire_cg_scene.blend` | 原创装甲六轮载具、机库、反应堆、灯光、90 秒镜头动画工程文件（含安全区标题卡） | Blender 程序化建模；SHA-256 `0DE3C81C7E6B2BE18BD070C119B5B4F46B663320F0EE2CC7E4FC6A4EBDE2CF23` |
| `art/blender/steelfire_cg_scene_voiced.blend` | 上述工程的 VSE 配音绑定副本，含 9 个 VO 时间线标记 | 本项目本地 WAV 绑定；SHA-256 `213440E880E1B9E395827457E9EB5E31F04DDF71E800C7FBB157E79D0ABC5707` |
| `tools/blender/build_steelfire_cg.py` | Blender 5.2.2 可复现建模与 90 秒（2160 帧）帧序列渲染脚本 | 本项目原创脚本；SHA-256 `FF82EA79D48C67551DDACD1EDC377D11CA6D9FC25B507B4679CF8AF59AF6E126` |
| `tools/blender/encode_cg_90s_mp4.py` | 离线将 90 秒 PNG 帧序列与本地中文旁白编码为 H.264/AAC MP4 | 本项目原创脚本；只调用本机 ffmpeg |
| `tools/blender/verify_cg_render.py` | 检查 2160 帧连续性、尺寸、镜头清单和最终视频 90 秒时长 | 本项目原创离线验收脚本 |
| `dist/cg/blender_frames/frame_####.png` | 960×540、24fps、90 秒的 Blender CG 无损帧序列 | 由上述原创 Blender 工程生成；可通过 `STEELFIRE_RENDER=1` 重建 |
| `dist/cg/steelfire_cg_shots.json` | 10 段镜头、起止帧、旁白同步点清单 | 由 Blender 构建脚本生成；SHA-256 `0913DEBDA5A8F47A825BD258043918FD89364B94EAAD19A10F76DED9A7BBFB30` |
| `dist/cg/steelfire_intro_preview.gif` | 480×270 审核预览 GIF，不作为 APK 运行时依赖 | 由上述帧序列本地缩略生成 |
| `dist/cg/steelfire_intro_90s.mp4` | 960×540、24fps、90 秒最终 Blender CG，含本地中文旁白（AAC）及片名落版 | 由 2160 张 Blender PNG 与 `intro_voiceover_90s.wav` 离线编码；SHA-256 `5B8EDEABBB22AA4C3E661E2FB73724F3F418BA621EA4613F1C011081DF8A641B` |
| `dist/cg/qa2/final_contact_sheet.png` | 10 个关键帧最终视觉 QA 证据，含 78–90 秒标题卡 | 由最终 2160 帧本地缩略生成；SHA-256 `A49CBE5BA9385F629E26C0D582902EF11DCE368773774475BE84638A29EEEAC9` |
| `dist/cg/steelfire_intro.mp4` | 旧版 10.8 秒审核视频，仅用于历史对比 | 由三张运行时 PNG 和本地旁白编码；90 秒版本应从 `blender_frames` 与 `intro_voiceover_90s.wav` 离线复核后生成 |

运行时接入的 Kenney 文件为 `laser_large_000.ogg`、`impact_metal_000.ogg`、`explosion_crunch_000.ogg`、`force_field_000.ogg`、`computer_noise_000.ogg`，均来自 [Kenney Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds)，未二次编辑。

## 已核实、可继续扩展的 CC0 素材源

- [Kenney Desert Shooter Pack](https://kenney.nl/assets/desert-shooter-pack)：角色、枪械、沙漠场景，CC0。
- [Kenney Interface Sounds](https://kenney.nl/assets/interface-sounds)：界面提示音，CC0。
- [Kenney Digital Audio](https://kenney.nl/assets/digital-audio)：电子提示和环境音，CC0。
- [Kenney Pixel Platformer Industrial Expansion](https://kenney.nl/assets/pixel-platformer-industrial-expansion)：工业基地图块，CC0。
- [OpenGameArt 2D Game Character Pack – Slim Version](https://opengameart.org/content/2d-game-character-pack-slim-version)：士兵/科幻角色，页面标注 CC0。
- [OpenGameArt Industrial Parallax Background](https://opengameart.org/content/industrial-parallax-background)：工业视差背景，页面标注 CC0。
- [OpenGameArt Sci-Fi Background](https://opengameart.org/content/sci-fi-background)：科幻背景，页面标注 CC0。
- [OpenGameArt Sound Effects for Platformer](https://opengameart.org/content/sound-effects-for-platformer)：平台动作音效，页面标注 CC0/公有领域。
- [Kay Lousberg 2D Guns](https://kaylousberg.itch.io/gun-assets)：六类枪械图形，页面标注 CC0。

## 导入规则

每次导入外部包时在本表追加下载日期、原始 URL、压缩包 SHA-256、保留的许可证文件和本地目录。Heavy Terror Machine 资源页面的美术可供参考，但其评论区提到音效可能来自第三方购买包；本项目不导入该包音频。

## 已下载到候选目录的 CC0 素材（2026-10-03）

以下压缩包来自 OpenGameArt 原始下载链接，已保存到 `tmp/cc0_asset_archives/` 并解压到 `tmp/third_party/cc0_candidates/`。逐包的作者、许可声明、文件大小和 SHA-256 见 `tmp/cc0_asset_archives/DOWNLOAD_REPORT.md` 及各目录 `SOURCE.txt`。

| 本地目录 | 来源/许可 | 压缩包 SHA-256 |
| --- | --- | --- |
| `tmp/third_party/cc0_candidates/character_pack_slim` | [2D Game Character Pack - Slim Version](https://opengameart.org/content/2d-game-character-pack-slim-version)，overcrafted，CC0 | `29F00FD60066E17DFA8699ECD2E6DA15570C37469BA786FC445A9223394EA0D0` |
| `tmp/third_party/cc0_candidates/industrial_parallax` | [Industrial Parallax Background](https://opengameart.org/content/industrial-parallax-background)，ansimuz，CC0 | `DC81BE04188A99E44D9D233CCCB4AF6606476E1EA63450995D88D5698FF57FD3` |
| `tmp/third_party/cc0_candidates/sci_fi_background` | [Sci-Fi Background](https://opengameart.org/content/sci-fi-background)，hassekf，CC0 | `C6737C408B73450478F71F435A20F20E4DC6F0D4E749904B8BFC5340378FF755` |
| `tmp/third_party/cc0_candidates/platformer_sounds` | [Platformer Sounds](https://opengameart.org/content/platformer-sounds-terminal-interaction-door-shots-bang-and-footsteps/)，yd，CC0 | `D88921E4A4C3D02ACC09D605AAA02AB89FA1D9F6E28ED56D79D37E04E15241B5` |

Heavy Terror Machine 音效未下载。候选目录中的外部素材在正式接入绘制/音频代码前仍需按技术方案完成尺寸、命名、采样率和内存占用归一化。

当前 APK 直接使用了候选音效中的 `explode.ogg`、`landing.ogg`、`steps_platform.ogg`、`beep_message.ogg` 和 `cogs.ogg`，复制到 `app/src/main/assets/audio/cc0/`；枪声、受伤和环境循环继续使用本项目原创合成音效。

## 扩展素材包（2026-10-03）

以下资源已下载到 `tmp/third_party/expansion_20261003/`，逐项来源、许可、大小、SHA-256 和用途说明见同目录 `EXPANSION_REPORT.md` 与各包 `SOURCE.txt`。全部为 OpenGameArt 页面明确标注的 CC0 资源；未下载 Heavy Terror Machine 音效。

| 本地路径 | 内容与用途 | 来源/许可 | SHA-256 |
| --- | --- | --- | --- |
| `tmp/third_party/expansion_20261003/industrial_tiles_sci_fi_platformer/sheet.png`、`sheet_alt.png` | 16x16 科幻平台/机器人/机关图块与备用调色板 | [Sci-fi platformer tileset](https://opengameart.org/content/sci-fi-platformer-tileset)，Buch，CC0 | `3200DFB69F872CBB87476729C88EFACFC654842B6185AF6017B61A449C8B7100`、`E9845B55CD0853A6B5500F1306DBA1DA3B8BB9194190B821D3399908A4B74539` |
| `tmp/third_party/expansion_20261003/warped_top_down_tech_lab_2` | 32x32 科幻实验室/工业室内图块 | [Warped Top-Down Tech Lab 2](https://opengameart.org/content/warped-top-down-tech-lab-2)，ansimuz，CC0 | `F51E5B0399D9D7F7A512D0B529EE9699BE1B59B9B9E635B2D439043636156ABF` |
| `tmp/third_party/expansion_20261003/enemy_sprites_mieki256` | 64x64 16帧/8帧科幻敌机精灵表 | [Enemy sprite](https://opengameart.org/content/enemy-sprite)，mieki256，CC0 | `3D99D9A23235DE329873D375998F7B50D0B0E98D561E2B899508AC03653CFB44`、`F3D827FEF3B6435D791C6B524E8E4D0864B6201E427A845C87012007D7BB9CCE` |
| `tmp/third_party/expansion_20261003/robot_enemy_pack_3d` | 5 个带动画、albedo/normal/specular 贴图的机器人 3D 参考 | [Robot Enemy Pack](https://opengameart.org/content/robot-enemy-pack)，Teh_Bucket 等，CC0 | `724CD18D1DBFF18AD4F8EE819975C5B677832FB3FA1DBC83AB07A49D613F7C74` |
| `tmp/third_party/expansion_20261003/gunshot_sounds` | CZ-52、Mosin、SKS、霰弹枪原始录音，需后期处理 | [Gunshot Sounds](https://opengameart.org/content/gunshot-sounds)，Tabasco，CC0 | `5B3960083A94E18EE47BC84376615A476DEBC884B18F25B93EA4B6AB3F278E4F` |
| `tmp/third_party/expansion_20261003/retro_synth_sfx` | 爆炸、射击、激光、死亡、强化等合成音效与源文件 | [50 CC0 retro / synth SFX](https://opengameart.org/content/50-cc0-retro-synth-sfx)，rubberduck，CC0 | `DB9370BFBE228D1FC3912EC455AD358129EEEC593169FC02701FF48D888DB9CB` |

Robot Enemy Pack 保留为 3D 质感和法线高光参考，不直接复制商业作品表达；正式接入 APK 前只选取必要的原创化渲染结果或 2D 化素材。

| `tmp/third_party/expansion_20261003/kenney_industrial_expansion` | 110 个 18x18 工业/工厂图块、图块表和 tilemap，适合作为首关地面、墙体和机械装饰 | [Kenney Pixel Platformer Industrial Expansion](https://kenney.nl/assets/pixel-platformer-industrial-expansion)，Kenney，CC0 1.0（内置 `License.txt`） | `C46E8FEE3528434D1680D50A2373C77EB33B1D5DB5C7B000D495EDECC854FD3E` |

| `tmp/third_party/expansion_20261003/kenney_impact_sounds` | 130 个碰撞、爆炸、脚步和机械 Foley OGG | [Kenney Impact Sounds](https://kenney.nl/assets/impact-sounds)，Kenney，CC0 1.0（内置 `License.txt`） | `029D734AF1582474EDF3A694D1B0CEBC97C1C152F2F39FA34D4C2BAFC5DE77F8` |
| `tmp/third_party/expansion_20261003/kenney_sci_fi_sounds` | 60 个科幻武器、激光、控制台和机械 OGG | [Kenney Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds)，Kenney，CC0 1.0（内置 `License.txt`） | `119340F351A5098AD814F78719438C0DA355A9CE8A4C8A3AF6A8D48AA3D49E04` |
| `tmp/third_party/expansion_20261003/kenney_digital_audio` | 60 个数字/太空环境与电子 OGG | [Kenney Digital Audio](https://kenney.nl/assets/digital-audio)，Kenney，CC0 1.0（内置 `License.txt`） | `24E6CE28B76A6D8C89CFF4D331E0965FF5C3DE8A73C612028E9D363CC64E4F06` |


## 90 秒 CG 旁白候选（2026-10-03）

| 本地路径 | 内容 | 来源/许可 |
| --- | --- | --- |
| `app/src/main/assets/audio/cg/intro_voiceover_90s.wav` | 90.0 秒中文旁白母带：叙事层、无线电层、工业环境底床，22.05 kHz/16-bit/mono | 构建期使用 Microsoft Edge/Azure Neural TTS voices（`zh-CN-YunyangNeural`、`zh-CN-YunjianNeural`；见 [Microsoft voice support](https://learn.microsoft.com/azure/ai-services/speech-service/language-support?tabs=tts)）生成后本地混音；运行时只读取已打包 WAV，不请求网络 |
| `tools/audio/synthesize_cg_voiceover.py` | 按 cue 表请求并缓存分段语音；用于重建候选片段 | 本项目原创构建脚本；网络仅用于构建期语音生成 |
| `tools/audio/mix_cg_voiceover.py` | 固定 90 秒时间线、工业底床、无线电 EQ、淡入淡出和峰值控制 | 本项目原创 Python/NumPy/SciPy 构建脚本 |
| 	ools/blender/add_cg_voiceover.py | 将 90 秒旁白 WAV 和 cue marker 绑定到 Blender VSE，供 MP4 渲染 | 本项目原创 Blender 5.2.2 构建脚本 |
| `.scratch/90s-blender-cg/issues/04-natural-voiceover.md` | 旁白工单和逐项验收标准 | 本项目本地工单 |
| `docs/90S_CG_SHOT_BIBLE.md` | 90 秒片头镜头、声音锚点和返工门槛 | 本项目原创制作规范 |
| `docs/FINAL_DELIVERY_REPORT.md` | 本轮 APK、CG、工程文件、验证命令和未关闭设备项 | 本项目本地交付记录 |
| `dist/cg/audio_candidates/edge_voice_segments/` | 原始候选分段（MP3/WAV）和 cue JSON，供听感复核 | 构建期缓存；最终 APK 不依赖该目录 |

网络 TTS 仅在开发机生成音频素材时使用；提交和交付物包含完整 WAV 与构建脚本，Android/Blender 播放阶段无网络依赖。若环境不允许联网，可改用 `tools/audio/build_cg_voiceover.ps1` 的本机 `Microsoft Huihui Desktop` SAPI 回退方案，但听感评分应单独记录。


