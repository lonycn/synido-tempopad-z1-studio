# Android维护约定

继承项目根AGENTS.md。这里是local.orange.z1androidtest原生Java测试版，当前0.3.1/code4，已加入模式active/超音区提示与原生采样DRUM/KEY；不是完整Mac版。

- 修改前备份源码/manifest/构建文件及APK；保留历史用户验收与审查记录，不用新状态覆盖旧快照。
- 源码为src/local/orange/z1androidtest/。local/test.keystore是签名目录；不能用递归ignore_patterns('local')误删Java包。复制开发副本只排除顶层build/与local/，不得复制副本密钥回主目录。
- 保留主目录原密钥。更新前核对旧/新APK签名证书身份，签名不匹配时停止，不卸载用户App来绕过。
- ./build.sh只做离线host检查、打包和签名，不执行adb、不打开真实MIDI。Android替身只在tests/编译，不入APK。
- 唯一设备写路径为MidiLighting私有EasyRgbTransport.send(RgbFrame)，帧来自RgbFrame.easyWireFrame：F0 65 [index R7 G7 B7]*64 F7，259字节，index=PadProtocol.drum(cell)-36。禁止任何其他SysEx/配置/模式/固件/HID/USER写入。
- 只选择唯一真实USB VID10467/PID4101，接收和发送端口必须来自同一MidiDevice；新增第二台也fail closed。真实EASY单字节输入才授权，鼠标/开关/试听/普通Note不能授权；所有通道Note都撤销输出。
- Android MidiOutputPort是从设备接收，MidiInputPort是向设备发送；不要弄反。每次发送重新检查唯一同设备和进程会话lease。
- 进程级SendAttemptLimiter保留Activity/worker之间的至少100ms尝试间隔，包括失败、清灯与重连。不能在clear/revoke/reconnect时重置时钟。
- rendered/succeeded/pending分离，最新帧优先但自然结束和显式clear的terminal黑屏必须锁存。最多3次连续失败后关闭RGB，保留音频输入；旧实例异步清理最多约300ms后交接USB lease。不要主线程sleep或盲目无限重试。
- 设置失败保持稳定错误，显式重连或相关拓扑变化才重试；禁止立即close->refresh->失败循环。
- MIDI/音频控制/灯光在串行worker，UI回主线程；采样触发先于灯光I/O。不同硬件/触摸/试听owner独立。退出/后台/失焦停止声音和合适条件下清灯。
- 构建必须执行ProtocolTest、ProtocolFuzzTest、LightingTest、LifecycleTest、SendAttemptLimiterTest、真实MidiLighting配host替身测试；同时检查APK元数据、签名、64WAV及无新增权限。
- host断言、nonzero播放ID、无阻塞静态审查都不是硬件或实际延迟验收。实机RGB输出前告知会改变灯光；没有用户反馈不标为通过。0.1有声/位置/跟手验收不覆盖0.2灯光。
- GM/钢琴使用已核对MIT许可的固定TSF与FluidR3字体，来源/大小/SHA256及完整许可在assets/licenses/；构建前逐项验证，APK必须携带notice和离线credits。Debian获取包不是运行资产，不执行或分发其GPL维护脚本。不得打包Apple DLS或不明音色，不把正弦音称作采样钢琴。
- native/Synth.cpp是回调唯一synth拥有者；预分配声部/通道、有限SPSC队列、panic代次及有限PCM限幅保持。不能在实时回调分配/加锁/I/O/开关流；JNI handle只由worker控制，加载/销毁在串行loader，AAudio关闭后才释放synth。扫描voice时先检查playingPreset!=-1再访问key/channel。
- UI必须使用worker已应用的模式代次，保留pending发布期间的dirty revision与ack，不可用最新请求代次给旧快照贴标签；保持UiPublisher调度回归。模式按钮active/文字同样只跟随接受的快照（启动默认EASY初始化除外），不能提前高亮未执行/被拒绝请求。保留ModeFeedbackTest；用户Octave+2问题已通过手动校正解决，不应据此自动猜模式或硬改音符映射。
- 增改音源重跑真实SF2 host测试（49钢琴、64鼓、力度/off/sustain/panic/溢出/有限PCM/寿命）、ASan/UBSan及UiPublisher、Instruments/模式灯光测试。ASan/UBSan不是MSan/泄漏检测；当前host LeakSanitizer/MSan不支持。音源资产/内存和Android音色/延迟实测必须区分。
- 不初始化Git，不提交/推送/上传素材，不使用Root，不规避Android/macOS权限。无线ADB仅在用户授权的可信局域网使用，测试结束关闭。
