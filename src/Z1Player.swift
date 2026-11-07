import AppKit
import AVFoundation
import CoreMIDI

let projectRoot: URL = {
    if let path=ProcessInfo.processInfo.environment["Z1_PROJECT_ROOT"], !path.isEmpty { return URL(fileURLWithPath: path) }
    let parent=Bundle.main.bundleURL.deletingLastPathComponent()
    return parent.lastPathComponent == "dist" ? parent.deletingLastPathComponent() : parent
}()

final class Player: NSObject, NSApplicationDelegate, NSWindowDelegate {
    var window: NSWindow!, surface: PadSurface!, connection: NSTextField!, audioStatus: NSTextField!, detail: NSTextField!, help: NSTextField!, modeTitle: NSTextField!, meter: NSLevelIndicator!
    var offsetControl: NSPopUpButton!, volumeControl: NSSlider!, hardwareStatus: NSTextField!
    let queue=DispatchQueue(label: "local.orange.z1.performance", qos: .userInteractive)
    var midi: MIDIInput!, performance: Performance?, lighting: Lighting!
    var mode: Mode = .drum, offset = 0
    var testGeneration = 0
    let modeIntent=ModeIntent()
    var appliedIntent: UInt64=0 // performance queue only
    func label(_ value: String, _ frame: NSRect, size: CGFloat = 14, color: NSColor = .secondaryLabelColor, weight: NSFont.Weight = .regular) -> NSTextField {
        let l=NSTextField(wrappingLabelWithString: value); l.frame=frame; l.font = .systemFont(ofSize: size, weight: weight); l.textColor=color
        window.contentView!.addSubview(l); return l
    }
    func button(_ title: String, _ frame: NSRect, _ action: Selector) {
        let b=NSButton(title: title, target: self, action: action); b.frame=frame; b.bezelStyle = .rounded; window.contentView!.addSubview(b)
    }
    func applicationDidFinishLaunching(_ notification: Notification) {
        // Existing mutually-exclusive hosts are asked to exit normally, never force-killed.
        for id in ["SY.CEG.SY020", "com.apple.garageband10"] {
            for host in NSRunningApplication.runningApplications(withBundleIdentifier: id) {
                host.terminate(); let deadline=Date().addingTimeInterval(8)
                while !host.isTerminated && Date()<deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.1)) }
                if !host.isTerminated {
                    let a=NSAlert(); a.messageText="请先保存并退出 \(host.localizedName ?? id)"; a.informativeText="避免重复发声；未强制退出任何程序。"; a.runModal(); NSApp.terminate(nil); return
                }
            }
        }
        mode=CommandLine.arguments.contains("--easy") ? .easy : CommandLine.arguments.contains("--piano") ? .key : .drum
        let available=NSScreen.main?.visibleFrame.size ?? NSSize(width: 1440,height: 900)
        let scale=min(1, (available.width-60)/1140, (available.height-70)/900)
        window=NSWindow(contentRect: NSRect(x: 0,y: 0,width: 1140*scale,height: 900*scale), styleMask: [.titled,.closable,.miniaturizable], backing: .buffered, defer: false)
        window.contentView!.bounds=NSRect(x: 0,y: 0,width: 1140,height: 900)
        let version=Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "开发版"
        let build=Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
        window.title="Z1 Studio v\(version) (\(build)) · 演奏助手"; window.appearance=NSAppearance(named: .darkAqua); window.backgroundColor=NSColor(white: 0.055, alpha: 1)
        window.center(); window.delegate=self
        _=label("Z1 STUDIO", NSRect(x: 30,y: 853,width: 220,height: 28),size: 23,color: .white,weight: .bold)
        _=label("v\(version) · Build \(build)  /  你的桌面演奏空间", NSRect(x: 215,y: 858,width: 550,height: 22),size: 13)
        _=label("LOCAL AUDIO  /  NO DAW REQUIRED", NSRect(x: 815,y: 858,width: 295,height: 22),size: 11,color: .systemMint,weight: .medium)
        surface=PadSurface(frame: NSRect(x: 24,y: 70,width: 760,height: 760)); window.contentView!.addSubview(surface)
        surface.onMode={ [weak self] value in if let mode=Mode(rawValue: value) { self?.select(mode) } }
        surface.onPadDown={ [weak self] in self?.pointer($0, down: true) }
        surface.onPadUp={ [weak self] in self?.pointer($0, down: false) }
        surface.onPadActivate={ [weak self] in self?.pulse($0, duration: 0.15) }
        modeTitle=label("", NSRect(x: 812,y: 775,width: 300,height: 48),size: 27,color: .white,weight: .bold)
        _=label("MIDI DEVICE", NSRect(x: 812,y: 735,width: 300,height: 20),size: 10,weight: .bold)
        connection=label("正在连接…", NSRect(x: 812,y: 680,width: 300,height: 52),size: 13,color: .systemMint)
        _=label("AUDIO ENGINE", NSRect(x: 812,y: 650,width: 300,height: 20),size: 10,weight: .bold)
        audioStatus=label("加载本地音源…", NSRect(x: 812,y: 595,width: 300,height: 48),size: 13)
        help=label("", NSRect(x: 812,y: 438,width: 300,height: 135),size: 14)
        _=label("击打力度", NSRect(x: 812,y: 408,width: 300,height: 20),size: 11,weight: .bold)
        meter=NSLevelIndicator(frame: NSRect(x: 812,y: 382,width: 290,height: 18)); meter.levelIndicatorStyle = .continuousCapacity; meter.minValue=0; meter.maxValue=127; meter.warningValue=115; meter.criticalValue=127; window.contentView!.addSubview(meter)
        detail=label("等待敲击 · 也可用鼠标弹奏", NSRect(x: 812,y: 304,width: 300,height: 60),size: 12)
        _=label("硬件显示偏移（半音）", NSRect(x: 812,y: 269,width: 290,height: 20),size: 11)
        offsetControl=NSPopUpButton(frame: NSRect(x: 808,y: 231,width: 296,height: 30))
        for n in -24...24 { offsetControl.addItem(withTitle: n == 0 ? "0 · 原厂布局" : String(format: "%+d", n)); offsetControl.lastItem?.tag=n }
        offsetControl.selectItem(withTag: 0); offsetControl.target=self; offsetControl.action=#selector(offsetChanged); window.contentView!.addSubview(offsetControl)
        button("试听当前音源", NSRect(x: 808,y: 172,width: 145,height: 34), #selector(testSound))
        button("停止所有声音", NSRect(x: 958,y: 172,width: 150,height: 34), #selector(panic))
        button("工程与使用说明 ↗", NSRect(x: 808,y: 119,width: 300,height: 34), #selector(openFolder))
        hardwareStatus=label("实体 RGB：等待 EASY 输入确认", NSRect(x: 812,y: 77,width: 300,height: 32),size: 10)
        _=label("MASTER", NSRect(x: 34,y: 25,width: 70,height: 22),size: 10,weight: .bold)
        volumeControl=NSSlider(value: 0.65,minValue: 0,maxValue: 1,target: self,action: #selector(volumeChanged)); volumeControl.frame=NSRect(x: 108,y: 25,width: 205,height: 22); volumeControl.setAccessibilityLabel("主音量"); window.contentView!.addSubview(volumeControl)
        _=label("鼠标力度 100  ·  EASY 敲击自动识别  ·  DRUM / KEY 仍需手动同步", NSRect(x: 345,y: 24,width: 750,height: 23),size: 12)
        updateSurface(); window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        midi=MIDIInput(queue: queue); lighting=Lighting(queue: queue)
        lighting.send={ [weak self] in self?.midi.sendRGB($0) ?? false }
        lighting.onFrame={ [weak self] colors in
            guard let self=self else { return }; let generation=self.modeIntent.current
            DispatchQueue.main.async {
                guard self.modeIntent.current == generation, self.mode == .easy else { return }; self.surface.applyLights(colors)
            }
        }
        lighting.onStatus={ [weak self] text in DispatchQueue.main.async { self?.hardwareStatus.stringValue=text } }
        midi.onOutputStatus={ [weak self] text in
            if text.contains("错误") || text.contains("失败") || text.contains("未") {
                DispatchQueue.main.async { self?.hardwareStatus.stringValue=text }
            } else { self?.lighting.status() }
        }
        midi.onDestinationChange={ [weak self] in self?.lighting.observeHardwareEasy(false) }
        midi.onStatus={ [weak self] text in DispatchQueue.main.async { self?.connection.stringValue=text } }
        midi.onMessage={ [weak self] source,msg in self?.receive(source,msg) }
        midi.onRemove={ [weak self] source in self?.lighting.observeHardwareEasy(false); self?.performance?.remove(source) }
        let initialMode=mode
        queue.async { [weak self] in
            guard let self=self else { return }
            do {
                let p=Performance(audio: try Audio()); self.performance=p; p.mode=initialMode; self.lighting.setMode(initialMode)
                p.onVisual={ [weak self] cells,velocity,down,text in
                    guard let self=self else { return }
                    if down && self.performance?.mode == .easy { cells.forEach { self.lighting.hit($0,velocity: velocity) } }
                    let generation=self.modeIntent.current, visualMode=self.performance?.mode
                    DispatchQueue.main.async {
                        guard self.modeIntent.current == generation, self.mode == visualMode else { return }
                        cells.forEach { self.surface.flash($0,velocity: velocity,down: down) }
                        if down { self.meter.doubleValue=Double(velocity); self.detail.stringValue=text }
                    }
                }
                p.onClear={ [weak self] in
                    self?.lighting.clear()
                    let generation=self?.modeIntent.current
                    DispatchQueue.main.async {
                        guard let self=self, self.modeIntent.current == generation else { return }
                        self.surface.clear(); self.meter.doubleValue=0
                    }
                }
                DispatchQueue.main.async { self.audioStatus.stringValue="● 本地音源已就绪 · 32 声部 EASY"; self.audioStatus.textColor = .systemMint }
            } catch { DispatchQueue.main.async { self.audioStatus.stringValue="音源失败：\(error.localizedDescription)"; self.audioStatus.textColor = .systemRed } }
            self.midi.start()
        }
    }
    func updateSurface() {
        surface.mode=mode.rawValue
        surface.cells=(0..<64).map { cell in
            let pad=Layout.pad(cell), note=Layout.drum(cell)+offset
            if mode == .key {
                guard let key=Layout.key(cell) else { return SurfaceCell(title: "—",subtitle: "停用",enabled: false,hue: 0) }
                let n=key+offset, black=(7-cell/8)%2 == 1
                return SurfaceCell(title: Layout.name(n),subtitle: "MIDI \(n)" + (black ? " ♯" : ""),enabled: true,hue: n%12 == 0 ? 0.76 : black ? 0.1 : 0.14)
            }
            if mode == .easy { return SurfaceCell(title: String(format: "%02d",pad+1),subtitle: pad<16 ? Layout.drums[pad] : Layout.sampleName(pad),enabled: true,hue: CGFloat(pad%16)/16) }
            let hue: CGFloat = cell%8<4 ? (cell/8<4 ? 0.1 : 0.76) : (cell/8<4 ? 0.61 : 0.51)
            return SurfaceCell(title: "\(note)",subtitle: "GM \(drumNote(UInt8(max(0,min(127,note)))))",enabled: true,hue: hue)
        }
        modeTitle.stringValue=mode == .drum ? "DRUM / 鼓组" : mode == .key ? "KEY / 大钢琴" : "EASY / 声音魔格"
        help.stringValue=mode == .drum ? "硬件按 DRUM。\n64 格鼓声，四个分区与实物一致。\n\n扩展音区复用常用打击乐。" : mode == .key ? "硬件按 KEY。\n暗格是原厂停用位置。\n\n重复 C 音会同时标亮两个位置；移调后请调整下方偏移。" : "硬件按 EASY，直接播放内置 64 格声音。\n若没有输入，可用硬件 DRUM 或 USER 作为备用。\n\n硬件 EASY 输入确认后，实体与屏幕同步扩散。"
    }
    func select(_ value: Mode) {
        let generation=modeIntent.advance()
        surface.clear(); mode=value; testGeneration += 1; updateSurface()
        detail.stringValue="已选 \(value.rawValue) · 请同步按硬件同名模式键"
        let offset=offset
        queue.async {
            guard self.modeIntent.current == generation else { return }
            self.appliedIntent=generation
            self.performance?.change(value,offset: offset); self.lighting.setMode(value)
        }
    }
    // Called only on the performance queue. Never infer DRUM vs KEY from notes.
    func receive(_ source: MIDIEndpointRef, _ message: MIDIMessage) {
        if midi.isPhysicalZ1(source) {
            if message.status == 0xF0 && message.data.count == 1 {
                if midi.isLightingSource(source) { lighting.observeHardwareEasy(true) }
                let generation=modeIntent.current
                if message.data[0] < 64 && performance?.mode != .easy && appliedIntent == generation {
                    let calibration=performance?.offset ?? 0
                    performance?.change(.easy,offset: calibration); lighting.setMode(.easy)
                    DispatchQueue.main.async {
                        guard self.modeIntent.current == generation else { return }
                        self.surface.clear(); self.mode = .easy; self.updateSurface()
                        self.detail.stringValue="已从硬件 EASY 敲击自动识别"
                    }
                }
            } else if midi.isLightingSource(source) && (message.status & 0xF0 == 0x90 || message.status & 0xF0 == 0x80) {
                lighting.observeHardwareEasy(false)
            }
        }
        performance?.message(source,message)
    }
    func pointer(_ cell: Int, down: Bool, ownerPrefix: String = "mouse") {
        let m=mode, offset=offset
        queue.async {
            guard let p=self.performance else { return }; let owner="\(ownerPrefix):\(cell)"
            if !down { p.release(owner); return }
            guard p.mode == m else { return }
            let note=(m == .key ? Layout.key(cell) : Layout.drum(cell)).map { $0+offset } ?? 60
            p.hit(owner,note: note,velocity: 100,explicitCell: cell)
        }
    }
    @objc func offsetChanged() { offset=offsetControl.selectedItem?.tag ?? 0; select(mode) }
    @objc func volumeChanged() { let value=volumeControl.floatValue; queue.async { self.performance?.audio.volume=value } }
    @objc func panic() { testGeneration += 1; surface.clear(); queue.async { self.performance?.stop() } }
    @objc func openFolder() { NSWorkspace.shared.open(projectRoot) }
    @objc func testSound() { pulse(56,duration: 0.4) }
    func pulse(_ cell: Int, duration: Double) {
        let prefix="pulse:\(UUID().uuidString)"
        pointer(cell,down: true,ownerPrefix: prefix)
        DispatchQueue.main.asyncAfter(deadline: .now()+duration) { [weak self] in
            guard let self=self else { return }
            // Unique ownership cannot cut off a later hardware/mouse note.
            self.queue.async { self.performance?.release("\(prefix):\(cell)") }
        }
    }
    func windowDidResignKey(_ notification: Notification) { surface.releaseMouse() }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func applicationWillTerminate(_ notification: Notification) { queue.sync { performance?.stop(); lighting?.shutdown(); midi?.stop() } }
}

@main struct Z1Main {
    static func main() {
        if CommandLine.arguments.contains("--self-test") || CommandLine.arguments.contains("--model-test") {
            runModelTests(); runEffectTests(); runLightingTests()
            if !CommandLine.arguments.contains("--model-test") {
                do { try runAudioTests() } catch { print("SELF TEST ERROR: \(error)"); exit(1) }
            }
            return
        }
        let app=NSApplication.shared, delegate=Player(); app.delegate=delegate; app.setActivationPolicy(.regular)
        if let icon=Bundle.main.url(forResource: "AppIcon",withExtension: "icns") {
            app.applicationIconImage=NSImage(contentsOf: icon)
        }
        let main=NSMenu(), item=NSMenuItem(), menu=NSMenu()
        let folder=NSMenuItem(title: "打开项目目录",action: #selector(Player.openFolder),keyEquivalent: "o"); folder.target=delegate; menu.addItem(folder)
        let panic=NSMenuItem(title: "停止所有声音",action: #selector(Player.panic),keyEquivalent: "."); panic.target=delegate; menu.addItem(panic)
        menu.addItem(.separator()); menu.addItem(withTitle: "退出 Z1 Studio",action: #selector(NSApplication.terminate(_:)),keyEquivalent: "q")
        item.submenu=menu; main.addItem(item); app.mainMenu=main
        withExtendedLifetime(delegate) { app.run() }
    }
}
