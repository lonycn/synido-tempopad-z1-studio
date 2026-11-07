# 维护、构建与发布

## Android 0.3

先读`android/AGENTS.md`、`android/README.md`。项目根`./android/build.sh`仅离线构建/测试，不连接设备；需本机JDK、SDK35/NDK23.1.7779620。固定采样库/TSF/notice必须通过`android/verify_instruments.py`摘要验证，不能无评估替换音色/引擎或加入网络下载；APK携带完整离线许可。

保留原`android/local/test.keystore`，禁止复制lane密钥或以卸载绕过签名。安装前核对设备上实际旧APK证书；0.3更新已由父会话执行。备份时只排除顶层build/local，不可递归丢失src/local/源码。原0.2源码/APK及历史报告在`backups/android-v02-before-diamond-20261107-221840/`。

音源/UI改动要跑完整Java/controller/diamond/publisher测试、实际SF2 native host与ASan/UBSan；这些不替代Android听感/力度/延迟/RAM。AAudio输出路由失效须重开App，不做自动重试；EASY独立可用。测试后关闭无线ADB并核验5555拒绝连接；演奏无需网络/Mac。当前验收状态及报告路径见[0.3审查安装](REVIEW_ANDROID_V03.md)。

## Mac环境

- macOS；当前仅在此 Mac 上验证。
- 构建：Xcode Command Line Tools（`xcode-select --install` 由用户在确有需要时执行）。
- 运行：系统 DLS 音源，Synido 官方软件用于 EASY；GarageBand 仅为备选工程宿主。
- Python 3：工程校验使用标准库。
- NumPy：仅重新生成音频时需要；普通运行、构建和校验不需要。
- AX/UI 工具另依赖 PyObjC 的 Quartz、ApplicationServices、AppKit，并需用户授权辅助功能／屏幕录制。不要为运行演奏助手而要求这些权限。

## 图标与打包

原创图标的矢量绘制代码：`scripts/generate_icon.swift`。运行 `swift scripts/generate_icon.swift` 生成：

- `assets/branding/Z1Studio-1024.png`：1024px预览源图。
- `app/Resources/AppIcon.icns`：16–1024px多分辨率图标，构建复制进应用Resources。
- `generated/branding/Z1Studio.iconset/`：可再生中间产物，不手改。

Info.plist用CFBundleIconFile=AppIcon引用，主程序也从包中加载以刷新Dock。不要在签名后替换包内图标；重新构建再签名。更新后如Finder仍缓存旧显示，可以先重新打开应用，避免强杀Finder/Dock。

退出助手后运行 `./scripts/package.sh`，会构建、校验包内版本和图标、运行离线测试、生成ZIP和SHA-256、解压后再次核验签名/可执行文件/图标并运行离线测试。打包不会打开演奏窗口或输出实体MIDI。

## 修改助手

1. 按职责修改 `src/*.swift`（入口为 `Z1Player.swift` 的 @main）；应用元数据修改 `app/Info.plist`。v2 已拆分音频、MIDI、模型和面板文件，构建时必须编译全部 Swift 文件。
2. 退出正在运行的助手，再执行：

   ```bash
   ./scripts/build.sh
   python3 scripts/validate.py
   "dist/Z1 演奏助手.app/Contents/MacOS/Z1Player" --self-test
   ```

3. 手动测试 DRUM／KEY／内置 EASY、热插拔、停止所有声音、鼠标拖动与界面同步。EASY 不再打开官方软件；其硬件专用输入还需实机验收。
4. 在 `CHANGELOG.md` 记录行为变化和测试范围。
5. 如果项目路径变化，运行 `./scripts/link-desktop.sh` 更新桌面链接。

构建会将原始 WAV 与 PAD_MAP 放进应用 Resources，必须在签名前完成；不要只替换可执行文件而忘记素材。音频控制运行在独立串行队列，UI 变更只能回到主线程。\n\n构建脚本从任意工作目录调用均可；编译临时目录位于 `dist/.build.*`，成功编译并签名后才整体替换最终 `.app`（同文件系统重命名切换，异常时恢复旧包），不再叠加复制到旧目录，避免残留文件与误导的外层日期。窗口和顶部显示Info.plist版本及Build号，可用于确认运行副本。本机构建架构由 uname -m 选择；最低系统目标从 Info.plist 的 LSMinimumSystemVersion 读取并显式传给 swiftc，避免默认跟随新SDK而与应用声明不一致。跨架构、通用包或旧版macOS的实际兼容性仍需单独验证。

重新打包产物位于 dist：完整.app、Z1-Studio-2.1-macOS-arm64.zip、SHA-256旁路校验文件和打包说明。ZIP使用ditto保留应用包与macOS资源，解压后再次校验签名并运行--self-test；本次记录在tests/reports/repackage-*。

源文件中的 AVAudioEngine `connect` 在当前 SDK 可能产生弃用警告，目前可构建，不应通过盲目替换 API 破坏本机兼容性。

## 重新生成入门音频

```bash
python3 scripts/generate_audio.py
```

默认输出 `generated/audio/`，**不会覆盖保留的原始素材**。使用确定性的随机种子生成 64 个各 1 秒的声音，同时输出 PAD_MAP.txt。

如果刻意要替换主素材：

```bash
python3 scripts/generate_audio.py --output-dir assets/audio
```

