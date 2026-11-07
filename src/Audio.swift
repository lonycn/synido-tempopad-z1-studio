import AVFoundation
import Foundation

let soundbank = URL(fileURLWithPath: "/System/Library/Components/CoreAudio.component/Contents/Resources/gs_instruments.dls")
let extraDrums: [UInt8] = [36,38,42,46,49,51,41,43,45,47,48,50,39,54,56,75,80,81]
func drumNote(_ note: UInt8) -> UInt8 {
    if (36...81).contains(note) { return note }
    if (82...99).contains(note) { return extraDrums[Int(note)-82] }
    return extraDrums[Int(note) % extraDrums.count]
}

final class Audio {
    let engine = AVAudioEngine(), piano = AVAudioUnitSampler(), drums = AVAudioUnitSampler()
    let limiter = AVAudioUnitEffect(audioComponentDescription: AudioComponentDescription(
        componentType: kAudioUnitType_Effect, componentSubType: kAudioUnitSubType_PeakLimiter,
        componentManufacturer: kAudioUnitManufacturer_Apple, componentFlags: 0, componentFlagsMask: 0))
    var samples: [AVAudioPCMBuffer] = []
    var voices: [AVAudioPlayerNode] = []
    private var nextVoice = 0
    var volume: Float = 0.65 { didSet { engine.mainMixerNode.outputVolume = volume } }

    init(offline: Bool = false) throws {
        engine.attach(piano); engine.attach(drums)
        engine.connect(piano, to: engine.mainMixerNode, format: nil)
        engine.connect(drums, to: engine.mainMixerNode, format: nil)
        try piano.loadSoundBankInstrument(at: soundbank, program: 0, bankMSB: 121, bankLSB: 0)
        try drums.loadSoundBankInstrument(at: soundbank, program: 0, bankMSB: 120, bankLSB: 0)
        let bundled = Bundle.main.resourceURL?.appendingPathComponent("Z1_64pads_source.wav")
        let source = bundled.flatMap { FileManager.default.fileExists(atPath: $0.path) ? $0 : nil }
            ?? projectRoot.appendingPathComponent("assets/audio/Z1_64pads_source.wav")
        let file = try AVAudioFile(forReading: source)
        guard file.length == 64 * 44100, file.processingFormat.sampleRate == 44100 else {
            throw NSError(domain: "Z1Audio", code: 1, userInfo: [NSLocalizedDescriptionKey: "EASY 素材应为 44.1kHz、64 秒"])
        }
        for _ in 0..<64 {
            let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: 44100)!
            try file.read(into: buffer, frameCount: 44100)
            guard buffer.frameLength == 44100 else { throw NSError(domain: "Z1Audio", code: 2) }
            samples.append(buffer)
        }
        for _ in 0..<32 {
            let voice = AVAudioPlayerNode(); engine.attach(voice)
            engine.connect(voice, to: engine.mainMixerNode, format: file.processingFormat)
            voices.append(voice)
        }
        engine.attach(limiter)
        engine.connect(engine.mainMixerNode, to: limiter, format: nil)
        engine.connect(limiter, to: engine.outputNode, format: nil)
        engine.mainMixerNode.outputVolume = volume
        if offline {
            try engine.enableManualRenderingMode(.offline,
                format: AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 2)!, maximumFrameCount: 4096)
        }
        try engine.start()
    }
    func easy(_ pad: Int, velocity: UInt8) {
        guard samples.indices.contains(pad) else { return }
        let voice = voices[nextVoice]; nextVoice = (nextVoice + 1) % voices.count
        voice.stop(); voice.volume = Float(velocity) / 127
        voice.scheduleBuffer(samples[pad]); voice.play()
    }
    func hush() {
        for n in UInt8(0)...UInt8(127) { piano.stopNote(n, onChannel: 0); drums.stopNote(n, onChannel: 0) }
        for sampler in [piano, drums] {
            sampler.sendController(64, withValue: 0, onChannel: 0)
            sampler.sendController(120, withValue: 0, onChannel: 0)
            sampler.sendController(123, withValue: 0, onChannel: 0)
        }
        voices.forEach { $0.stop() }
    }
}

func runAudioTests() throws {
    let audio = try Audio(offline: true)
    let buffer = AVAudioPCMBuffer(pcmFormat: audio.engine.manualRenderingFormat, frameCapacity: 4096)!
    func render() throws {
        let status = try audio.engine.renderOffline(4096, to: buffer)
        guard status == .success else { throw NSError(domain: "Z1Render", code: Int(status.rawValue)) }
    }
    func energy(_ play: () -> Void) throws -> Double {
        audio.hush()
        for _ in 0..<12 { try render() }
        play(); var sum = 0.0; var frames = 0
        for _ in 0..<3 {
            try render()
            for i in 0..<Int(buffer.frameLength) { let x = Double(buffer.floatChannelData![0][i]); sum += x*x }
            frames += Int(buffer.frameLength)
        }
        return sqrt(sum / Double(max(frames, 1)))
    }
    var silentDrums: [Int] = [], silentEasy: [Int] = []
    for n in 36...99 {
        let rms = try energy { audio.drums.startNote(drumNote(UInt8(n)), withVelocity: 100, onChannel: 0) }
        if rms < 0.00001 { silentDrums.append(n) }
    }
    for pad in 0..<64 {
        let rms = try energy { audio.easy(pad, velocity: 100) }
        if rms < 0.00001 { silentEasy.append(pad+1) }
    }
    let piano = try energy { audio.piano.startNote(60, withVelocity: 100, onChannel: 0) }
    print("DRUM64 silent notes: \(silentDrums); EASY64 silent pads: \(silentEasy); piano C4 RMS: \(piano)")
    guard silentDrums.isEmpty, silentEasy.isEmpty, piano > 0.00001 else { throw NSError(domain: "Z1SelfTest", code: 1) }
    let p=Performance(audio: audio); p.mode = .key
    p.hit("hardware",note: 60,velocity: 100,source: 1)
    p.hit("mouse",note: 60,velocity: 100)
    p.release("mouse"); precondition(p.held["hardware"]?.output == 60)
    p.remove(1); precondition(p.held.isEmpty)
    p.change(.easy,offset: 12)
    for i in 0..<64 {
        p.message(1,MIDIMessage(status: 0xF0,data: [UInt8(i)]))
        precondition(p.held.values.first?.cells == Layout.cells(i+36,mode: .easy))
        p.message(1,MIDIMessage(status: 0xF0,data: [UInt8(i+64)])); precondition(p.held.isEmpty)
    }
    p.stop()
    var peak: Float=0
    for pattern in [Array(0..<64), Array(repeating: 0,count: 32), Array(0..<16)] {
        audio.hush(); for _ in 0..<12 { try render() }
        for pad in pattern { audio.easy(pad,velocity: 127) }
        for _ in 0..<12 { try render(); for ch in 0..<2 { for i in 0..<Int(buffer.frameLength) { peak=max(peak,abs(buffer.floatChannelData![ch][i])) } } }
    }
    precondition(peak > 0.00001 && peak <= 1.01, "Polyphony clipping: \(peak)")
    audio.hush(); print("ROUTING PASS: shared-note ownership, disconnect panic, all 64 passive EASY messages; 32-voice peak: \(peak)")
}
