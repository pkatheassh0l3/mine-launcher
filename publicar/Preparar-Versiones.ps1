param([string]$Version = '2026.10.1', [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$cfg = Get-Content (Join-Path $PSScriptRoot 'tiers.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($Version -notmatch '^\d+\.\d+\.\d+(\.\d+)?$') { throw 'La versión debe tener el formato 2026.9.29.' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot "salida/v$Version" }
$repo = (Get-Content (Join-Path $PSScriptRoot 'repo.txt') -Raw).Trim()
$utf8 = New-Object Text.UTF8Encoding($false)
function Add-Json($zip, $path, $value) {
    $old = $zip.GetEntry($path)
    if ($old) { $old.Delete() }
    $entry = $zip.CreateEntry($path)
    $writer = New-Object IO.StreamWriter($entry.Open(), $utf8)
    try { $writer.Write(($value | ConvertTo-Json -Depth 30)) } finally { $writer.Dispose() }
}
# Comprobar todos los paquetes antes de escribir la salida.
foreach ($tier in $cfg.tiers.PSObject.Properties) {
    $source = Join-Path $PSScriptRoot $tier.Value.package
    if (-not (Test-Path -LiteralPath $source)) { throw "Falta el paquete validado: $source. Consulta PUBLICAR.md." }
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $tier.Value.sha256) { throw "La huella no coincide: $source" }
}
$exe = Join-Path $PSScriptRoot 'Instalar-TFC-Create.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw 'Compila primero el instalador.' }
if ((Get-FileHash $exe -Algorithm SHA256).Hash -ne $cfg.installerSha256) { throw 'El instalador no coincide con la compilación validada.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw "La carpeta ya existe: $OutputDirectory. Usa otra versión o salida." }
[void](New-Item -ItemType Directory -Path $OutputDirectory -Force)
foreach ($tier in $cfg.tiers.PSObject.Properties) {
    $out = Join-Path $OutputDirectory "$($cfg.assetPrefix)$($tier.Name).mrpack"
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $tier.Value.package) -Destination $out
    $zip = [IO.Compression.ZipFile]::Open($out, 'Update')
    try {
        $reader = New-Object IO.StreamReader($zip.GetEntry('modrinth.index.json').Open())
        try { $index = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $index.name = $tier.Value.name
        $index.versionId = "$Version-$($tier.Name)"
        $index.summary = "Ascension: nueve eras, misiones y bloqueos. Gama $($tier.Name)."
        $managed = @($zip.Entries | Where-Object { $_.FullName -match '^overrides/(mods|shaderpacks|resourcepacks)/' -and $_.Name } | ForEach-Object { $_.FullName.Substring(10) })
        $marker = [ordered]@{ packId=$cfg.packId; tier=$tier.Name; version=$Version; repo=$repo; contentRevision=$cfg.contentRevision; files=@($managed) + @($index.files | ForEach-Object { $_.path }) }
        Add-Json $zip 'modrinth.index.json' $index
        Add-Json $zip "overrides/$($cfg.markerName)" $marker
    } finally { $zip.Dispose() }
    Write-Host "Preparada gama $($tier.Name): $out"
}
Copy-Item -LiteralPath $exe -Destination $OutputDirectory
$release = [ordered]@{
    tag_name = "v$Version"
    body = 'Nueve eras, 126 misiones personalizadas y cinco perfiles gráficos, incluido PC patata con texturas 8x8. Absolute Order ascension.12.'
    assets = @(Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.mrpack' | ForEach-Object {
        [ordered]@{name=$_.Name;size=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'versiones.json'), ($release | ConvertTo-Json -Depth 10), $utf8)
Get-ChildItem -LiteralPath $OutputDirectory -File | Where-Object Extension -in '.mrpack','.exe' | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash)  $([IO.Path]::GetFileName($_.Path))" } | Set-Content (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding ASCII
Write-Host 'Preparación terminada. No se ha publicado en GitHub.'


