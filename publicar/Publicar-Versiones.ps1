param([string]$Version)
$ErrorActionPreference = 'Stop'
if (-not $Version) { $Version = Read-Host 'Versión nueva (por ejemplo 2026.9.29)' }
$dir = Join-Path $PSScriptRoot "salida/v$Version"
& (Join-Path $PSScriptRoot 'Preparar-Versiones.ps1') -Version $Version -OutputDirectory $dir
$repo = (Get-Content (Join-Path $PSScriptRoot 'repo.txt') -Raw).Trim()
Write-Host "Archivos preparados en $dir"
Write-Host 'Publicar hará que los jugadores reciban esta actualización.'
if ((Read-Host 'Escribe PUBLICAR para subir esta versión a GitHub') -cne 'PUBLICAR') { return }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Falta GitHub CLI. Sube los archivos preparados manualmente a una release de GitHub.' }
$assets = @(Get-ChildItem -LiteralPath $dir -File | Select-Object -ExpandProperty FullName)
$notes = Join-Path $PSScriptRoot 'notas-version.md'
& gh release create "v$Version" @assets --repo $repo --title "Ascension v$Version" --notes-file $notes
if ($LASTEXITCODE -ne 0) { throw 'GitHub no pudo publicar la versión.' }
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'ultima-version.txt'), $Version)
