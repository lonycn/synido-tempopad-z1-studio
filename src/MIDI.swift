import Foundation
import CoreMIDI

struct MIDIMessage: Equatable {
    let status: UInt8
    let data: [UInt8]
}
struct MIDIParser {
    var status: UInt8 = 0
    var pending: [UInt8] = []
    var sysex: [UInt8]? = nil
    var sysexOverflow = false
    mutating func feed(_ bytes: [UInt8]) -> [MIDIMessage] {
        var result: [MIDIMessage] = []
        for byte in bytes {
            if byte >= 0xF8 { continue }
            if byte == 0xF0 { status = 0; pending = []; sysex = []; sysexOverflow = false; continue }
            if byte == 0xF7 {
                if let data = sysex, !sysexOverflow, !data.isEmpty { result.append(MIDIMessage(status: 0xF0, data: data)) }
                sysex = nil; status = 0; pending = []; continue
            }
            if byte >= 0x80 { sysex = nil; status = byte < 0xF0 ? byte : 0; pending = []; continue }
            if sysex != nil {
                // Bounded capture for verified mode/state reports and EASY input.
                if sysex!.count < 512 { sysex!.append(byte) } else { sysexOverflow = true }
                continue
            }
            guard status != 0 else { continue }
            pending.append(byte)
            let kind = status & 0xF0
            if pending.count == ((kind == 0xC0 || kind == 0xD0) ? 1 : 2) {
                result.append(MIDIMessage(status: status, data: pending)); pending = []
            }
        }
        return result
    }
}

