# Android 0.3.1 · active模式与超音区诊断

## 结果

0.3.1/code4已原位安装到华为DBY-W09，原签名证书与实际已装0.3 APK一致，无卸载。父会话在设备逐一切换EASY/DRUM/KEY，三个截图均显示唯一青绿●按钮及同名当前模式文字，64WAV/FluidR3/AAudio就绪，最后停在DRUM。未改用户Octave、设备配置、映射、原生音频或RGB边界。

## 八度问题与用户确认

用户在按要求切DRUM后报告：左上88、左下60、右下95；右上无声/没有新显示。三个值均比默认64/36/71高24。说明书Octave段明确DRUM允许最高+2八度。右上默认99+24=123是**推算，不是测得**；旧软件只路由36–99，故这种输入会无声且旧版保留上次成功显示。

父会话请用户只选Octave再降低两次，未发送设备命令。用户回复：“是的。你说的对，我调整了octave 可以了。”记录为该次偏移/无声投诉已恢复，不扩展成全部64格/其他音区或全部模式验收。

## 变更与门禁

- MainActivity：按钮selected/activated、青绿色背景/●、明确当前软件模式；只接受worker已应用快照驱动后续选中态，不提前高亮请求。启动时默认EASY初始化是明确例外。
- Instruments.inputCell提取原有路由，KEY保留首次匹配与重复C，所有合法0–127输入与旧路由等价；表未改。
- 不支持通道/超布局Note On显示实际输入/力度/未演奏原因。接受通道Note On在提交尝试后写本地日志；有日志不代表声音成功。
- Manifest仅版本变为0.3.1/code4，无权限变化；构建补ModeFeedbackTest并更新APK文件名。
- 前次复核因父会话漏建隔离目录未启动，已明确报告并停止该轮；后续目录创建后用同一sol正常复核，没有更换runner或绕过门禁。

## 独立复核和父会话测试

独立sol：**NO BLOCKING FINDINGS**。独立JDK17纯测试2140/64/536检查与额外384种合法mode/pitch路由等价检查通过。没有ADB、硬件、密钥、网络或主项目写入；不是完整构建/听音验收。

父会话完整`./android/build.sh`通过：固定素材/许可/64WAV、全部原有协议/随机拆包/菱形/生命周期/并发/真实控制器替身、Instruments536、UiPublisher64、新ModeFeedback2140，以及真实SF2 native测试。原生源码字节未变，此轮未另重跑sanitizer，0.3父级ASan/UBSan记录保留，不能冒称0.3.1重新执行sanitizer。

签名/实际旧证书、版本、对齐、精确素材与notices、唯一arm64/无测试替身/无权限审计通过；完整安装/版本/截图确认见报告。

## 证据

- `tests/reports/android-v031-change-request.json`
- `tests/reports/android-v031-corner-observation.json`
- `tests/reports/android-v031-octave-user-acceptance.json`
- `tests/reports/android-v031-parent-build.txt`
- `tests/reports/android-v031-independent-review.md`
- `tests/reports/android-v031-package-verification.json`
- `tests/reports/android-v031-install.json`
- `docs/images/android-v031-{easy,drum,key}.png`
- 备份：`backups/android-v03-before-mode-mapping-20261107-225940/`，没有复制/导出签名密钥。

## 未完成验收

Android失败音源请求、超范围/通道拒绝提示为源码/纯测试验证，未做Android故障注入；2140项不是视觉/仪器测量。全64格、其他八度/移调、不自动模式推断、AAudio断路重开、native音色/延音/密集演奏/RAM/温度、菱形与清灯等仍按0.3范围单独验收。active为软件模式，不证明实体也已切换。
