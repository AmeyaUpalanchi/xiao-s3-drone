# The Drone Community: flash the drone and install the Android app in one go (Windows).
#   irm https://raw.githubusercontent.com/AmeyaUpalanchi/xiao-s3-drone/main/tools/installer/install.ps1 | iex
# Inside an unzipped release, double-click Install.bat instead.
$ErrorActionPreference = "Stop"
$raw  = "https://raw.githubusercontent.com/AmeyaUpalanchi/xiao-s3-drone/main/tools/installer/tdc_install.py"
$home_dir = Join-Path $env:USERPROFILE ".tdc-drone"
$venv = Join-Path $home_dir "venv"
$py   = Join-Path $venv "Scripts\python.exe"
New-Item -ItemType Directory -Force $home_dir | Out-Null

$sys = $null
foreach ($c in @("py", "python")) { if (Get-Command $c -ErrorAction SilentlyContinue) { $sys = $c; break } }
if (-not $sys) {
  Write-Host "Python 3 is needed. Installing it with winget..."
  winget install -e --id Python.Python.3.12 --accept-source-agreements --accept-package-agreements
  Write-Host "Close this window, open a new one and run the installer again."
  exit 1
}
if (-not (Test-Path $py)) { Write-Host "Setting up esptool (first run only)..."; & $sys -m venv $venv }
& $py -c "import esptool, serial" 2>$null
if ($LASTEXITCODE -ne 0) {
  & $py -m pip install -q --disable-pip-version-check --upgrade pip
  & $py -m pip install -q --disable-pip-version-check --prefer-binary "esptool==4.8.1" "pyserial>=3.5"
}

$here = if ($PSScriptRoot) { $PSScriptRoot } else { "" }
if ($here -and (Test-Path (Join-Path $here "tdc_install.py"))) {
  $script = Join-Path $here "tdc_install.py"
} else {
  $script = Join-Path $home_dir "tdc_install.py"
  Invoke-WebRequest -UseBasicParsing $raw -OutFile $script
}
$extra = @()
if ($here -and ((Test-Path (Join-Path $here "firmware")) -or (Get-ChildItem $here -Filter "*-full.bin" -ErrorAction SilentlyContinue))) {
  $extra = @("--dir", $here)
}
& $py $script @extra @args
