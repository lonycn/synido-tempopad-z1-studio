import Foundation

// Official full64 form, 259 bytes. No guessed mode/configuration commands.
struct LightingWire {
    static func frame(_ cells: [RGB]) -> [UInt8] {
        precondition(cells.count == 64)
        var bytes: [UInt8]=[0xF0,0x65]
        for index in 0..<64 {
            let cell=Layout.cells(index+36,mode: .easy)[0], color=cells[cell]
            bytes += [UInt8(index),min(127,color.r),min(127,color.g),min(127,color.b)]
        }
        bytes.append(0xF7); return bytes
    }
}

// Queue-confined. One latest pending frame, independent rendered/sent state.
final class Lighting {
    let queue: DispatchQueue
    var mode: Mode = .drum
    private(set) var hardwareEasy=false
    private var effect=Diffusion(), timer: DispatchSourceTimer?
    private var rendered: [RGB]?, sent: [RGB]?, pending: [RGB]?
    private var lastAttempt: TimeInterval = -.infinity
    private var failures=0
    var clock: () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }
    var send: (([RGB]) -> Bool)?
    var onFrame: (([RGB]) -> Void)?
    var onStatus: ((String) -> Void)?
    init(queue: DispatchQueue) { self.queue=queue }
    func status() { onStatus?(hardwareEasy ? "实体 RGB：EASY 已识别 · 扩散灯效就绪" : "实体 RGB：等待硬件 EASY 敲击确认") }
    func observeHardwareEasy(_ value: Bool) {
        guard value != hardwareEasy else { return }
        clear(sendBlack: false); hardwareEasy=value; sent=nil; failures=0; status()
        // Do not reset pacing when authorization changes.
    }
    func setMode(_ value: Mode) { if value != mode { clear(); mode=value }; status() }
    private func ensureTimer() {
        guard timer == nil, !effect.waves.isEmpty || pending != nil else { return }
        let t=DispatchSource.makeTimerSource(queue: queue)
        t.schedule(deadline: .now()+0.05,repeating: 0.05,leeway: .milliseconds(3))
        t.setEventHandler { [weak self] in self?.tick() }; timer=t; t.resume()
    }
    func hit(_ cell: Int, velocity: Int) {
        guard mode == .easy else { return }
        effect.hit(cell,velocity: velocity,now: clock()); tick(); ensureTimer()
    }
    private func offer(_ colors: [RGB]) {
        // Replacing pending never queues historical animation frames.
        pending = colors == sent ? nil : colors
    }
    private func drain(now: TimeInterval) {
        guard let frame=pending else { return }
        guard hardwareEasy, mode == .easy || frame.allSatisfy({ $0 == .black }) else { pending=nil; return }
        guard now-lastAttempt >= 0.1 else { return }
        lastAttempt=now
        if send?(frame) == true { sent=frame; pending=nil; failures=0 }
        else {
            failures += 1; onStatus?("实体 RGB 输出失败，请检查 USB 连接")
            if failures >= 3 { pending=nil; hardwareEasy=false }
        }
    }
    // Internal visibility permits deterministic clock/mock regression tests.
    func tick() {
        let now=clock(), colors=effect.frame(now: clock())
        if colors != rendered { onFrame?(colors); rendered=colors }
        if hardwareEasy && (mode == .easy || pending != nil) { offer(colors) }
        drain(now: now)
        if effect.waves.isEmpty && pending == nil { timer?.cancel(); timer=nil }
        else { ensureTimer() }
    }
    func clear(sendBlack: Bool = true) {
        timer?.cancel(); timer=nil; effect.clear()
        let black=Array(repeating: RGB.black,count: 64)
        if rendered != black { onFrame?(black); rendered=black }
        pending=nil
        if sendBlack && hardwareEasy { offer(black); drain(now: clock()); ensureTimer() }
    }
    func shutdown() {
        clear()
        // At most ~300ms for a paced terminal blackout before disposing CoreMIDI.
        for _ in 0..<3 where pending != nil && hardwareEasy {
            let delay=max(0,0.101-(clock()-lastAttempt))
            if delay>0 { Thread.sleep(forTimeInterval: min(0.101,delay)) }
            tick()
        }
        timer?.cancel(); timer=nil; pending=nil; hardwareEasy=false
    }
    deinit { timer?.cancel() }
}

func runLightingTests() {
    var cells=Array(repeating: RGB.black,count: 64)
    cells[56]=RGB(r: 127,g: 64,b: 1); cells[60]=RGB(r: 2,g: 3,b: 4)
    let bytes=LightingWire.frame(cells)
    precondition(bytes.count == 259 && Array(bytes.prefix(6)) == [0xF0,0x65,0,127,64,1])
    precondition(Array(bytes[130..<134]) == [32,2,3,4] && bytes.last == 0xF7)
    precondition(bytes.dropFirst().dropLast().allSatisfy { $0 < 128 })
    let blackout=LightingWire.frame(Array(repeating: .black,count: 64))
    for i in 0..<64 { precondition(Array(blackout[(2+4*i)..<(6+4*i)]) == [UInt8(i),0,0,0]) }
    let queue=DispatchQueue(label: "Z1 light tests")
    queue.sync {
        let light=Lighting(queue: queue); var now=100.0, frames: [(Double,[RGB])]=[]
        light.clock={ now }; light.send={ frames.append((now,$0)); return true }
        light.setMode(.easy); light.hit(56,velocity: 100); precondition(frames.isEmpty)
        light.clear(); light.observeHardwareEasy(true); light.hit(27,velocity: 100)
        for _ in 0..<100 { light.hit(27,velocity: 100) }
        precondition(frames.count == 1)
        for i in 1...32 { now=100+Double(i)*0.05; light.tick() }
        precondition(frames.last!.1.allSatisfy { $0 == .black })
        for i in 1..<frames.count { precondition(frames[i].0-frames[i-1].0 >= 0.099999) }
        // Clear + setMode must not send duplicate black frames or bypass pacing.
        now=103; light.hit(56,velocity: 100); let count=frames.count
        light.clear(); light.setMode(.key); precondition(frames.count == count)
        now=103.11; light.tick(); precondition(frames.count == count+1)
        light.observeHardwareEasy(false); light.clear(); precondition(frames.count == count+1)
        // Failed sends are not committed; retry latest only, then fail closed.
        let faulty=Lighting(queue: queue); var attempts=0
        faulty.clock={ now }; faulty.send={ _ in attempts += 1; return false }
        faulty.setMode(.easy); faulty.observeHardwareEasy(true); faulty.hit(56,velocity: 100)
        now += 0.11; faulty.tick(); now += 0.11; faulty.tick()
        precondition(attempts == 3 && !faulty.hardwareEasy)
        faulty.clear(sendBlack: false); light.clear(sendBlack: false)
    }
    print("LIGHT PASS: audited RGB/quadrant frame; terminal blackout; strict pacing; dedup; bounded failed-send retries")
}
