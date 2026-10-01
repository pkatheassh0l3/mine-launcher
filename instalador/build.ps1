param([string]$Go = 'go')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & $Go run github.com/akavel/rsrc@v0.10.2 -manifest app.manifest -ico icon.ico -o rsrc_windows_amd64.syso
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron compilar los recursos.' }
    $env:GOOS = 'windows'; $env:GOARCH = 'amd64'; $env:CGO_ENABLED = '0'
    & $Go build -trimpath -ldflags '-s -w -H windowsgui' -o ../publicar/Instalar-TFC-Create.exe .
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el instalador.' }
} finally { Pop-Location }
