# 钢火突袭（Steelfire Assault）

钢火突袭是一款原创 Android 横版动作射击游戏，使用 Kotlin、Android View 和 Canvas 实现。项目包含 50 个离线关卡、固定步长战斗、触控操作、载具、Boss、检查点存档和本地资源管线。

项目只借鉴跑射类型的抽象玩法，不使用《合金弹头》或其他商业游戏的角色、地图、台词、商标、代码或素材。资源来源和许可证记录见 [ASSET_SOURCES.md](ASSET_SOURCES.md)。

## 构建

仓库附带离线构建所需的脚本入口。准备 JDK 17、Android SDK Platform 35、Build Tools 35.0.0 和 Gradle 8.10.2 后，在 Windows PowerShell 中运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

APK 和 SHA-256 文件会写入 `dist/`。构建不会访问运行时网络资源。

## 校验

```powershell
python tools/verify_50_level_plan.py
python tools/verify_50_level_assets.py
python tools/verify_campaign_runtime.py
python tools/verify_ui_save.py
python tools/verify_combat_slice.py
python tools/verify_level_pacing.py
```

设备验收以横屏 Android 设备或模拟器为准；没有连接设备时，静态检查和构建不能代替真机验收。

## 文档

- [PRD.md](PRD.md)：产品需求和原创边界
- [TECH_DESIGN.md](TECH_DESIGN.md)：技术架构和数据契约
- [GAME_DESIGN.md](GAME_DESIGN.md)：50 关战役设计
- [PROGRESS.md](PROGRESS.md)：当前交付状态和已知限制

代码和文档采用 MIT License；第三方资源按 [ASSET_SOURCES.md](ASSET_SOURCES.md) 中各自许可证使用。
