import Foundation
import CoreMIDI
var client=MIDIClientRef(),source=MIDIEndpointRef()
MIDIClientCreateWithBlock("Z1 Local Test" as CFString,&client){_ in}
MIDISourceCreate(client,"Synido Z1 Local Test" as CFString,&source)
RunLoop.current.run(until:Date().addingTimeInterval(1))
func send(_ data:[UInt8]) {
 var list=MIDIPacketList()
 withUnsafeMutablePointer(to:&list) { p in
  let packet=MIDIPacketListInit(p)
  data.withUnsafeBufferPointer { b in _=MIDIPacketListAdd(p,MemoryLayout<MIDIPacketList>.size,packet,0,b.count,b.baseAddress!) }
  MIDIReceived(source,p)
 }
}
for n:UInt8 in [36,81,82,99] {send([0x90,n,1]);Thread.sleep(forTimeInterval:0.06);send([0x80,n,0])}
Thread.sleep(forTimeInterval:0.3)
MIDIClientDispose(client)
