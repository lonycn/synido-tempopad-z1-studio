# Mac v2.1 / Android 0.3 架构

## Android 0.3

USB MIDI → MidiLighting单worker → Instruments布局/owner/模式 → EASY原SoundPool64WAV，或NativeInstrument有限命令队列 → AAudio回调独占Synth/TSF → 采样鼓/钢琴。异步loader负责SF2加载和AAudio开关/销毁，回调不分配/加锁/I/O；生命周期panic代次丢弃旧事件。

worker-applied模式代次/dirty revision → UiPublisher capture/delivery/ack → MainActivity与黑白/鼓组界面；请求代次不冒充实际已应用代次。Diffusion归一化Manhattan模型由屏幕和原有RGB控制器共用，唯一同设备/真实EASY授权/≥100ms发送及terminal黑屏边界不变。

完整边界、音源许可、内存和route错误重开策略见[Android说明](../android/README.md)，审查与父会话重测见[0.3记录](REVIEW_ANDROID_V03.md)。以下为Mac架构，两个平台不共享Apple DLS素材或native引擎。

## Mac v2.1

```text
实体 Z-1 → CoreMIDI → 分端点字节解析器 ─┐
鼠标 / 无障碍按钮 / 试听命令 ──────────┤
                                     ↓ 串行 performance 队列
                      布局映射 / 模式 / 按键所有权
                         ├─ DRUM → GM 鼓补全
                         ├─ KEY  → GM 钢琴
                         └─ EASY → 64 PCM 切片 / 32 播放声部
                                     ↓
                    AVAudioEngine → Apple PeakLimiter → 音频输出
                                     ↓ 异步主线程事件
                          AppKit 8×8 面板 / 状态 / 力度
```

## 模块

- `src/Z1Player.swift`：@main，窗口与生命周期，用户命令，队列入口。
- `src/Surface.swift`：圆角金属色面板，64 个可访问 NSButton，模式键，点击／拖动／松开，按下和余辉动画；无动画时停止刷新计时器。
- `src/Models.swift`：Mode、物理布局、声音路由、输入所有权和纯模型测试。
- `src/MIDI.swift`：CoreMIDI输入／输出、设备配对与分端点解析。仅暴露sendRGB，不能发送任意设备命令；多台同名设备时灯光fail closed。通知后只处理新增／移除端点。
- `src/Effects.swift`：纯扩散模型，12波纹上限、单调时钟、7位颜色。
- `src/Lighting.swift`：20fps逻辑动画、100ms严格发送间隔；rendered/sent/pending三态，只保留最新帧，包含黑屏的有限重试；退出清理最多短等约300ms。
- `src/Audio.swift`：Sampler、PCM 切片、32 声部一次性采样、全局停止、峰值限制和离线测试。

解析／音频控制仅在串行队列运行；UI 不等待音频，音频不等待绘制。CoreMIDI 回调复制包数据后立即入队。按端点传递来源，不混用运行状态。

## 布局

UI cell 为从上至下、从左至右。PAD 编号为从下至上，每排8格。DRUM 在四个4×4区内按不同序号布局，不能统一用 note−36 当屏幕格号。

精确公式和证据见 `references/PROTOCOL.md`。默认映射的纯测试覆盖64个 DRUM 和PAD位置，KEY有52个有效位置／49个不同音高。重复边界 C 同时高亮，不能声称精准定位。

右侧 −24…+24 半音是硬件布局校准：让输入 MIDI 与当前实物移调对应，KEY 声音仍弹收到的原始音符。EASY 私有输入自带实体索引，不受该偏移影响。

## 音源

- 系统 DLS：`/System/Library/Components/CoreAudio.component/Contents/Resources/gs_instruments.dls`。
- 钢琴：program 0 / MSB121 / LSB0；鼓：program0 / MSB120 / LSB0。
- DRUM 36–81 不变；82–99 映射到 `[36,38,42,46,49,51,41,43,45,47,48,50,39,54,56,75,80,81]`。范围外按该列表取模兜底。
- EASY 从应用 Resources 解码保留的 WAV，一次读取64个各44100帧的缓冲区，演奏时不读取磁盘、不解密 EAS。
- 32个 AVAudioPlayerNode 循环分配，允许相同格重叠；超限替换最早声部。力度映射声部增益。
- 主音量默认0.65，输出经 Apple PeakLimiter 限制，避免大量叠音产生数字削波。不是响度归一化或完整母带处理。

## 所有权与停止

硬件 owner 含端点、通道和音符；鼠标／试听有不同 owner。松开某个 owner 时，若同一 Sampler 输出仍有其他 owner，不能提前停止音符。相同音高再次触发仍采用 Sampler 的重触发行为，并非独立音高复音。

模式切换／Panic／退出：释放所有按键、延音、CC120/123，以及采样声部。端点断开采用全局 Panic，避免延音踏板或遗漏 Note Off 导致挂音；这也会停止鼠标正在播放的声音。

KEY 松键为 gated；EASY 松键仅释放屏幕按下态，不截断一次性音频。屏幕窗口失焦会释放鼠标，不主动中断硬件演奏。

## EASY 及灯光

助手直接支持64格入门音频，不再启动官方软件。接收两条路径：

1. 标准 MIDI：DRUM/CH1 或 USER/CH3。
2. 只读专用输入：F0 i F7 按下，F0 (i+64) F7 松开，i=DRUM音符−36。该行为来自官方二进制静态分析，仍需实机验收。

v2.1增加已核实的实体RGB输出：F0 65 [index R7 G7 B7]*64 F7。259字节全帧，索引为Layout.drum(cell)−36，输入／输出必须属于同一物理设备。只在真实EASY输入确认后启用，标准Note流量或断线撤销识别。没有猜测模式切换／初始化命令。

渲染状态与成功发送状态独立，避免末尾黑屏被100ms节流丢弃。clear去重且不绕过限频；失败最多3次后关闭输出。硬件发送约10fps，屏幕20fps，空闲停止计时器。软件切模式和Panic清灯；退出在dispose MIDI前有限等待交付清灯。

EASY首次真实敲垫可在发声前自动切软件模式。ModeIntent以锁保护手动命令代次，自动UI发布和模式相关帧检查代次；未应用的手动命令优先，旧自动事件不可覆盖新选择。

硬件模式键没有可确认报告，DRUM/KEY不能可靠自动识别；软件切硬件模式也无可确认命令。只按模式键而未敲新垫时，旧EASY识别可能暂时过期，灯光不是实时模式查询。详见references/PROTOCOL_V21.md。原.eAS工程未修改。

## 路径、互斥和限制

应用标准位置 `<项目>/dist/*.app`。项目入口从 Bundle 推导，开发时可用 Z1_PROJECT_ROOT。EASY 音频优先使用应用内资源；系统音源仍依赖 macOS。

启动时请求正常退出官方软件与 GarageBand，等待约8秒；未保存内容阻止退出时提示并退出助手，不强杀。

当前不处理 MIDI2 UMP、ProgramChange、PitchBend，仅在首次专用敲垫时自动识别EASY，不自动识别 DRUM/KEY 硬件模式；未完整实现音频输出热切换／睡眠恢复／音源失败重试。需要切换音频设备时若无声，先 Panic 后重新打开助手。输入时间戳未用于未来时刻调度；界面极高频更新尚未做专门合并。窗口采用固定布局，非响应式全屏面板。

本地 ad-hoc 签名、当前架构编译，未公证。跨机发布及无障碍体验需进一步验收。
