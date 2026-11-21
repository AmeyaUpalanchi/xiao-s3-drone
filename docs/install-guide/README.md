# Install guide (2 × A4)

`TDC_XIAO_S3_Drone_Install_Guide.pdf` is rendered from `install-guide.html` with the same
WebKit renderer and Sora fonts as the quick guide:

```bash
swiftc -O ../quick-guide/html2pdf.swift -o /tmp/html2pdf && /tmp/html2pdf install-guide.html TDC_XIAO_S3_Drone_Install_Guide.pdf 596 843 2
```
