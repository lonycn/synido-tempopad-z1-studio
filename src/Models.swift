import Foundation

enum Mode: String, CaseIterable { case easy = "EASY", drum = "DRUM", key = "KEY" }

// Manual commands reserve their generation immediately, before queue submission.
final class ModeIntent {
    private let lock=NSLock()
    private var version: UInt64=0
    var current: UInt64 { lock.lock(); defer { lock.unlock() }; return version }
    @discardableResult func advance() -> UInt64 { lock.lock(); defer { lock.unlock() }; version += 1; return version }
}
struct Layout {
    // UI indices are top-to-bottom; vendor PAD numbers are bottom-to-top.
    static func pad(_ cell: Int) -> Int { (7-cell/8)*8 + cell%8 }
    static func drum(_ cell: Int) -> Int { let r=7-cell/8, c=cell%8; return 36+4*r+c%4+32*(c/4) }
    static func key(_ cell: Int) -> Int? {
        let r=7-cell/8, c=cell%8
        let offsets = r%2 == 0 ? [0,2,4,5,7,9,11,12] : [-1,1,3,-1,6,8,10,-1]
        return offsets[c] < 0 ? nil : 36+12*(r/2)+offsets[c]
    }
    static func cells(_ note: Int, mode: Mode, offset: Int = 0) -> [Int] {
        (0..<64).filter { cell in
            if mode == .key { return key(cell).map { $0+offset == note } ?? false }
            return drum(cell)+offset == note
        }
    }
    static func name(_ note: Int) -> String {
        let names = ["C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"]
        return names[(note%12+12)%12] + "\(note/12-1)"
    }
    static let drums = ["Deep Kick","Punch Kick","Snare","Rim Snare","Closed Hat","Open Hat","Clap","High Tom","Mid Tom","Low Tom","Crash","Ride","Cowbell","Shaker","Clave","Click"]
    static func sampleName(_ pad: Int) -> String {
        if pad < 16 { return drums[pad] }
        let j=pad-16; return "Pluck \(48+12*(j/5)+[0,2,4,7,9][j%5])"
    }
}
struct HeldVoice { let output: UInt8?; let cells: [Int]; let source: UInt32 }

// Queue-confined shared routing for hardware, pointer, accessibility and test events.
final class Performance {
    let audio: Audio
    var mode: Mode = .drum, offset = 0, count = 0
    var held: [String: HeldVoice] = [:]
    var onVisual: (([Int], Int, Bool, String) -> Void)?
    var onClear: (() -> Void)?
    init(audio: Audio) { self.audio = audio }
    func stop() { held = [:]; audio.hush(); onClear?() }
    func change(_ mode: Mode, offset: Int) { stop(); self.mode=mode; self.offset=offset }
    func release(_ owner: String) {
        guard let old=held.removeValue(forKey: owner) else { return }
        if let output=old.output, !held.values.contains(where: { $0.output == output }) {
            (mode == .key ? audio.piano : audio.drums).stopNote(output, onChannel: 0)
        }
        let unused=old.cells.filter { i in !held.values.contains { $0.cells.contains(i) } }
        onVisual?(unused, 0, false, "")
    }
    func remove(_ source: UInt32) {
        let owners=held.filter { $0.value.source == source }.map { $0.key }
        owners.forEach { release($0) }
        // A pedal may still be latched even after every physical key was released.
        // Disconnect is a global panic, including samples and mouse-owned notes.
        stop()
    }
    func hit(_ owner: String, note: Int, velocity: UInt8, source: UInt32 = 0, explicitCell: Int? = nil) {
        release(owner)
        let cells=explicitCell.map { [$0] } ?? Layout.cells(note, mode: mode, offset: offset)
        var output: UInt8?
        if mode == .easy {
            guard let cell=cells.first else { return }
            audio.easy(Layout.pad(cell), velocity: velocity)
        } else {
            guard (0...127).contains(note) else { return }
            let n=mode == .key ? UInt8(note) : drumNote(UInt8(note)); output=n
            (mode == .key ? audio.piano : audio.drums).startNote(n, withVelocity: velocity, onChannel: 0)
        }
        held[owner]=HeldVoice(output: output, cells: cells, source: source); count += 1
        let target=mode == .easy ? cells.first.map { Layout.sampleName(Layout.pad($0)) } ?? "" : "\(output!)"
        let detail="\(count) 次触发  ·  输入 \(note) → \(target)  ·  力度 \(velocity)" + (cells.count>1 ? "  ·  重复 C 同步显示" : "")
        onVisual?(cells, Int(velocity), true, detail)
    }
    func message(_ source: UInt32, _ msg: MIDIMessage) {
        if msg.status == 0xF0 {
            guard mode == .easy, msg.data.count == 1 else { return }
            let index=Int(msg.data[0]), note=36+(index%64)
            let owner="easy:\(source):\(index%64)"
            if index<64 { hit(owner, note: note, velocity: 100, source: source, explicitCell: Layout.cells(note,mode: .easy).first) } else { release(owner) }
            return
        }
        let kind=msg.status & 0xF0, channel=msg.status & 0x0F
        guard channel == 0 || (mode == .easy && channel == 2), msg.data.count == 2 else { return }
        let n=Int(msg.data[0]), v=msg.data[1], owner="\(source):\(channel):\(n)"
        if kind == 0x90 && v>0 { hit(owner, note: n, velocity: v, source: source) }
        else if kind == 0x80 || kind == 0x90 { release(owner) }
        else if kind == 0xB0 && mode == .key {
            audio.piano.sendController(msg.data[0], withValue: v, onChannel: 0)
        }
    }
}

func runModelTests() {
    precondition(Set((0..<64).map(Layout.drum)) == Set(36...99))
    precondition(Set((0..<64).map(Layout.pad)) == Set(0..<64))
    let keys=(0..<64).compactMap(Layout.key)
    precondition(keys.count == 52 && Set(keys) == Set(36...84))
    precondition(Layout.drum(0) == 64 && Layout.drum(63) == 71 && Layout.drum(56) == 36)
    precondition(Layout.cells(48, mode: .key).count == 2)
    precondition(MIDIInput.uniqueDestination([]) == nil)
    precondition(MIDIInput.uniqueDestination([1]) == 1)
    precondition(MIDIInput.uniqueDestination([1,2]) == nil)
    let intent=ModeIntent(), autoGeneration=intent.current
    let manualGeneration=intent.advance()
    precondition(autoGeneration != intent.current && manualGeneration == intent.current)
    var a=MIDIParser(), b=MIDIParser()
    precondition(a.feed([0x90,60]).isEmpty)
    precondition(b.feed([0x91,62,100]) == [MIDIMessage(status: 0x91, data: [62,100])])
    precondition(a.feed([0xF8,100,61,0]) == [MIDIMessage(status: 0x90, data: [60,100]), MIDIMessage(status: 0x90, data: [61,0])])
    precondition(a.feed([0xF0,4]).isEmpty)
    precondition(a.feed([0xF8,0xF7]) == [MIDIMessage(status: 0xF0, data: [4])])
    precondition(a.feed([60,127]).isEmpty)
    precondition(a.feed([0xF0,1,2,0xF7]) == [MIDIMessage(status: 0xF0,data: [1,2])])
    precondition(a.feed([0xF0]+Array(repeating: 1,count: 513)+[0xF7]).isEmpty)
    print("MODEL PASS: 64 DRUM/PAD positions; 52 KEY cells/49 pitches; endpoint parser isolation, running status, realtime, split EASY input")
}
