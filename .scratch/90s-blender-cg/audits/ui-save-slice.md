# 工单 08：UI / 存档切片审计

日期：2026-10-03

## 已验证

运行：

```text
python tools/verify_ui_save.py
```

结果：`UI_SAVE_SLICE_STATIC_AUDIT=PASS`。

审计覆盖：

- `SaveProfile` schema version 1、数值范围、旧键迁移和异常 JSON 默认值重建。
- Continue 按钮与 checkpoint / 分数 / 波次索引的本地保存路径。
- `MainActivity.onPause/onResume`、窗口焦点和固定步长时钟重置。
- 1280×720 逻辑画布的等比 letterbox 变换和触控逆变换。
- 暂停层“继续 / 退出关卡”、结果层“进入下一关 / 返回关卡选择”路径。

## 尚未闭单

以下项目必须由真机或横屏模拟器完成，静态脚本不会伪造通过：

1. iQOO 13 冷启动后删除 `steelfire_save` 并确认默认档能进入菜单。
2. 写入非法 `save_profile_json` 后启动，确认无崩溃并能继续进入关卡。
3. 运行中切后台再返回，确认画面停在暂停层、生命和进度没有追补，音频没有重复叠加。
4. 在 16:9、20:9 横屏尺寸触摸暂停、射击和移动边界，确认安全区外点按不会误触。
5. 断网启动并查看 logcat，确认无远程请求或未处理异常。

这些证据产生前，工单 08 仍保持“设备验收待完成”。
