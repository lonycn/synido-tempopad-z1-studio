# 目录迁移记录

## 主目录

迁移前：`~/Music/Synido Z1 Starter`

迁移后：项目工作区（公开版本省略维护者本机绝对路径）。

策略：**复制、校验、更新入口，保留原目录作为快照**。未删除旧 Music 文件，未卸载官方软件，也未修改其受保护的沙盒数据。

## 文件对应关系

| 原位置 | 新位置 |
|---|---|
| `Tools/Z1Player.swift` | `src/Z1Player.swift` |
| `.app/Contents/Info.plist` | `app/Info.plist`，作为构建模板 |
| `Z1 演奏助手.app` | `dist/Z1 演奏助手.app`，由迁移后源码重建 |
| `Z1_EASY_Lights.eas` | `projects/easy/Z1_EASY_Lights.eas` |
| `Z1_DRUM_Starter.band` | `projects/garageband/Z1_DRUM_Starter.band` |
| `Z1_KEY_Piano.band` | `projects/garageband/Z1_KEY_Piano.band` |
| `Z1_64pads_source.wav` | `assets/audio/Z1_64pads_source.wav` |
| `PAD_MAP.txt` | `docs/PAD_MAP.txt` |
| `使用说明.txt` | 原文存 `backups/使用说明_迁移前.txt`，当前版为 `docs/使用说明.txt` |
| 初版两个 `.eas` | `backups/easy/` |
| `Tools/validation.json` | `tests/reports/initial-validation.json` |
| `/tmp/make_z1_starter.py` | `scripts/generate_audio.py`，去除旧路径，默认安全生成到 `generated/audio/` |
| `/tmp/z1_midi_test.swift` | `tests/midi_smoke.swift` |
| `/tmp/synido_ui.py`、`z1_ax.py`、`z1_ocr.swift` | `tools/diagnostics/` |
| 下载的官方 PDF、OCR 文本 | `references/vendor/`，来源单独记录 |

`.DS_Store` 不纳入维护内容。临时屏幕截图含桌面／其他应用信息，不自动归档；复现工具已保留。

## 本次代码调整

- 源码不再引用旧 Music 路径。
- 项目目录由 `<项目>/dist/*.app` 的位置推导，可用 `Z1_PROJECT_ROOT` 覆盖。
- 应用菜单增加“打开项目目录”。
- 音频生成器参数化输出路径，默认不覆盖正式素材。
- 增加本机构建、文件校验和桌面链接维护脚本。
- **没有改动已验证的 DRUM 映射、系统音源选择、EASY 灯光工程及原有 GarageBand 音色。**

## 入口

桌面：

```text
Z1 演奏助手.app → <项目工作区>/dist/Z1 演奏助手.app
Z1 工程与说明   → <项目工作区>
```

更新方式：`./scripts/link-desktop.sh`。

官方软件可能记住旧目录的“最近打开”记录，不应通过修改隐私数据库或受保护沙盒来强行迁移。以后需要重载时，使用项目内 `projects/easy/Z1_EASY_Lights.eas`。

## 校验与回滚

- 素材、EASY、备份及 GarageBand 包逐文件 SHA-256 比较：`tests/reports/migration-integrity.json`。
- 新版文件结构校验：`tests/reports/migration-validation.json`。
- 新版离线音频检查：`tests/reports/migration-self-test.txt`。
- 本次没有为迁移强行退出用户正在使用的官方软件，也没有重做完整硬件逐格验收。

回滚时先退出助手，将桌面两条符号链接改回旧 Music 目录；旧应用和工程仍在原位置。不要用迁移前源码覆盖新主目录而不保留差异。

迁移当时未执行 `git init`、提交、推送或任何对外发布；本记录为历史快照。后续源码发布范围以当前README为准，本地工程/备份不随源码仓库分发。
