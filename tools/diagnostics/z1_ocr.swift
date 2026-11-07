import Foundation
import PDFKit
import Vision
import AppKit
let doc=PDFDocument(url:URL(fileURLWithPath:CommandLine.arguments[1]))!
for i in 0..<doc.pageCount {
 let p=doc.page(at:i)!;let image=p.thumbnail(of:NSSize(width:1600,height:1200),for:.mediaBox)
 var r=CGRect(origin:.zero,size:image.size)
 guard let cg=image.cgImage(forProposedRect:&r,context:nil,hints:nil) else {continue}
 let req=VNRecognizeTextRequest();req.recognitionLevel = .accurate;req.recognitionLanguages=["en-US","zh-Hans"]
 try? VNImageRequestHandler(cgImage:cg).perform([req])
 print("\n=== PAGE \(i+1) ===")
 for x in req.results ?? [] {if let s=x.topCandidates(1).first {print(s.string)}}
}
