import AppKit

struct SurfaceCell {
    let title: String
    let subtitle: String
    let enabled: Bool
    let hue: CGFloat
}
private func ink(_ text: String, in rect: NSRect, size: CGFloat, color: NSColor, weight: NSFont.Weight = .medium) {
    let paragraph = NSMutableParagraphStyle(); paragraph.alignment = .center
    (text as NSString).draw(in: rect, withAttributes: [.font: NSFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color, .paragraphStyle: paragraph])
}
final class PadTile: NSButton {
    var padInfo = SurfaceCell(title: "", subtitle: "", enabled: true, hue: 0.5)
    var index = 0
    weak var surface: PadSurface?
    var pressed = false, strength: CGFloat = 0
    var effectRGB = RGB.black
    override var acceptsFirstResponder: Bool { padInfo.enabled }
    override func draw(_ dirtyRect: NSRect) {
        let rect = bounds.insetBy(dx: 2, dy: 2)
        let shape = NSBezierPath(roundedRect: rect, xRadius: 9, yRadius: 9)
        let cell = padInfo
        let tint = effectRGB == .black ? NSColor(calibratedHue: cell.hue, saturation: 0.68, brightness: 0.95, alpha: 1) : NSColor(calibratedRed: CGFloat(effectRGB.r)/127, green: CGFloat(effectRGB.g)/127, blue: CGFloat(effectRGB.b)/127, alpha: 1)
        let glow = cell.enabled ? max(strength,CGFloat(effectRGB.brightness)) : 0
        NSGraphicsContext.saveGraphicsState(); shape.addClip()
        let whiteKey = surface?.mode == "KEY" && cell.enabled && !cell.subtitle.contains("♯")
        let base = NSColor(white: whiteKey ? 0.84 : cell.enabled ? 0.15 : 0.065, alpha: 1)
        NSGradient(starting: base.blended(withFraction: glow*0.65, of: tint)!, ending: NSColor(white: whiteKey ? 0.59 : 0.07, alpha: 1))!.draw(in: rect, angle: -90)
        NSGraphicsContext.restoreGraphicsState()
        (cell.enabled ? tint.withAlphaComponent(0.3 + glow*0.7) : NSColor(white: 0.17, alpha: 1)).setStroke()
        shape.lineWidth = pressed ? 2.6 : 1.2; shape.stroke()
        if cell.enabled {
            tint.withAlphaComponent(0.65 + glow*0.35).setFill()
            NSBezierPath(roundedRect: NSRect(x: rect.minX+13, y: rect.maxY-4, width: max(3, rect.width-26), height: 2), xRadius: 1, yRadius: 1).fill()
        }
        ink(cell.title, in: NSRect(x: 3, y: bounds.height*0.43, width: bounds.width-6, height: 19), size: min(13, bounds.width*0.18), color: cell.enabled ? NSColor(white: whiteKey ? 0.12 : 0.94, alpha: 1) : .darkGray, weight: .semibold)
        ink(cell.subtitle, in: NSRect(x: 2, y: bounds.height*0.18, width: bounds.width-4, height: 15), size: min(9, bounds.width*0.14), color: cell.enabled ? NSColor(white: whiteKey ? 0.28 : 0.52, alpha: 1) : .darkGray)
        if window?.firstResponder === self {
            NSColor.white.withAlphaComponent(0.7).setStroke(); shape.lineWidth = 2; shape.stroke()
        }
    }
    override func mouseDown(with event: NSEvent) { surface?.track(event) }
    override func mouseDragged(with event: NSEvent) { surface?.track(event) }
    override func mouseUp(with event: NSEvent) { surface?.releaseMouse() }
}

