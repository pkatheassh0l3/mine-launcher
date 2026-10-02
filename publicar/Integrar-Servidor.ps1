param([string]$ModJar, [string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not (Test-Path -LiteralPath $ModJar)) { throw 'Falta el mod de servidores' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Usa una carpeta nueva para conservar los paquetes anteriores' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$cfgPath=Join-Path $PSScriptRoot 'tiers.json'
$cfg=Get-Content $cfgPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($tier in $cfg.tiers.PSObject.Properties) {
    $src=Join-Path $PSScriptRoot $tier.Value.package
    if((Get-FileHash $src -Algorithm SHA256).Hash -ne $tier.Value.sha256){throw "Base modificada: $src"}
    $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src))
    Copy-Item $src $dest
    $zip=[IO.Compression.ZipFile]::Open($dest,'Update')
    try {
        foreach($entry in @($zip.Entries | Where-Object FullName -like 'overrides/mods/ascension-server-defaults-*.jar')){$entry.Delete()}
        if($zip.GetEntry('overrides/servers.dat')){throw 'El pack contiene servers.dat y podría sustituir listas personales'}
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$ModJar,('overrides/mods/'+[IO.Path]::GetFileName($ModJar)))
    } finally {$zip.Dispose()}
    $tier.Value.package=[IO.Path]::GetRelativePath($PSScriptRoot,(Resolve-Path $dest).Path).Replace('\','/')
    $tier.Value.sha256=(Get-FileHash $dest -Algorithm SHA256).Hash
}
$cfg | ConvertTo-Json -Depth 10 | Set-Content $cfgPath -Encoding utf8
