// Read-only USB MIDI capture. No output ports and no device commands.
// swift tools/diagnostics/capture_input.swift [seconds]
import Foundation
import CoreMIDI
var client=MIDIClientRef(), port=MIDIPortRef()
let seconds=Double(CommandLine.arguments.dropFirst().first ?? "60") ?? 60
MIDIClientCreateWithBlock("Z1 Read-only Capture" as CFString,&client) { _ in }
MIDIInputPortCreateWithBlock(client,"Z1 Capture Input" as CFString,&port) { list,_ in
    var packet=UnsafeRawPointer(list).advanced(by: MemoryLayout<MIDIPacketList>.offset(of: \.packet)!).assumingMemoryBound(to: MIDIPacket.self)
    for _ in 0..<list.pointee.numPackets {
        let bytes=UnsafeRawBufferPointer(start: UnsafeRawPointer(packet).advanced(by: MemoryLayout<MIDIPacket>.offset(of: \.data)!),count: Int(packet.pointee.length))
        print(String(format: "%.3f",ProcessInfo.processInfo.systemUptime),bytes.map { String(format: "%02X",$0) }.joined(separator: " "))
        fflush(stdout); packet=UnsafePointer(MIDIPacketNext(packet))
    }
}
var connected=0
for i in 0..<MIDIGetNumberOfSources() {
    let e=MIDIGetSource(i); var value:Unmanaged<CFString>?
    MIDIObjectGetStringProperty(e,kMIDIPropertyDisplayName,&value)
    let name=value?.takeRetainedValue() as String? ?? ""
    if name.lowercased().contains("synido tempopad z-1") {
        let result=MIDIPortConnectSource(port,e,nil)
        print("CONNECTED",name,result); connected += result == noErr ? 1 : 0
    }
}
fflush(stdout)
if connected>0 { RunLoop.current.run(until: Date().addingTimeInterval(max(1,min(300,seconds)))) }
MIDIClientDispose(client)
print("CAPTURE END");fflush(stdout)