final class PadSurface: NSView {
    var mode = "DRUM" { didSet { updateModes() } }
    var cells: [SurfaceCell] = [] { didSet { updateCells() } }
    var onPadDown: ((Int) -> Void)?
    var onPadUp: ((Int) -> Void)?
    var onPadActivate: ((Int) -> Void)?
    var onMode: ((String) -> Void)?
    private var pads: [PadTile] = []
    private var modes: [NSButton] = []
    private var held: Int?
    private var flashes: [Int: TimeInterval] = [:]
    private var timer: Timer?
    override init(frame: NSRect) {
        super.init(frame: frame)
        for title in ["EASY", "DRUM", "KEY"] {
            let b = NSButton(title: title, target: self, action: #selector(selectMode(_:)))
            b.bezelStyle = .rounded; b.font = .systemFont(ofSize: 12, weight: .bold)
            b.isBordered = false; b.wantsLayer = true; b.layer?.cornerRadius = 6
            addSubview(b); modes.append(b)
        }
        for i in 0..<64 {
            let b = PadTile(frame: .zero); b.index = i; b.surface = self
            b.target = self; b.action = #selector(accessiblePad(_:)); b.isBordered = false
            addSubview(b); pads.append(b)
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    @objc private func selectMode(_ sender: NSButton) { onMode?(sender.title) }
    @objc private func accessiblePad(_ sender: PadTile) {
        guard sender.padInfo.enabled else { return }
        onPadActivate?(sender.index)
    }
    private func updateModes() {
        for b in modes {
            let selected = b.title == mode
            b.layer?.backgroundColor = (selected ? NSColor.systemMint : NSColor(white: 0.2, alpha: 1)).cgColor
            b.attributedTitle = NSAttributedString(string: b.title, attributes: [.font: NSFont.systemFont(ofSize: 12, weight: .bold), .foregroundColor: selected ? NSColor.black : NSColor.white])
            b.state = selected ? .on : .off
        }
    }
    private func updateCells() {
        for (i, b) in pads.enumerated() where cells.indices.contains(i) {
            b.padInfo = cells[i]; b.isEnabled = cells[i].enabled
            b.setAccessibilityLabel("第 \(i/8+1) 排 第 \(i%8+1) 列，\(cells[i].title)，\(cells[i].subtitle)")
            b.needsDisplay = true
        }
    }
    override func layout() {
        super.layout()
        let inset: CGFloat = 27, gap: CGFloat = 8
        let size = min((bounds.width-2*inset-7*gap)/8, (bounds.height-118-7*gap)/8)
        let width = size*8+gap*7, left = (bounds.width-width)/2
        for (i, b) in pads.enumerated() {
            b.frame = NSRect(x: left+CGFloat(i%8)*(size+gap), y: 40+CGFloat(7-i/8)*(size+gap), width: size, height: size)
        }
        for (i,b) in modes.enumerated() { b.frame = NSRect(x: bounds.width-300+CGFloat(i)*85, y: bounds.height-55, width: 79, height: 28) }
    }
    override func draw(_ dirtyRect: NSRect) {
        let chassis = NSBezierPath(roundedRect: bounds.insetBy(dx: 3, dy: 3), xRadius: 24, yRadius: 24)
        NSColor(white: 0.095, alpha: 1).setFill(); chassis.fill()
        NSColor(white: 0.23, alpha: 1).setStroke(); chassis.lineWidth = 1; chassis.stroke()
        ink("synido", in: NSRect(x: 25, y: bounds.height-51, width: 103, height: 27), size: 23, color: .white, weight: .heavy)
        ink("TEMPOPAD Z-1", in: NSRect(x: 130, y: bounds.height-47, width: 145, height: 19), size: 11, color: .gray, weight: .bold)
        ink("64 PADS   /   VELOCITY SENSITIVE   /   USB MIDI", in: NSRect(x: 40, y: 12, width: bounds.width-80, height: 16), size: 9, color: .gray)
    }
    func track(_ event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        let index = pads.firstIndex { $0.frame.contains(point) && $0.padInfo.enabled }
        if index != held {
            releaseMouse(); held = index
            if let index = index { window?.makeFirstResponder(pads[index]); onPadDown?(index) }
        }
    }
    func releaseMouse() { if let index = held { held = nil; onPadUp?(index) } }
    func flash(_ index: Int, velocity: Int, down: Bool) {
        guard pads.indices.contains(index) else { return }
        let pad = pads[index]; pad.pressed = down
        if down { pad.strength = max(0.35, CGFloat(velocity)/127) }
        flashes[index] = Date.timeIntervalSinceReferenceDate; pad.needsDisplay = true
        if timer == nil {
            let t = Timer(timeInterval: 1/30, repeats: true) { [weak self] _ in self?.animate() }
            timer = t; RunLoop.main.add(t, forMode: .common)
        }
    }
    private func animate() {
        let now = Date.timeIntervalSinceReferenceDate
        for (i,time) in flashes {
            let pad = pads[i]
            let duration = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 0.08 : 0.35
            if now-time > duration {
                pad.strength = pad.pressed ? 0.35 : 0; pad.needsDisplay = true; flashes[i] = nil
            } else if !pad.pressed { pad.strength *= 0.8; pad.needsDisplay = true }
        }
        if flashes.isEmpty { timer?.invalidate(); timer = nil }
    }
    func applyLights(_ colors: [RGB]) {
        guard colors.count == 64 else { return }
        for (i,color) in colors.enumerated() where pads[i].effectRGB != color {
            pads[i].effectRGB=color; pads[i].needsDisplay=true
        }
    }
    func clear() {
        releaseMouse(); flashes = [:]; timer?.invalidate(); timer = nil
        for pad in pads { pad.pressed = false; pad.strength = 0; pad.effectRGB = .black; pad.needsDisplay = true }
    }
    deinit { timer?.invalidate() }
}
