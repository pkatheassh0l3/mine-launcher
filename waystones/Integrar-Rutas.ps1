param([string]$PublisherDirectory,[string]$OutputDirectory,[string]$InterfaceJar)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path $OutputDirectory){throw 'Usa una carpeta nueva'}
$cfgPath=Join-Path $PublisherDirectory 'tiers.json';$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
New-Item -ItemType Directory $OutputDirectory|Out-Null
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PublisherDirectory $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw 'Base modificada'}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{
  [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,(Resolve-Path $InterfaceJar).Path,('overrides/mods/'+[IO.Path]::GetFileName($InterfaceJar)))
  [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,(Join-Path $PSScriptRoot 'overrides/config/waystones-common.toml'),'overrides/config/waystones-common.toml')
 }finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath((Resolve-Path $PublisherDirectory).Path,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath -Encoding utf8
