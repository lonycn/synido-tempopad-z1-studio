import Foundation

struct RGB: Equatable {
    let r: UInt8, g: UInt8, b: UInt8
    static let black = RGB(r: 0,g: 0,b: 0)
    var brightness: Double { Double(max(r,max(g,b))) / 127 }
    static func hue(_ hue: Double, intensity: Double) -> RGB {
        let h=(hue-floor(hue))*6, x=1-abs(h.truncatingRemainder(dividingBy: 2)-1)
        let palette: [(Double,Double,Double)]=[(1,x,0),(x,1,0),(0,1,x),(0,x,1),(x,0,1),(1,0,x)]
        let (r,g,b)=palette[min(5,Int(h))]
        let gain=max(0,min(1,intensity))*100 // Restrained maximum, not full-bright strobes.
        return RGB(r: UInt8(r*gain),g: UInt8(g*gain),b: UInt8(b*gain))
    }
}
struct Diffusion {
    struct Wave { let cell: Int; let time: TimeInterval; let velocity: Double }
    private(set) var waves: [Wave] = []
    static let duration: Double = 1.15
    mutating func hit(_ cell: Int, velocity: Int, now: TimeInterval) {
        guard (0..<64).contains(cell) else { return }
        waves.append(Wave(cell: cell,time: now,velocity: Double(max(1,min(127,velocity)))/127))
        if waves.count > 12 { waves.removeFirst(waves.count-12) }
    }
    mutating func clear() { waves=[] }
    mutating func frame(now: TimeInterval) -> [RGB] {
        waves.removeAll { now-$0.time >= Self.duration }
        var frame=Array(repeating: RGB.black,count: 64)
        for wave in waves {
            let age=max(0,now-wave.time), radius=age*9
            let fade=pow(max(0,1-age/Self.duration),0.7)*wave.velocity
            for cell in 0..<64 {
                let dx=Double(cell%8-wave.cell%8), dy=Double(cell/8-wave.cell/8)
                let distance=sqrt(dx*dx+dy*dy)
                let ring=exp(-pow((distance-radius)/0.68,2))
                let center=distance<0.1 ? exp(-age*10) : 0
                let intensity=max(ring,center)*fade
                guard intensity>0.012 else { continue }
                let color=RGB.hue(Double(Layout.pad(wave.cell)%16)/16+age*0.12,intensity: intensity)
                let old=frame[cell]
                frame[cell]=RGB(r: max(old.r,color.r),g: max(old.g,color.g),b: max(old.b,color.b))
            }
        }
        return frame
    }
}

func runEffectTests() {
    var effect=Diffusion()
    effect.hit(27,velocity: 100,now: 1)
    let start=effect.frame(now: 1)
    precondition(start.count == 64 && start[27].brightness > 0.2)
    let expanded=effect.frame(now: 1.25)
    precondition(expanded.enumerated().contains { $0.offset != 27 && $0.element.brightness > 0.1 })
    precondition(effect.frame(now: 3).allSatisfy { $0 == .black } && effect.waves.isEmpty)
    for _ in 0..<100 { effect.hit(63,velocity: 127,now: 4) }
    precondition(effect.waves.count == 12)
    effect.clear(); precondition(effect.frame(now: 4).allSatisfy { $0 == .black })
    print("EFFECT PASS: center hit, expanding ring, final blackout, bounded polyphony")
}
