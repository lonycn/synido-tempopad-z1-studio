# Android 0.2 审查、纠正与集成

## 当前状态

**已集成、原位安装到平板，尚未验收0.2实体灯光。**

父会话安装前读取实际已装0.1 APK并比对签名身份，以`adb install --no-incremental -r`更新，未卸载旧App。设备版本0.2/code2、前台启动、64/64音频加载、扩散开关开启已目视/系统确认；无线ADB在受信局域网恢复连接（公开文档省略设备地址）。安装期间平板接Mac而非Z-1，未发出真实RGB。记录：`tests/reports/android-v02-install.json`。

用户已确认0.1在华为DBY-W09上实际有声音、位置对应、听感跟手；没有测得声学延迟数值。该结果不能替代0.2硬件验收。

## 分工与隔离

- gpt-6.1 sol：EASY实体扩散与连接/生命周期实现。
- gpt-6.1 terra：独立协议fuzz与异常流测试。
- gpt-6.1 terra：后续GM/钢琴音源授权/架构研究；未引入素材。
- 新的gpt-6.1 sol：第一轮只读安全审查，给出BLOCK。
- 原sol实现代理：按审查修复、增加回归、原样纳入独立fuzz。
- 另一个新的gpt-6.1 sol：纠正后独立只读审查，结论NO BLOCKING FINDINGS。
- 父会话：主目录集成、完整重跑构建测试、原密钥签名与0.1证书对比、APK资源/权限/摘要检查。

子代理仅在独立目录工作，没有访问ADB或实体设备。开发副本源码最初因递归忽略local误漏Java包，已从只读主目录补齐并修正备份；主源码未丢失。副本签名密钥未集成。

## 初审拦下的三个P1

| 问题 | 纠正与回归 |
|---|---|
| Activity重建导致新控制器重置发送时钟，可能小于100ms | 进程级同步尝试限频器与USB会话lease；双控制器/双worker跨实例最终黑屏、新输入及旧回调测试；16个host线程竞争预约。 |
| 自然结束黑屏作为普通最新帧，可能被节流窗口内的新敲击覆盖 | 自然结束与显式clear同样锁存terminal黑屏，保留新波纹，黑屏成功后再限频恢复；1100/1150/1175/1200ms时序及失败重试回归。 |
| 接收端口设置失败触发立即关闭/重开循环 | setupFailed稳定错误门控，显式重连/相关拓扑变化才重试；null/throw/connect失败/异步空设备、5000ms有界尝试和worker仍执行回归。 |

独立二审逐项检查了生产接线及对应测试，不只是纯辅助类。其结论允许集成，不意味着真机灯效正确或没有其他潜在问题。

## 父会话重新执行的检查

```bash
./android/build.sh
python3 scripts/validate.py
(cd dist/android && shasum -a 256 -c Z1-Studio-Android-0.2-test.apk.sha256)
```

- 原ProtocolTest通过。
- ProtocolFuzzTest：20,084次检查，seed99167796544585。
- LightingTest：14,706次检查。
- LifecycleTest：3,010次检查。
- SendAttemptLimiterTest：6次检查，包含16个并发host线程。
- MidiLightingTest：229次控制器/替身检查。
- 测试计数是断言/检查数量，不等于独立硬件场景数量。
- APK版本0.2/code2，v2/v3签名通过，证书身份与保留的0.1 APK相同，64WAV，无声明权限，无DLS/SF2音源库。
- 主目录仍只有一个Android设备写入口，构造已核实的固定259字节RGB全帧；Mac现有资源/结构校验通过，Mac包未重新构建或修改。
- 编译只有Java8 source/target旧选项、Android MIDI旧API的非阻塞警告。

APK：`dist/android/Z1-Studio-Android-0.2-test.apk`。哈希和证书验证见下列JSON，不手抄易过期摘要。

## 证据路径

- `tests/reports/android-v02-review-initial.md`
- `tests/reports/android-v02-review-corrected.md`
- `tests/reports/android-v02-implementation.md`
- `tests/reports/android-v02-protocol-fuzz-report.md`
- `tests/reports/android-v02-integrated-build.txt`
- `tests/reports/android-v02-integration.json`
- `tests/reports/android-v02-package-verification.json`
- `tests/reports/android-v02-mac-regression.json`
- `tests/reports/android-instrument-research.md`（后续研究，不是素材分发批准）
- `backups/android-v01-before-v02-20261107-214302/`：0.1源码、APK、历史Android报告。

## 下一轮真机验收

1. 已完成：授权USB接回Mac，核对实际已装APK签名，原位更新0.2并恢复无线ADB，没有卸载旧App。
2. 待用户将平板重新通过OTG接Z-1；RGB测试会改变当前灯光，已告知。
3. 用实体EASY敲中心与角落，核对声音/位置仍对应，彩色环方向/颜色、约1.15秒淡出和最终黑屏。
4. 连敲与叠音是否仍跟手；停止声音、扩散开关、后台返回/退出清灯、重连及物理拔插。
5. 普通Note撤销RGB、焦点中断/恢复；可用条件下验证第二台同型号设备歧义关闭。
6. 记录用户目视与听感，未测项继续标为未验收；无线ADB在本轮结束后关闭。

物理拔线不能保证清灯，最近EASY消息不是当前模式查询。没有已核实的双向模式命令，不猜测。实际声学延迟、全部64格与长时间供电仍需测量或用户逐项确认。
