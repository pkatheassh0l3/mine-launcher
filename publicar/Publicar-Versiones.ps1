param(
    [string]$Version,
    [string]$PreparedDirectory,
    [string]$GitHubCli = 'gh',
    [switch]$Publish,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
if (-not $Version) { $Version = Read-Host 'Versión nueva (por ejemplo 2026.10.2.1)' }
if ($Version -notmatch '^\d+\.\d+\.\d+(\.\d+)?$') { throw 'Formato de versión incorrecto.' }
$tag = "v$Version"
$dir = $PreparedDirectory
if (-not $dir) {
    $dir = Join-Path $PSScriptRoot "salida/$tag"
    if (-not (Test-Path -LiteralPath $dir)) {
        & (Join-Path $PSScriptRoot 'Preparar-Versiones.ps1') -Version $Version -OutputDirectory $dir
    }
}
$repo = (Get-Content (Join-Path $PSScriptRoot 'repo.txt') -Raw).Trim()
$cfg = Get-Content (Join-Path $PSScriptRoot 'tiers.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$manifest = Get-Content (Join-Path $dir 'versiones.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.tag_name -ne $tag) { throw 'La carpeta preparada pertenece a otra versión.' }
$hashes = @{}
foreach ($line in Get-Content (Join-Path $dir 'SHA256SUMS.txt')) {
    if ($line -notmatch '^([A-Fa-f0-9]{64})\s+([^/\\]+)$') { throw 'Lista de huellas incorrecta.' }
    $hashes[$Matches[2]] = $Matches[1]
}
$names = @($cfg.tiers.PSObject.Properties | ForEach-Object { "$($cfg.assetPrefix)$($_.Name).mrpack" })
$expected = @($names) + @('Instalar-TFC-Create.exe')
foreach ($name in $expected) {
    $path = Join-Path $dir $name
    if (-not $hashes.ContainsKey($name) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $hashes[$name]) {
        throw "Fichero ausente o modificado: $name"
    }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($tier in $cfg.tiers.PSObject.Properties) {
    $name = "$($cfg.assetPrefix)$($tier.Name).mrpack"
    $path = Join-Path $dir $name
    $asset = @($manifest.assets | Where-Object name -eq $name)
    if ($asset.Count -ne 1 -or $asset[0].sha256 -ne $hashes[$name] -or $asset[0].size -ne (Get-Item $path).Length) { throw "Manifiesto incorrecto: $name" }
    $zip = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $entry = $zip.GetEntry("overrides/$($cfg.markerName)")
        if (-not $entry) { throw "Falta el marcador: $name" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $marker = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($marker.version -ne $Version -or $marker.packId -ne $cfg.packId -or $marker.contentRevision -ne $cfg.contentRevision -or $marker.tier -ne $tier.Name) { throw "Marcador incompatible: $name" }
    } finally { $zip.Dispose() }
    & (Join-Path $PSScriptRoot '../instalador/Test-Dependencias.ps1') -Package $path
}
Write-Host "Versión $tag validada: cinco gamas y sus dependencias."
if ($ValidateOnly) { return }
if (-not $Publish -and (Read-Host 'Escribe PUBLICAR para distribuir esta actualización') -cne 'PUBLICAR') { return }
if (-not (Get-Command $GitHubCli -ErrorAction SilentlyContinue)) { throw 'Falta GitHub CLI. Instálalo o usa -GitHubCli con la ruta a gh.exe.' }
function Invoke-Gh([string[]]$Arguments) {
    $result = & $GitHubCli @Arguments
    if ($LASTEXITCODE -ne 0) { throw "GitHub no pudo completar: $($Arguments[0..1] -join ' '). Puedes reintentar; el borrador no se distribuye." }
    return $result
}
Invoke-Gh -Arguments @('auth', 'status') | Out-Host
$releases = (Invoke-Gh -Arguments @('api', "repos/$repo/releases?per_page=100")) | ConvertFrom-Json
foreach ($release in $releases) {
    $v = $release.tag_name -replace '^[vV]', ''
    if (-not $release.draft -and -not $release.prerelease -and $v -match '^\d+\.\d+\.\d+(\.\d+)?$' -and [version]$v -ge [version]$Version) {
        throw "Ya existe una versión pública igual o posterior ($($release.tag_name)). Usa un número nuevo."
    }
}
$existing = @($releases | Where-Object tag_name -eq $tag)
if ($existing.Count -gt 0 -and -not $existing[0].draft) { throw 'No se modifica una release ya publicada.' }
$notes = Join-Path $PSScriptRoot 'notas-version.md'
if ($existing.Count -eq 0) {
    Invoke-Gh -Arguments @('release', 'create', $tag, '--repo', $repo, '--draft', '--title', "Ascension $tag", '--notes-file', $notes) | Out-Host
} else {
    Invoke-Gh -Arguments @('release', 'edit', $tag, '--repo', $repo, '--notes-file', $notes) | Out-Host
}
# El canal público solo cambia después de subir y comprobar todos los archivos.
$uploadNames = $expected + @('versiones.json', 'SHA256SUMS.txt')
foreach ($name in $uploadNames) {
    Invoke-Gh -Arguments @('release', 'upload', $tag, (Join-Path $dir $name), '--repo', $repo, '--clobber') | Out-Host
}
$draft = (Invoke-Gh -Arguments @('release', 'view', $tag, '--repo', $repo, '--json', 'databaseId')) | ConvertFrom-Json
$remote = (Invoke-Gh -Arguments @('api', "repos/$repo/releases/$($draft.databaseId)/assets")) | ConvertFrom-Json
foreach ($name in $uploadNames) {
    $local = Get-Item -LiteralPath (Join-Path $dir $name)
    $found = @($remote | Where-Object name -eq $name)
    $sha = (Get-FileHash -LiteralPath $local.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($found.Count -ne 1 -or $found[0].size -ne $local.Length -or $found[0].state -ne 'uploaded' -or $found[0].digest -ne "sha256:$sha") { throw "La verificación de GitHub falló: $name. La versión queda como borrador." }
}
Invoke-Gh -Arguments @('release', 'edit', $tag, '--repo', $repo, '--draft=false', '--latest') | Out-Host
$latest = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{'User-Agent'='Ascension-publisher'}
if ($latest.tag_name -ne $tag) { throw 'GitHub todavía no devuelve esta versión como latest. Comprueba la publicación antes de repetir.' }
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'ultima-version.txt'), $Version)
Write-Host "Publicada y disponible para los instaladores existentes: $($latest.html_url)"

