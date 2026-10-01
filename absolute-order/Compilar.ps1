param([string]$Perfil = "$env:APPDATA/ModrinthApp/profiles/NeoForge 1.21.1")
$ErrorActionPreference = 'Stop'
$mods = Join-Path $Perfil 'mods'
if (!(Test-Path (Join-Path $mods 'sophisticatedcore-1.21.1-1.5.1.2341.jar'))) {
    throw 'No se encuentra la versión de Sophisticated Core usada para compilar. Indica -Perfil con la ruta del perfil.'
}
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :common:test :neoforge:build "-PstorageCompatDir=$mods" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas han fallado.' }
    Write-Host 'Mod generado en neoforge/build/libs. Instala únicamente el archivo terminado en neoforge-mc1.21.1.jar.'
} finally { Pop-Location }
