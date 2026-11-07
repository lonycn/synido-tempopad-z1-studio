# 测试与验收（Mac v2.1 / Android 0.3.1）

## Android 0.3.1小修

父会话完整构建/回归通过；新增ModeFeedback2140纯标签/路由检查，独立sol复核无阻塞并验证384种合法mode/pitch路由与旧版等价。原位更新前核对实际已装0.3证书，设备0.3.1/code4确认；截图逐一验证EASY/DRUM/KEY青绿●active及当前模式文字，最终留DRUM。

用户已确认调整Octave后DRUM位置/无声投诉恢复；没有软件写设备配置。不是全64格/其他音区验收，也未测得右上偏移值123（仅推算）。失败音源请求/超范围诊断为源码与纯函数检查，尚未做Android故障注入。详见[0.3.1记录](REVIEW_ANDROID_V031.md)、`tests/reports/android-v031-*`。

## Android 0.3

父会话主目录执行`./android/build.sh`及真实SF2的ASan/UBSan检查通过：原0.2套件不减；新增Instruments536、UiPublisher64、Diamond76477检查与真实采样49钢琴音高/64鼓输入非零、力度差异、松音/延音/panic、溢出/并发、有限PCM、对象寿命。host负对照验证原生guard与UI代次/dirty regressions确实敏感；ASan/UBSan不是通用未初始化/泄漏证明。

0.3已用原签名原位安装；实际旧包证书、资源/许可/ABI/无权限已检查，设备64WAV和FluidR3/AAudio就绪，DRUM/KEY屏幕试听与布局已检查。详见[0.3审查安装](REVIEW_ANDROID_V03.md)、`tests/reports/android-v03-*`。

待用户：实体DRUM同格轻重、KEY音色/长按松开/力度/CC64、EASY菱形方向/淡出/尾黑、全部位置及长期/热插拔/后台/音频路由。一次Android内存快照约387MiB PSS不是上限，不能以host或ready替代听感和实机稳定性。

## Android 0.2

`./android/build.sh`在本机构建并执行全部host检查，不访问ADB或实体设备。包括原协议测试、独立fuzz20,084、灯光14,706、生命周期3,010、并发限频6、真实控制器配Android替身229项检查；断言数不代表同等数量的独立硬件场景。必须检查APK签名身份与已装0.1一致，禁止通过卸载旧版来绕过签名不匹配。

主目录结果：`tests/reports/android-v02-integrated-build.txt`、`android-v02-package-verification.json`；初审、修复及二审见[Android审查记录](REVIEW_ANDROID_V02.md)。0.1用户确认有声、位置对、听感跟手，不能当作0.2验收。

0.2实机待确认（先告知会改变灯光）：EASY中心/角落/逐格扩散方向、颜色、自然尾黑；连敲是否仍跟手；停止声音/扩散开关/后台返回/退出清灯；USB拔插/重新连接；普通Note输入是否撤销RGB。拔线后不能保证黑屏，不能发送猜测的模式/配置/固件命令。没有设备或用户目视反馈时，记录为未验收，不用host替身代替。

## Mac自动检查说明

## 自动检查（不打开UI、不发硬件MIDI）

```bash
python3 scripts/validate.py
codesign --verify --deep --strict "dist/Z1 演奏助手.app"
"dist/Z1 演奏助手.app/Contents/MacOS/Z1Player" --model-test
"dist/Z1 演奏助手.app/Contents/MacOS/Z1Player" --self-test
```

纯模型测试：DRUM64与PAD64双射；KEY52位置/49音高；分端点隔离、running status、实时字节、跨包EASY、超长SysEx丢弃；单设备选择和手动命令代次。

灯光模型/Mock测试：扩散中心和环传播、12波纹上限、全黑结束；259字节帧与7位分量、实体四分区索引；严格100ms发送间隔、clear去重、尾部黑屏节流后交付、失败最多3次、输出确认门控。不创建MIDI输出端口。

独立最终审查额外验证了真实Timer驱动的Mock尾帧、模式退出清灯、正常退出约103ms、失败退出最多3次约209ms。仍不是实体设备验收。

离线音频测试：逐个渲染64个鼓输入、64个EASY样本、钢琴C4，检查非静音；渲染失败不忽略。额外检查共享音高所有权、断开Panic、全部64组EASY专用按下／松开、偏移不污染私有实体索引，以及32声部峰值限制。

这些是 smoke / 回归检查，不代表全部音高、力度层、音色听感或完整实时延迟测量。

`validate.py` 检查推荐EAS含64音频+64灯光、旧版无灯光工程、原始64秒PCM、PAD表和GarageBand包。EAS载荷加密，仅验证容器，不解密。

## GUI 检查

- 三种模式显示正确，不打开官方软件。
- 64个实体按钮具有无障碍标签与动作；KEY12格停用。
- 鼠标按下立即播放，拖动切换格，拖到间隙/窗口外松开不挂音。
- 模式切换/Panic 清除声音、屏幕按下状态和测试音所有权。
- 屏幕失焦释放鼠标，不中断硬件输入。
- 主音量与音频状态独立于MIDI连接状态。
- 硬件敲击更新屏幕、次数和力度；重复C明确标亮多格。

## 真实硬件仍需验收

- DRUM每个分区、KEY每个八度及重复C的实际对应。
- 原厂EASY是否在没有官方软件时直接输出三字节消息；若否，DRUM备用输入是否顺畅。
- 拔插USB、延音踏板断开、硬件移调/八度偏移。
- 32声部密集演奏的实际音量、听感与延迟；不能把离线峰值测试当作完整实时测量。
- 实体RGB：确认扩散位置/颜色/淡出，多重叠加、Panic/退出清灯；不要把MIDISend成功当作已经看见硬件效果。
- 模式限制：仅首次实体EASY敲垫自动识别，不能声称按模式键立即识别或软件反向控制硬件。

可选 `swift tests/midi_smoke.swift` 会创建虚拟输入源并发送4次标准音符，不是纯单元测试。只在人未演奏时运行；虚拟源名含Synido，助手会短暂连接。

## 记录

- `tests/reports/v21-self-test.txt`、`v21-validation.json`、`v21-ui-check.json`：当前版本。
- `references/PROTOCOL_V21.md`：两个独立协议核查与同步边界。
- 下列v2和迁移记录为历史，不覆盖。

- `tests/reports/v2-self-test.txt`：新版离线检查。
- `tests/reports/v2-validation.json`：新版资产结构。
- `tests/reports/v2-ui-check.json`：GUI检查记录（如已执行）。
- `migration-*` 和 `initial-validation.json`：历史迁移／v1记录，不覆盖，不当作当前完整验收。

音频生成可复现检查见维护文档。保留素材可能包含超高频合成音及高于127的音高标签；它们是波形生成标签，不是可输出的MIDI音符，v2未重新生成或改变素材。
