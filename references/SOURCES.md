# 参考资料与权利说明

## Android 0.3 原生采样音源

- TinySoundFont：MIT，固定commit853a0a171759f1ddba0de1442133a75912bbeffa，https://github.com/schellingb/TinySoundFont 。完整许可在`../android/vendor/tinysoundfont/LICENSE`与APK assets/licenses/。
- FluidR3 GM：Debian fluid-soundfont-gm3.1-6，仅字体数据MIT；https://metadata.ftp-master.debian.org/changelogs/main/f/fluid-soundfont/fluid-soundfont_3.1-6_copyright 。取得的.deb按官方SHA256验证，仅提取字体/copyright，不执行包或打包其单独GPL维护脚本。
- 完整固定来源、字节数/SHA256和font/packaging区别：`../android/assets/licenses/instrument-sources.json`；完整MIT、NDK静态runtime notices随APK并可离线阅读。没有作超出notice的素材溯源保证。
- 目前仅本机项目/用户平板开发测试，不自动授予整个项目统一第三方发行许可，不上传/公开发布，不复制Apple DLS；真正对外发行仍须复核许可、签名和隐私声明。

## 官方说明书

- 名称：Synido TempoPAD Z-1 — User Guide English / Chinese V1.0。
- 来源：
  https://cdn.shopify.com/s/files/1/0831/0901/9943/files/Synido_TempoPAD_Z-1_-_User_Guide_English_Chinese_V1.0.pdf?v=1710906787
- 本地归档：`vendor/Z1_User_Guide_EN_ZH_V1.0.pdf`。
- 检索副本：`vendor/manual-ocr.txt`，由 `tools/diagnostics/z1_ocr.swift` 生成。
- PDF 为厂商资料，权利属于相应权利人；OCR 可能识别错误，映射／按钮含义以 PDF 图示及实际设备为准。

本次重点核对：DRUM 音符布局、KEY 的停用位置、DRUM/KEY 通道固定为 CH1、EASY 依赖官方软件、灯光保存及全局应用。

## 官方网页

- 使用指南：https://www.synido.cn/synido-tempopad-z-1/
- 中文下载：https://www.synido.cn/support/downloads/
- 英文下载：https://www.synido.com/pages/downloads
- FAQ：https://www.synido.com/pages/synido-tempopad-z-1-faqs

FAQ 说明 MIDI 控制器本身不发声，且使用 DAW 时建议退出控制面板避免冲突。

## 本地软件和素材

- 官方控制软件：`/Applications/Synido TempoPADZ-1.app`；本次检查版本 1.2.0，bundle ID `SY.CEG.SY020`。未复制官方应用进本目录。
- macOS 系统 GM DLS：助手运行时直接使用系统安装文件，不在项目中复制分发。
- GarageBand / Steinway / SoCal：Apple 安装的音色，本项目只保留本地工程；没有打包其采样库。
- 自制内容：演奏助手源码及编译应用、合成入门 WAV、PAD 清单、EASY 声光配置、生成／诊断／测试脚本和文档。

本项目没有统一声明第三方资料或 Apple 音色可自由分发；对外发布前应单独审查权利与软件签名要求。