覆盖前请先备份；同步更新 `docs/PAD_MAP.txt`，并在官方软件中重新切片、分配、保存 EASY 工程。修改 WAV **不会自动更新**已导出的 `.eas`。

## 编辑保留的官方 EASY 声光工程\n\nv2 助手内置 EASY 播放的是保留 WAV 的64个等长片段，不读取 `.eas`。修改以下官方工程不会同步更改助手声音；修改 WAV 也不会自动更新官方工程。两者应分开验收。

1. 退出演奏助手和 GarageBand，打开官方 Synido 软件，选择 Easy。
2. 文件 → 打开 `projects/easy/Z1_EASY_Lights.eas`。
3. 修改前将该文件复制到 `backups/easy/`，使用清晰的新文件名。
4. 改声音：音频编辑或快速切片；原始 WAV 是 64 秒，均分为 64 片。
5. 改灯光：灯光绘制 → 简单图案，可选扩散／颜色／速度，保存灯光并分配给 PAD。
6. 文件 → 另存为，把正式版本保存回 `projects/easy/`。
7. 运行 `python3 scripts/validate.py`，并在官方软件内重开工程。
8. 实际敲击设备检查音频与灯光；说明书中明确需要官方软件和硬件均在 EASY。

当前 `.eas` 是加密容器；只通过官方 UI 编辑，不依赖未公开工程格式或密码。若软件仍记住旧 Music 目录，主动选择项目内的新路径。

## GarageBand 工程

- `projects/garageband/Z1_KEY_Piano.band`：Steinway 大钢琴。
- `projects/garageband/Z1_DRUM_Starter.band`：SoCal 标准鼓组；并不保证 64 个 DRUM 音符都有采样。

`.band` 是目录包，备份／复制必须复制整个目录。不要只复制 `ProjectData`。Apple 音色没有打包进项目，跨机器要安装对应音色。更改工程后正常保存，再做文件备份。

## 桌面入口

`./scripts/link-desktop.sh` 维护两条符号链接：

- `~/Desktop/Z1 演奏助手.app` → `dist/Z1 演奏助手.app`
- `~/Desktop/Z1 工程与说明` → 本项目根目录

脚本拒绝覆盖同名真实文件或文件夹。不要删除用户自己创建的桌面内容。

## v2.1 实体灯光与同步维护

协议证据与发送边界见 `references/PROTOCOL_V21.md`。新灯光代码在 `Effects.swift`、`Lighting.swift`，唯一硬件发送入口 `MIDIInput.sendRGB`。不要把它扩展成任意SysEx发送器。

- 259字节官方RGB全帧、实体四分区索引，不是PAD行列编号。
- 任意修改限频或清灯前，执行模型测试：必须覆盖末尾黑屏、失败重试、clear去重和模式退出。
- 真实输入与唯一输出必须属于同一物理设备；多设备不按枚举顺序猜。
- 手动选择的代次必须在入队前推进，旧自动模式/UI更新不能覆盖新选择。
- 完整模式双向同步尚无协议；不得通过猜测音符区分DRUM/KEY，也不借用固件命令尝试切模式。
- 实机测试会改变当前RGB灯光，先说明；颜色、扩散几何和清灯要人工验收。
- `backups/v2/`保留v2.0应用、源码及Info.plist，需要回滚时整个应用包替换，不拼接资源和签名。

## 诊断工具

`tools/diagnostics/` 保留了本次调试的 UI 检查、点击／按键、截图、PDF OCR 工具，便于后续调查。它们**不是稳定自动化安装器，也不应加入应用启动流程**。

- `capture_input.swift`：只读USB输入监听，不创建输出端口。用户确认测试时运行 `swift tools/diagnostics/capture_input.swift 90`，再按实体模式键/指定垫，配合操作时间核查。静默但没有用户操作记录，不能证明模式键绝不发消息。
- `synido_ui.py`：PyObjC AX 树及 Quartz 输入。
- `z1_ax.py`：AX 查找、按钮操作、截图辅助，导入同目录的 `synido_ui.py`。
- `z1_ocr.swift`：使用 PDFKit/Vision 读取 PDF 并 OCR；示例：

  ```bash
  swift tools/diagnostics/z1_ocr.swift references/vendor/Z1_User_Guide_EN_ZH_V1.0.pdf
  ```

UI 坐标依赖分辨率、窗口位置和应用版本。运行前应检查当前窗口，避免盲目点击；不要在有人正在演奏时运行虚拟 MIDI 或关闭宿主测试。

## 数据与回滚

- `backups/v1/` 保留升级前可用应用及单文件源码；需要回滚时先退出助手，再复制完整 `.app` 到 dist，避免拼接不同版本的资源或签名。\n- `~/Music/Synido Z1 Starter` 保留了迁移前快照；不要继续在两处并行维护。
- 初版无灯光 `.eas` 在 `backups/easy/`，不要误当成推荐工程。
- `tests/reports/initial-validation.json` 是初始记录，不覆盖；新测试另存新报告。
- 早期维护阶段未初始化Git或对外发布；后续已按用户明确授权建立源码仓库与GitHub预发布。Git只提交公开源码/必要素材；安装包经Releases独立提供，私钥、设备报告与备份继续排除。
- 官方 PDF 与 macOS/Apple 音源有各自权利归属；保留来源，不把厂商素材标记为本项目原创。
