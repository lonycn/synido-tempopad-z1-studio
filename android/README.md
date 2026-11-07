# Z1 Studio Android · 0.3.1

已在华为DBY-W09（HarmonyOS4.2 / Android12 API31）原位安装，包名`local.orange.z1androidtest`，versionName0.3.1/code4。保留原签名密钥，更新前核对实际已装0.2证书，没有卸载旧App。父会话重新构建/复测后安装；设备显示64WAV和FluidR3/AAudio均就绪，三模式界面及屏幕试听已检查。

**实体DRUM/KEY音色、真实力度、踏板、菱形灯效及长时间稳定性仍待用户验收。** 已安装/就绪/播放提交不等于已听见声音或测得延迟。

## 0.3.1小修

当前模式按钮显示青绿色背景、●和selected/activated状态，并单独显示“当前软件模式”。除启动时默认EASY初始化外，只按已接受的worker快照更新，不把尚未执行或失败的请求显示为成功。EASY/DRUM/KEY三个高亮及布局已在平板逐一切换检查。

音符超出当前布局或通道不受支持时，显示实际输入/力度与未演奏原因，不继续显示上次成功按键。接受通道的Note On在音频提交尝试后写本地诊断日志；日志不是声音成功证明，也不发送到设备/网络。没有改映射、引擎或RGB边界。用户已确认调整Octave后原DRUM偏移问题恢复。

独立sol复核无阻塞、父会话完整构建/复测通过，更新前重新拉取实际已装0.3 APK核对签名，未卸载。详见[0.3.1记录](../docs/REVIEW_ANDROID_V031.md)。2140项新增检查是纯标签/路由检查，不是2140次Android视觉或物理测试。

## 使用

| 软件 | 硬件 | 内容 |
|---|---|---|
| EASY | EASY | 64段原创WAV，菱形彩色扩散；真实敲垫可自动识别。 |
| DRUM | DRUM | FluidR3 GM采样鼓，64位置有路由，使用收到的真实Note力度。 |
| KEY | KEY | FluidR3采样钢琴，黑白键布局，按住/松开、CC64延音。 |

- **DRUM/KEY需要在实体和软件各选一次。** 软件不发硬件模式命令，不按重叠Note猜模式。真正EASY按下可以切回EASY，但不能覆盖更新的未执行手动意图。
- EASY私有消息`F0 i F7`没有力度字段；显示“无力度数据/固定音量”。内部100仅是触发增益参数，不是测量值或百分比。触屏/试听也固定；标准Note显示实际`N/127`。
- 主音量默认45%，独立于系统媒体音量。用扬声器/有线耳机测试，避免蓝牙干扰延迟判断。
- KEY52有效位置/49音高36–84/12停用格；重复C48/60/72同时高亮。首owner起音、末owner松音，物理/触屏/试听与踏板独立，不是每owner独立复音。
- DRUM保留GM36–81，82–99映射`[36,38,42,46,49,51,41,43,45,47,48,50,39,54,56,75,80,81]`；64格不是64种独立乐器。松键不切断鼓尾音，同音重击替换旧层。
- 停止声音、切模式、拔出MIDI、后台、失焦均清按键/延音和声音。CC120/123安全停止。回前台自动重连MIDI。
- 软件侧栏可滚动，底部有试听、停止、关于/离线许可、重新连接。装好音色后无需Mac或网络。

## 按键位置不对应 / 高音角落无声

先核对实体与软件是否同为DRUM或KEY，再检查实体Octave/Transpose。软件当前显示的是默认音区，不自动猜测八度或重写设备设置。说明书允许DRUM最高+2八度。

本机曾收到DRUM左上88、左下60、右下95，三处恰好都比默认64/36/71高24；右上推算123超出软件36–99布局。用户手动调整Octave后已确认恢复，**没有改映射表或恢复出厂**。这是该次偏移问题的确认，不代表所有64位置/全部音区已验收。

默认DRUM四角：左上64、右上99、左下36、右下71。若多个位置都相差同一12的倍数，先核对八度；不要只凭单个Note自动猜模式或将RGB索引修改为另一套布局。具体数据见`../tests/reports/android-v031-{corner-observation,octave-user-acceptance}.json`。

## 灯光边界

菱形使用`(|dx|+|dy|)/sqrt(2)`，仍是1150ms、12波纹、约20fps模型、0–100七位颜色；归一化保证角落到对角角落在淡出前可达。屏幕和实体共用模型。

唯一物理输出仍是私有`MidiLighting.EasyRgbTransport.send(RgbFrame)`生成的已核实259字节`F0 65 [index R7 G7 B7]*64 F7`，index=PadProtocol.drum(cell)-36。没有模式/固件/配置/HID/USER写入。

