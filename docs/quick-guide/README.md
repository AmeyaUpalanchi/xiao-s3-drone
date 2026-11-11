# Quick guide (2 × A4)

`XIAO_S3_Drone_Quick_Guide.pdf` is generated from `build_guide.py` (content and callout
positions) and rendered with WebKit on macOS:

```bash
python3 build_guide.py
swiftc -O html2pdf.swift -o /tmp/html2pdf && /tmp/html2pdf quick-guide.html XIAO_S3_Drone_Quick_Guide.pdf 596 843 2
```

Callout targets are pixel positions in `pcb1-top.png` (2000 × 2000 render of PCB1).