// All callbacks and parsers live on this queue; AppKit is never accessed here.
final class MIDIInput {
    let queue: DispatchQueue
    var client = MIDIClientRef(), port = MIDIPortRef(), outputPort = MIDIPortRef()
    var destination: MIDIEndpointRef?
    var onOutputStatus: ((String) -> Void)?
    var onDestinationChange: (() -> Void)?
    var sources: Set<MIDIEndpointRef> = []
    var parsers: [MIDIEndpointRef: MIDIParser] = [:]
    var onMessage: ((MIDIEndpointRef, MIDIMessage) -> Void)?
    var onRemove: ((MIDIEndpointRef) -> Void)?
    var onStatus: ((String) -> Void)?
    init(queue: DispatchQueue) { self.queue = queue }
    func start() {
        let a = MIDIClientCreateWithBlock("Z1 Studio" as CFString, &client) { [weak self] _ in
            self?.queue.async { [weak self] in self?.connect() }
        }
        guard a == noErr else { onStatus?("MIDI 客户端错误 \(a)"); return }
        let b = MIDIInputPortCreateWithBlock(client, "Z1 Input" as CFString, &port) { [weak self] list, ref in
            guard let self = self else { return }
            let source = MIDIEndpointRef(UInt(bitPattern: ref))
            var p = UnsafeRawPointer(list).advanced(by: MemoryLayout<MIDIPacketList>.offset(of: \.packet)!).assumingMemoryBound(to: MIDIPacket.self)
            var bytes: [UInt8] = []
            for _ in 0..<list.pointee.numPackets {
                bytes += Array(UnsafeRawBufferPointer(start: UnsafeRawPointer(p).advanced(by: MemoryLayout<MIDIPacket>.offset(of: \.data)!), count: Int(p.pointee.length)))
                p = UnsafePointer(MIDIPacketNext(p))
            }
            let copy = bytes
            self.queue.async {
                guard self.sources.contains(source) else { return }
                var parser = self.parsers[source] ?? MIDIParser()
                let messages = parser.feed(copy); self.parsers[source] = parser
                messages.forEach { self.onMessage?(source, $0) }
            }
        }
        guard b == noErr else { onStatus?("MIDI 端口错误 \(b)"); return }
        let c=MIDIOutputPortCreate(client,"Z1 EASY RGB" as CFString,&outputPort)
        if c != noErr { onOutputStatus?("灯光输出端口错误 \(c)") }
        connect()
    }
    func name(_ e: MIDIEndpointRef) -> String {
        var value: Unmanaged<CFString>?
        MIDIObjectGetStringProperty(e, kMIDIPropertyDisplayName, &value)
        return value?.takeRetainedValue() as String? ?? "Unknown"
    }
    func connect() {
        guard port != 0 else { return }
        var available: Set<MIDIEndpointRef> = []
        for i in 0..<MIDIGetNumberOfSources() {
            let e = MIDIGetSource(i), n = name(e).lowercased()
            if n.contains("synido") || n.contains("tempopad") { available.insert(e) }
        }
        for e in sources.subtracting(available) {
            MIDIPortDisconnectSource(port, e); parsers[e] = nil; onRemove?(e); sources.remove(e)
        }
        var errors: [String] = []
        for e in available.subtracting(sources) {
            let code = MIDIPortConnectSource(port, e, UnsafeMutableRawPointer(bitPattern: UInt(e)))
            if code == noErr { sources.insert(e) } else { errors.append("\(name(e)): \(code)") }
        }
        let previous=destination
        var candidates: [MIDIEndpointRef]=[]
        for i in 0..<MIDIGetNumberOfDestinations() {
            let e=MIDIGetDestination(i)
            if isPhysicalZ1(e) { candidates.append(e) }
        }
        // One-device support only: fail closed rather than pick enumeration order.
        destination=Self.uniqueDestination(candidates)
        if destination != previous { onDestinationChange?() }
        onOutputStatus?(outputPort == 0 ? "RGB 输出端口错误" : candidates.count > 1 ? "未选择灯光设备：请仅连接一台 Z-1" : destination == nil ? "未发现实体 Z-1 灯光输出" : "USB RGB 可用 · 等待 EASY 敲击确认")
        onStatus?(errors.isEmpty ? (sources.isEmpty ? "未连接 · 可用鼠标试听" : "已连接 · " + sources.map { name($0) }.sorted().joined(separator: ", ")) : errors.joined(separator: ", "))
    }
    static func uniqueDestination(_ candidates: [MIDIEndpointRef]) -> MIDIEndpointRef? {
        candidates.count == 1 ? candidates[0] : nil
    }
    func isPhysicalZ1(_ e: MIDIEndpointRef) -> Bool {
        var entity=MIDIEntityRef(); MIDIEndpointGetEntity(e,&entity)
        return entity != 0 && name(e).lowercased() == "synido tempopad z-1"
    }
    func isLightingSource(_ source: MIDIEndpointRef) -> Bool {
        guard isPhysicalZ1(source), let destination=destination else { return false }
        var inputEntity=MIDIEntityRef(), outputEntity=MIDIEntityRef()
        var inputDevice=MIDIDeviceRef(), outputDevice=MIDIDeviceRef()
        MIDIEndpointGetEntity(source,&inputEntity); MIDIEndpointGetEntity(destination,&outputEntity)
        MIDIEntityGetDevice(inputEntity,&inputDevice); MIDIEntityGetDevice(outputEntity,&outputDevice)
        return inputDevice != 0 && inputDevice == outputDevice
    }
    // The ONLY output command exposed is the audited live EASY RGB frame.
    @discardableResult func sendRGB(_ colors: [RGB]) -> Bool {
        guard outputPort != 0, let destination=destination, colors.count == 64 else { return false }
        let bytes=LightingWire.frame(colors)
        let memory=UnsafeMutableRawPointer.allocate(byteCount: 1024,alignment: MemoryLayout<MIDIPacketList>.alignment)
        defer { memory.deallocate() }
        let list=memory.bindMemory(to: MIDIPacketList.self,capacity: 1)
        let first=MIDIPacketListInit(list)
        let added=bytes.withUnsafeBufferPointer { MIDIPacketListAdd(list,1024,first,0,$0.count,$0.baseAddress!) }
        guard added != nil else { onOutputStatus?("RGB 消息装包失败"); return false }
        let result=MIDISend(outputPort,destination,list)
        if result != noErr { onOutputStatus?("RGB 输出失败 \(result)") }
        return result == noErr
    }
    func stop() {
        if client != 0 { MIDIClientDispose(client); client = 0 }; port = 0; outputPort = 0; destination=nil; sources = []; parsers = [:]
    }
}
