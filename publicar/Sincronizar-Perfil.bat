<# : batch
@echo off
setlocal
set "TFC_SELF=%~f0"
set "TFC_ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -Command "iex ([IO.File]::ReadAllText($env:TFC_SELF,[Text.Encoding]::UTF8))"
pause
exit /b
: #>
# ==========================================================================
#  Sincronizar tu perfil de origen con el repositorio
#  - Descarga los mods de mods-perfil.json que falten en tu perfil
#  - Copia la unificación (kubejs, config/almostunified, millenaire-custom)
# ==========================================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
$Here = Split-Path -Parent $env:TFC_SELF
function Log($m) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) }
function Read-Text($p) { [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8) }
Add-Type -AssemblyName System.Windows.Forms
function Msg($t, $i = 'Information') { [void][System.Windows.Forms.MessageBox]::Show($t, 'Sincronizar perfil', 'OK', $i) }

try {
    $Source = (Read-Text (Join-Path $Here 'perfil-origen.txt')).Trim()
    $mods = Join-Path $Source 'mods'
    if (-not (Test-Path -LiteralPath $mods)) { throw "No encuentro el perfil: $Source" }
    $running = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue |
                 Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($Source, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
    if ($running.Count) { throw 'Minecraft está abierto con este perfil. Ciérralo primero.' }

    $list = (Read-Text (Join-Path $Here 'mods-perfil.json') | ConvertFrom-Json).mods
    $n = 0; $new = 0
    foreach ($m in $list) {
        $n++
        $dest = Join-Path $mods $m.filename
        if (Test-Path -LiteralPath $dest) {
            if ((Get-FileHash -LiteralPath $dest -Algorithm SHA1).Hash.ToLowerInvariant() -eq $m.sha1) { continue }
        }
        Log ("({0}/{1}) Descargando {2}" -f $n, $list.Count, $m.filename)
        Invoke-WebRequest -Uri $m.url -OutFile "$dest.part" -UseBasicParsing -Headers @{ 'User-Agent' = 'tfc-create-sync' }
        if ((Get-FileHash -LiteralPath "$dest.part" -Algorithm SHA1).Hash.ToLowerInvariant() -ne $m.sha1) {
            Remove-Item -LiteralPath "$dest.part" -Force; throw "El archivo descargado de $($m.name) no es correcto."
        }
        Move-Item -LiteralPath "$dest.part" -Destination $dest -Force; $new++
    }
    Log "Mods nuevos: $new"

    $zipPath = Join-Path $Here 'unificacion.zip'
    if (Test-Path -LiteralPath $zipPath) {
        Log 'Copiando la unificación (kubejs, Almost Unified, Millénaire)...'
        foreach ($old in @('millenaire-custom\tfc_unificado', 'kubejs\data\unificado')) {
            $p = Join-Path $Source $old
            if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Recurse -Force }
        }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($zipPath)
        try {
            foreach ($e in $zip.Entries) {
                if (-not $e.Name) { continue }
                $dest = Join-Path $Source ($e.FullName -replace '/', '\')
                [void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest))
                [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
            }
        } finally { $zip.Dispose() }
    }
    Msg "Perfil sincronizado.`n`nMods nuevos descargados: $new`n`nAbre el perfil en Modrinth y prueba que arranca."
} catch {
    Msg "Error:`n`n$($_.Exception.Message)" 'Error'
}
