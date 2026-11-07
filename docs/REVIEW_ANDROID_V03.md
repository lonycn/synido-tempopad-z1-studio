# Android 0.3 · 审查、集成、安装

## 状态

已集成、由父会话重新构建/测试并用原密钥原位安装到华为DBY-W09；版本0.3/code3、MIDI连接、64WAV及FluidR3/AAudio就绪，DRUM/KEY布局与屏幕试听已检查。**真实力度、音色、踏板、菱形灯效和新版长时间稳定性仍待用户确认。** 未测声学延迟。

## 分工与两轮审查

- sol：采样GM鼓/钢琴、JNI/AAudio、布局/力度/owner/踏板与生命周期。
- terra：归一化Manhattan菱形、76477项检查及旧圆形负对照；合并时只复制Diffusion.java与DiamondTest.java。
- 新sol独立初审：BLOCK，发现原生空闲声部未初始化读取，以及旧模式快照错贴最新请求代次/合并器丢更新两个P1。
- 原sol：仅在副本修复，增加真实生产路径回归；新sol二审：NO BLOCKING FINDINGS，允许源代码集成，不是硬件验收。
- 父会话：将源码/音色/许可集成主目录，保留原密钥；没有复制副本密钥、Debian获取缓存或编译产物。重新执行完整构建和ASan/UBSan，再核对实际已装0.2 APK证书后更新。所有子代理均未访问设备。

## 修复

1. Native重触发扫描先检查playingPreset!=-1，再读channel/key。固定vendor头未修改。新增首音/16次重击/清音后鼓声回归；host仅测试用ASan poison覆盖实际生产扫描，删除guard的负对照失败。ASan/UBSan不是通用未初始化检测器。
2. MainActivity使用生产UiPublisher，快照标记worker已应用代次，不是最新请求代次；dirty revision与交付/拒绝后的ack确保待发期间的新状态不会丢失。64项确定性调度检查覆盖旧KEY/新DRUM交错、未执行新意图、同代次自动EASY及批量变更，相关负对照均被拒绝。

## 父会话重新执行

```bash
./android/build.sh
clang++ -std=c++17 -O1 -g -DSYNTH_TEST_ALLOC -DSYNTH_TEST_POISON_INACTIVE \
  -fsanitize=address,undefined -fno-omit-frame-pointer -pthread \
  android/native/Synth.cpp android/native/SynthTest.cpp -o android/build/native/synth-test-sanitized
ASAN_OPTIONS=detect_leaks=0 android/build/native/synth-test-sanitized android/assets/FluidR3_GM.sf2
python3 scripts/validate.py
```

均通过。原协议/拆包/灯光/生命周期/并发/控制器保持通过；新增模式/owner/真实力度536检查、发布器64检查、菱形76477检查及真实音色49钢琴音高/64鼓输入，力度高低RMS差异、release/sustain/panic、队列溢出/并发、有限PCM及对象生命周期通过。计数是断言/检查，不是相同数量独立硬件场景。LeakSanitizer及MSan未支持，不能宣称泄漏/全部初始化已证明。

APK147.5MiB，版本/证书身份、签名、对齐、精确SF2/notice、64WAV、唯一arm64 ABI和无权限声明通过。源码和字体许可路径、摘要见`android/assets/licenses/instrument-sources.json`。MIT适用于引擎/字体；未打包Debian GPL维护脚本或Apple/GeneralUser音色。完整credits可离线阅读，未公开发布。

## 安装及设备快照

无线ADB仍获授权。安装前拉取用户实际已装0.2 APK核对证书，返回键让旧Activity进入后台请求清音清灯；紧接的preinstall快照可能仍在约300ms异步清理窗口（因此记录端口仍开），不能据此宣称物理黑屏已看见。随后正常`adb install --no-incremental -r`更新，无卸载、无root。

设备启动后原生音源和AAudio ready。检查了EASY、DRUM、KEY视图，屏幕分别触发鼓/钢琴36音，没有“音频未提交”提示；这不是实听证据。已让软件停在DRUM，请用户匹配硬件并轻重敲同一格。截图位于docs/images/android-v03-{easy,drum,key}.png。

一次实际平板内存快照PSS396068KiB、RSS480800KiB，约387/470MiB，非峰值。完整字体148398306字节另存私有缓存；host单engine约302MB、双engine约602MB均非Android上限。AAudio输出路由错误采用已批准的fail-closed+重开App，不自动热恢复；EASY独立可用。

## 证据

- `tests/reports/android-v03-review-initial.md`、`android-v03-review-corrected.md`
- `tests/reports/android-v03-integration.json`：按文件SHA256复制及密钥保护。
- `tests/reports/android-v03-parent-build.txt`、`android-v03-parent-sanitized.txt`
- `tests/reports/android-v03-package-verification.json`、`android-v03-install.json`
- `tests/reports/android-v03-device-memory.txt`、`android-v03-mac-regression.json`
- `tests/reports/android-v03-agent/`保留原报告（其中“待复审/未安装”为原时间快照，不是当前状态）。

## 仍待验收

DRUM同格轻重敲的原始N/127、实际响度/音色；KEY真实力度、白黑键/重复C、长按松开与踏板；EASY真实菱形位置/颜色/淡出/最终黑屏。另有密集演奏、全64格、后台/焦点/退出/热插拔、AAudio断路、Activity切换RAM/OOM、长时间功耗/温度。不要以host或“ready”当作通过。

所有硬件输出仍只允许同一唯一USB设备、真实EASY授权下的259字节固定RGB，100ms尝试限频和terminal黑屏保持；DRUM/KEY真实力度不授权该灯光。没有双向硬件模式控制，仍须手动匹配。
