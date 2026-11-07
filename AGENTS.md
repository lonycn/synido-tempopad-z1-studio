# 维护约定（Mac v2.1 / Android 0.3.1）

- Android源码/构建/测试在android/，另读android/AGENTS.md和android/README.md。不要把Java包src/local/误当作签名目录android/local/而在备份或副本中递归忽略。
- Android输出唯一入口为MidiLighting的私有EasyRgbTransport.send(RgbFrame)，仅允许同一已配对USB设备上的已核实RGB帧；Mac的原输出入口保持不变。Android离线构建不会安装/发送MIDI，安装及实机输出须按用户授权执行。

- 本目录是Synido Z-1维护主目录。先读README.md、docs/ARCHITECTURE.md、references/PROTOCOL_V21.md。
- 源码src/*.swift，@main位于Z1Player.swift；元数据app/Info.plist；dist是构建产物。整体运行scripts/build.sh，不直接改签名包。
- 工程修改先备份；.band作为完整目录包处理。推荐官方EASY为projects/easy/Z1_EASY_Lights.eas，.eas通过官方UI编辑，不解密载荷。
- 助手EASY独立播放打包WAV，不依赖官方软件。DRUM与KEY均为CH1，音符重叠，不能猜模式；KEY暗键停用、重复边界C多格显示。
- 模式顺序固定EASY / DRUM / KEY。真实EASY敲垫可自动识别；未找到模式键报告和硬件切模式命令，不声称完整双向同步。
- 用户已要求实体扩散。唯一允许的设备输出是两个独立核查确认的EASY RGB帧：F0 65 [index R7 G7 B7]*64 F7（259字节）；全零为清灯。
- 不发送其他未知SysEx、不升级固件、不恢复出厂、不写USER配置。不混用配置/版本/固件路径充当模式初始化。
- RGB只在真实EASY输入确认后启用，输入必须与唯一物理输出配对。多个Z-1输出时fail closed，不按枚举顺序猜设备。
- 输出经Lighting的限频drain统一发送，渲染帧与已成功发送帧分离，只保留最新pending。黑屏不可被节流丢弃；失败最多3次。清理不重置限频，退出可短暂等待约300ms交付清灯。
- 当前没有硬件模式查询；最近EASY输入可能在用户只按模式键后过期。不能宣称始终知道硬件模式。实机灯效与退出清灯必须单独验收。
- 音频控制/MIDI解析/灯光定时器只在串行performance队列；AppKit回主线程。ModeIntent保留手动命令代次，自动发布不能覆盖更新的手动选择。
- 松键按独立输入owner释放，不能让延迟试听/无障碍动作截断后来按下的音。
- 默认检查python3 scripts/validate.py、签名、--model-test、--self-test。这些不输出硬件MIDI。RGB实机测试会改变当前灯光，执行前说明。
- tools/diagnostics不是盲跑安装器。capture_input.swift只读输入，可供用户按实体键时核查；不要将未记录到用户操作的静默窗口当作模式键绝不发消息的证明。
- 启动助手会请求正常退出官方软件/GarageBand，不强杀未保存工程。不要规避隐私授权。
- 项目路径从脚本/Bundle推导或Z1_PROJECT_ROOT，不再硬编码旧Music目录。
- 保留backups/v1、backups/v2和旧Music快照，不并行维护。改行为更新文档、CHANGELOG和测试记录，如实列出未完成验收。
- 未经用户要求不初始化Git、提交、推送或发布厂商/Apple资料。