只匹配唯一USB VID10467/PID4101，同一MidiDevice的收发端口。真实EASY输入才授权；屏幕/开关/普通Note不能授权。所有通道Note撤销RGB，因此**DRUM/KEY真实力度不能据此开启EASY硬件灯效**。进程级至少100ms尝试限频、会话lease交接、terminal黑屏锁存、最多3次连续失败和约300ms异步清理保持。物理拔线无法保证黑屏，最近EASY不是实时模式查询。

## 原生音源与许可

- `assets/FluidR3_GM.sf2`：148398306字节完整采样库，MIT字体数据；不是Apple DLS、GeneralUser或正弦替代钢琴。
- `vendor/tinysoundfont/`：固定commit853a0a171759f1ddba0de1442133a75912bbeffa，MIT引擎。
- `assets/licenses/instrument-sources.json`记录URL/字节数/SHA256/许可。完整MIT及NDK runtime notices随APK打包并可从“关于”离线查看。Debian包仅提取字体/copyright，未执行/安装或打包其GPL维护脚本。没有作超出原notice的溯源担保。预发布APK经用户授权公开，包含字体及完整MIT/NDK notices；原始字体和私钥不提交Git历史。
- NDK23.1.7779620，arm64 API26 JNI/AAudio，48kHz双声道float，低延迟模式只是请求，不是实测毫秒数。TSF不支持modulators/reverb/chorus；128是分层声部上限，不等于128用户音符。
- `Synth`在回调线程拥有渲染/声部状态，预分配声部，256槽SPSC队列，溢出panic代次淘汰旧事件，输出有限并限制±0.98。回调不分配/加锁/读盘/开关流；loader异步加载和销毁，旧loader结果不复活已销毁Activity。

## 限制与内存

- AAudio输出路由错误会静音并拒绝后续native音符，正常状态刷新/回前台/演奏会提示重开App。**不自动恢复音频路由**，EASY仍独立可用；同一Activity后台保留静音原生流，无后台服务。
- APK约147.5MiB（155MB）；首次私有缓存另需约148MB。目标平板安装后一次快照PSS约387MiB/RSS约470MiB，**不是峰值或上限**。host单engine约302MB，双engine测试约602MB；Activity交接可能短暂共存，Android长时间/OOM仍待验收。
- 声学延迟、音色、踏板/长按听感、全64格、强密集和弦、长期供电/温度、真实热插拔/焦点/AAudio断路仍需实测。
- 无网络、录音、存储、麦克风等权限。仅本地测试签名，非官方或商店发行版；其他设备未验收。

## 构建与记录

Git源码不含大字体库、私钥、安装包或设备报告；可安装APK另见[预发布下载](https://github.com/lonycn/synido-tempopad-z1-studio/releases/tag/v2.1-android-0.3.1)，其中包含合法字体及完整notice，私钥不发布。自行构建时首次先运行`python3 android/fetch_soundfont.py`，联网获取固定Debian包、验证包与字体大小/SHA256后仅提取SF2，不执行/安装包。也可用`--archive /path/to/package.deb`离线准备。此准备步骤不属于App联网行为，之后构建/演奏均可离线。

项目根执行`./android/build.sh`。使用本机JDK/SDK35/NDK23.1，无Gradle/Maven下载，不访问ADB或真实MIDI。`python3 android/verify_instruments.py [APK路径]`检查固定输入/包内容。不要复制开发副本密钥，保留`local/test.keystore`用于原位更新。

构建执行原协议/随机拆包/灯光/生命周期/控制器替身，以及Instruments536、UiPublisher64、Diamond76477检查和真实SF2 native49钢琴音高/64鼓输入测试。父会话另重跑ASan/UBSan及特定空闲字段poison测试；LeakSanitizer/MSan本机不支持，不把它称为完整未初始化或泄漏证明。

初审发现两个P1，纠正后新sol独立审查无阻塞；详见[0.3审查与安装](../docs/REVIEW_ANDROID_V03.md)。原始代理报告保持历史状态，父会话新报告记录后续集成安装，不用修改旧快照。

- `../tests/reports/android-v03-parent-build.txt`
- `../tests/reports/android-v03-parent-sanitized.txt`
- `../tests/reports/android-v03-package-verification.json`
- `../tests/reports/android-v03-install.json`
- `../tests/reports/android-v03-device-memory.txt`
- `../tests/reports/android-v03-agent/`：原始源/许可/测试证据。
- `../backups/android-v02-before-diamond-20261107-221840/`：0.2源码/APK/历史记录。

历史0.1有声/位置/跟手反馈及0.2正面反馈不等于0.3全部验收。继续维护前读AGENTS.md。
