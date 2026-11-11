// html2pdf <input.html> <output.pdf> <pageWidth> <pageHeight> <pages>
// Renders fixed-size page <section>s stacked vertically into a multi-page vector PDF with WebKit.
import AppKit
import WebKit
import PDFKit

let a = CommandLine.arguments
let input = URL(fileURLWithPath: a[1])
let output = URL(fileURLWithPath: a[2])
let pw = CGFloat(Double(a[3])!), ph = CGFloat(Double(a[4])!)
let pages = Int(a[5])!

final class Renderer: NSObject, WKNavigationDelegate {
    let web: WKWebView
    var done = false
    override init() {
        web = WKWebView(frame: NSRect(x: 0, y: 0, width: pw, height: ph * CGFloat(pages)))
        super.init()
        web.navigationDelegate = self
    }
    func start() { web.loadFileURL(input, allowingReadAccessTo: input.deletingLastPathComponent()) }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        webView.evaluateJavaScript("document.fonts.ready.then(() => document.fonts.size)") { _, _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { self.render(0, PDFDocument()) }
        }
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        print("load failed: \(error)"); done = true
    }
    func render(_ i: Int, _ doc: PDFDocument) {
        if i == pages {
            doc.write(to: output); print("wrote \(output.path) pages=\(doc.pageCount)"); done = true; return
        }
        let cfg = WKPDFConfiguration()
        cfg.rect = CGRect(x: 0, y: CGFloat(i) * ph, width: pw, height: ph)
        web.createPDF(configuration: cfg) { result in
            switch result {
            case .success(let data):
                if let p = PDFDocument(data: data)?.page(at: 0) { doc.insert(p, at: doc.pageCount) }
                self.render(i + 1, doc)
            case .failure(let e):
                print("pdf failed: \(e)"); self.done = true
            }
        }
    }
}

let app = NSApplication.shared
let r = Renderer()
r.start()
while !r.done { RunLoop.main.run(until: Date().addingTimeInterval(0.05)) }
