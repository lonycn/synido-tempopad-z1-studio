// Original Z1 Studio icon. Reproducible vector drawing, no third-party artwork.
// swift scripts/generate_icon.swift
import AppKit
import Foundation

let root=URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let fm=FileManager.default
let iconset=root.appendingPathComponent("generated/branding/Z1Studio.iconset")
let branding=root.appendingPathComponent("assets/branding")
let resources=root.appendingPathComponent("app/Resources")
for directory in [iconset,branding,resources] { try fm.createDirectory(at: directory,withIntermediateDirectories: true) }

func rounded(_ rect: NSRect, _ radius: CGFloat) -> NSBezierPath {
    NSBezierPath(roundedRect: rect,xRadius: radius,yRadius: radius)
}
func drawIcon() {
    NSGraphicsContext.saveGraphicsState()
    let shadow=NSShadow(); shadow.shadowColor=NSColor.black.withAlphaComponent(0.42)
    shadow.shadowBlurRadius=35; shadow.shadowOffset=NSSize(width: 0,height: -18); shadow.set()
    let body=rounded(NSRect(x: 88,y: 96,width: 848,height: 848),180)
    NSColor(calibratedRed: 0.055,green: 0.065,blue: 0.10,alpha: 1).setFill(); body.fill()
    NSGraphicsContext.restoreGraphicsState()
    NSGraphicsContext.saveGraphicsState(); body.addClip()
    NSGradient(starting: NSColor(calibratedRed: 0.16,green: 0.19,blue: 0.25,alpha: 1),ending: NSColor(calibratedRed: 0.035,green: 0.045,blue: 0.075,alpha: 1))!.draw(in: body.bounds,angle: -75)
    NSGraphicsContext.restoreGraphicsState()
    NSColor.white.withAlphaComponent(0.18).setStroke(); body.lineWidth=2; body.stroke()
    let face=rounded(NSRect(x: 126,y: 132,width: 772,height: 772),139)
    NSColor(calibratedRed: 0.035,green: 0.043,blue: 0.068,alpha: 1).setFill(); face.fill()
    NSColor.white.withAlphaComponent(0.07).setStroke(); face.lineWidth=2; face.stroke()
    let stripe=rounded(NSRect(x: 185,y: 855,width: 112,height: 10),5)
    NSColor(calibratedRed: 0.17,green: 0.95,blue: 0.82,alpha: 1).setFill(); stripe.fill()
    let title="Z1 STUDIO" as NSString
    title.draw(at: NSPoint(x: 564,y: 842),withAttributes: [.font: NSFont.systemFont(ofSize: 31,weight: .bold),.foregroundColor: NSColor(white: 0.91,alpha: 1),.kern: 2])
    for row in 0..<8 {
        for col in 0..<8 {
            let isZ=(row == 0 || row == 7 || col == 7-row)
            let hue=CGFloat(col)*0.075+CGFloat(row)*0.033+0.40
            let color=NSColor(calibratedHue: hue.truncatingRemainder(dividingBy: 1),saturation: 0.82,brightness: 1,alpha: 1)
            let rect=NSRect(x: 184+CGFloat(col)*84,y: 170+CGFloat(7-row)*84,width: 68,height: 68)
            let pad=rounded(rect,15)
            NSGraphicsContext.saveGraphicsState()
            if isZ {
                let glow=NSShadow(); glow.shadowColor=color.withAlphaComponent(0.58); glow.shadowBlurRadius=19; glow.shadowOffset = .zero; glow.set()
            }
            color.withAlphaComponent(isZ ? 0.90 : 0.10).setFill(); pad.fill()
            NSGraphicsContext.restoreGraphicsState()
            color.withAlphaComponent(isZ ? 1 : 0.40).setStroke(); pad.lineWidth=isZ ? 2.5 : 1.5; pad.stroke()
            let shine=rounded(NSRect(x: rect.minX+11,y: rect.maxY-9,width: rect.width-22,height: 3),1.5)
            (isZ ? NSColor.white.withAlphaComponent(0.54) : color.withAlphaComponent(0.32)).setFill(); shine.fill()
        }
    }
}
func png(_ size: Int) throws -> Data {
    let bitmap=NSBitmapImageRep(bitmapDataPlanes: nil,pixelsWide: size,pixelsHigh: size,bitsPerSample: 8,samplesPerPixel: 4,hasAlpha: true,isPlanar: false,colorSpaceName: .deviceRGB,bytesPerRow: 0,bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current=NSGraphicsContext(bitmapImageRep: bitmap)
    NSColor.clear.setFill(); NSRect(x: 0,y: 0,width: size,height: size).fill(using: .copy)
    let transform=NSAffineTransform(); transform.scale(by: CGFloat(size)/1024); transform.concat()
    drawIcon(); NSGraphicsContext.restoreGraphicsState()
    return bitmap.representation(using: .png,properties: [:])!
}
for base in [16,32,128,256,512] {
    for scale in [1,2] {
        let name="icon_\(base)x\(base)" + (scale == 2 ? "@2x" : "") + ".png"
        try png(base*scale).write(to: iconset.appendingPathComponent(name))
    }
}
try png(1024).write(to: branding.appendingPathComponent("Z1Studio-1024.png"))
let tool=Process(); tool.executableURL=URL(fileURLWithPath: "/usr/bin/iconutil")
tool.arguments=["-c","icns",iconset.path,"-o",resources.appendingPathComponent("AppIcon.icns").path]
try tool.run(); tool.waitUntilExit()
guard tool.terminationStatus == 0 else { exit(tool.terminationStatus) }
print("Generated original icon: \(resources.appendingPathComponent("AppIcon.icns").path)")
