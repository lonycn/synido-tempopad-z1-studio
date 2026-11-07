# 不带电脑：iPhone / iPad / 安卓平板方案

## 结论

Z-1是USB MIDI控制器，手机/平板可以作为本地音源，不需要旁边开着Mac。厂商Z-1产品页明确列出iPhone/iPad及Android平台；但不能由此推断我们这套自制EASY音频和RGB在所有手机App里自动可用。

最初建议先验证USB、供电和延迟，再决定是否开发移动版。当前已在用户现有华为平板完成自制Android0.1基础演奏验收，优先继续这台设备，不急于购买iPad；以下跨平台比较保留作为参考。

- **尽快玩起来：优先USB-C iPad。** 转接较简单，音乐App生态成熟；已经装有GarageBand时，KEY钢琴最容易先验证。
- **完整复刻自制EASY + 实体扩散：需要原生移动版。** 安卓APK通常比iOS自签长期安装更省事，但音频延迟与USB支持必须实机测。iPad版则可复用部分Swift/CoreMIDI/AVAudioEngine思路，需要替换AppKit UI并解决签名分发。
- **旧iPhone：适合小型专用音源盒，但先确认型号和系统。** Lightning转接、供电、App最低系统和电池状况可能比设备性能更影响便利。

## 已连接的安卓候选设备

本轮通过用户授权USB实际读取并建立局域网ADB：

- HUAWEI DBY-W09，鸿蒙4.2.0.213（平台4.2.0）。
- Android兼容层报告Android12 / API31，arm64-v8a，内存约6GB。
- 系统声明USB Host、android.software.midi、低延迟音频与专业音频特性，MIDI系统服务存在。
- 无线ADB通过授权USB启用，设备局域网地址的5555端口曾连通，后续可腾出USB连接Z-1。仅在可信局域网使用；本轮基础测试完成后已执行adb usb关闭TCP调试，5555端口复核拒绝连接。后续继续开发需先接USB恢复网络ADB，已安装App演奏不依赖ADB。
- 随后已实际接入Z-1：系统枚举为ArtFast / Synido TempoPAD Z-1，VID10467 / PID4101，Android MIDI服务已注册1个输入端口和1个输出端口，平板处于USB Host并给控制器供电。
- 后续经用户授权已开发并安装Android0.1最小测试APK（local.orange.z1androidtest）。64段原创WAV全部预加载，MIDI接收端口已打开，屏幕试听获得非零SoundPool播放ID。用户随后实体演奏确认“有声音、位置对、跟手”，基础演奏链路通过；没有测得声学延迟毫秒数。
- 0.2加入已核实RGB扩散；用户反馈“很好”，继而请求真实力度说明、菱形与DRUM/KEY。当前**0.3.1已原位安装**（补充模式active与超音区提示；用户已确认手动校正Octave后DRUM对应恢复）：MIT FluidR3采样GM鼓/钢琴、真实Note力度/延音、菱形模型；第二次独立审查及父会话完整重测通过，设备音源和三模式界面就绪。真实音色/力度/菱形与长期稳定性待验收。EASY仍无力度字段，不伪造传感值。详见[Android测试版](../android/README.md)、[0.3审查](REVIEW_ANDROID_V03.md)。

报告：`tests/reports/android-device-check.json`（初始系统检查）、`tests/reports/android-z1-usb-check.json`（实接Z-1检查）。用户反馈另见 `tests/reports/android-user-acceptance.json`：端到端基础发声/位置/主观响应已确认，仍不代表全部64格、全部模式和长期稳定性完成验收。

## 对比

| 设备 | 主要优点 | 主要限制 | 推荐条件 |
|---|---|---|---|
| USB-C iPad | 音乐App多、USB MIDI路径成熟、界面大 | 集线器/供电及App版本仍需确认；自制App有签名问题 | 首选现成音源方案 |
| Lightning iPad/iPhone | 可使用class-compliant MIDI；旧机能做专用音源 | 建议Apple Lightning转USB3相机转换器并接电，线较多；旧系统兼容性 | 已有转换器和兼容App时很实用 |
| USB-C安卓Pad | OTG方便，原生APK通常便于长期自用安装 | Android MIDI为厂商可选支持；USB主机、电源与音频延迟因机型而异 | 想开发完整助手且实机延迟可接受时 |

iPhone最轻，iPad屏幕操作最方便，安卓自制部署最灵活；在不知道具体型号前，不给绝对的性能排名。

## 接线

### USB-C iPad / 安卓Pad

```text
平板USB-C → OTG或带供电的USB Hub → Z-1数据USB-C口
充电宝/合规5V电源 → Z-1的DC-5V供电口（按需要）
声音 → 平板内置扬声器 / 有线耳机 / USB音频接口
```

可以先使用Z-1随附OTG转接头和原USB-A→USB-C数据线；支持数据传输的C→C线也是候选，仍需实际枚举确认。USB-C插头形状相同不代表一定有数据功能。

### Lightning iPhone / iPad

```text
手机/平板 → Apple Lightning转USB3相机转换器 → Z-1原USB数据线
电源/充电宝 → 转换器的Lightning供电口
必要时另给Z-1的DC-5V口供电，或使用带电源的USB Hub
```

Apple说明相机转换器支持Audio/MIDI设备；如果出现“配件耗电量过大”，需要外部供电。不要默认一个便宜的充电转接头可以当USB主机数据转换器。

**Z-1有两个用途不同的接口：数据USB-C和仅供电DC-5V。接错供电口只会亮灯，不会收到MIDI。MIDI OUT也不是耳机输出。** 官方说明书PDF第4页确认这些定义。

初次验证使用设备扬声器或有线耳机，不用蓝牙耳机/蓝牙音箱评价打击延迟。装好App和素材后，选用支持离线演奏的方案，可在没有Mac和网络的情况下工作。

## 现成App能做到什么

### KEY钢琴：GarageBand等音源App

- 设备与App版本兼容时，使用普通USB MIDI输入，硬件KEY，App选钢琴。
- iPhone和iPad的GarageBand是独立音源，不是需要Mac的Logic Remote。不要把遥控软件当成无电脑方案。
- 本轮App Store当前GarageBand页面要求较新系统（检索到iOS/iPadOS27）；旧机不能直接假设可下载最新版。已安装的旧版、可下载历史兼容版与其他音源App需逐机确认，不能保证一定提供旧版下载。

### 鼓/自定义采样：Koala Sampler

- 官方帮助明确支持USB class-compliant MIDI控制器，iOS与Android均有版本。
- 可导入自己的WAV并用MIDI映射触发。当前原始64秒素材可以另行导出为64个WAV。
- **使用硬件DRUM作为标准Note输入，不是直接使用私有EASY消息。** 按实体位置配置映射，不要把音符36–99简单当作逐行PAD1–64。
- 普通鼓音源不一定覆盖36–99全部64个音符；这个问题不会因为换到手机自动消失。
- Koala MIDI支持不等于支持Synido专用RGB。不能承诺直接复现我们的实体扩散。
- 本轮Koala App Store页面列出iOS/iPadOS15及以后版本，价格和内购按当地商店显示；购买前核对设备兼容性。Android官方帮助提示某些情况下需先插USB再打开App。

### 安卓其他音源

FluidSynth MIDI Synthesizer等可作候选，需要合法SoundFont及具体音符/通道配置。不同App的延迟、输入支持、声音覆盖和后台运行不能一概而论，优先以实际“收到MIDI并且敲起来跟手”验收。

Android主机连接Z-1时，不应照搬“把手机USB模式改成MIDI”的电脑外设教程；那通常是让手机充当USB外设，而我们需要平板充当USB主机。

## 完整移动版Z1 Studio的开发范围

现在的macOS .app不能复制到iPhone/iPad/Android直接运行。移动端至少要实现：

1. USB MIDI设备发现、输入解析和已核实的RGB发送。
2. EASY64音频预加载、多声部、限幅和Panic。
3. 8×8触控界面、硬件高亮和现有映射。
4. 插拔、音频中断/锁屏/后台、外部音频设备及电池管理。
5. 合法的移动钢琴/鼓音源。macOS系统DLS的路径在移动系统不可依赖，也不能简单复制Apple采样库作为可分发资源。

可复用：纯映射、MIDI协议思路、扩散算法、原创EASY WAV。iOS可较多复用Swift和Apple音频架构；Android需Kotlin/Java或本地音频层，不能直接运行AppKit代码。

iOS/iPadOS免费Personal Team安装的描述文件约7天到期，需重新构建安装；长期外出演奏要规划正式签名分发，不应把每周重签的开发包当成免维护成品。Android原生APK可作为长期自用候选，仍取决于设备的安装限制与权限。

手机/平板不会解决Z-1未公开模式通知的问题。没有新的协议证据前，仍只有EASY首次敲垫可识别，DRUM/KEY手动同步；不承诺移动端突然具备双向模式自动切换。

不优先用纯网页作为三平台通用方案：浏览器USB MIDI/SysEx支持和权限不同，尤其不能假设iPhone/iPad网页可直接控制设备，原生验证更可控。

## 下一步最少需要的信息

- iPad型号、系统版本、USB-C还是Lightning。
- 旧iPhone型号和系统版本。
- 安卓Pad型号和Android版本。
- 已有OTG/相机转换器/带供电Hub及耳机接口情况（可选）。

第一轮先做5分钟接线验证：识别Z-1 → 标准MIDI按键有输入 → 钢琴/采样出声 → 密集敲击是否跟手 → 开灯后供电是否稳定。通过后再决定开发哪一端，不先购买新平板或移植整套程序。

## 来源

- 厂商Z-1产品说明：https://www.synido.com/products/synido-tempopad-z-1-midi-controller-beat-maker-dj-equipment
- 本地官方手册：references/vendor/Z1_User_Guide_EN_ZH_V1.0.pdf，第4页接口定义。
- Apple USB相机转换器与供电：https://support.apple.com/en-mide/111811
- Apple USB-C iPad与MIDI连接：https://support.apple.com/guide/logicpro-ipad/connect-external-devices-lpip26513101/ipados
- GarageBand当前兼容性：https://apps.apple.com/us/app/garageband/id408709785
- Koala USB MIDI帮助：https://koalasampler.com/help/midi/how-do-i-use-koala-with-my-midi-keyboard-controller.html
- Koala MIDI映射：https://manual.koalasampler.com/mobile/8-settings/
- Koala当前兼容性：https://apps.apple.com/us/app/koala-sampler-beat-maker/id1449584007
- Android MIDI API（含SysEx）：https://developer.android.com/reference/android/media/midi/package-summary
- Android MIDI可选平台支持：https://source.android.com/docs/core/audio/midi
- Apple免费签名限制：https://developer.apple.com/help/account/basics/about-your-developer-account/

资料核查之外，已完成华为平板系统/USB MIDI检查与Android0.1测试版安装、加载及屏幕试听调用。尚未完整移植Mac版；用户已确认实际出声、观察到的位置对应和主观跟手，未测量声学延迟，0.3已增加并安装采样鼓/钢琴与菱形，音源就绪、三模式界面已检查，实际音色/力度/菱形和新版跟手性待用户确认。
