param([string]$ModJar,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(!(Test-Path $ModJar)){throw 'Falta el JAR validado'}
if(Test-Path $OutputDirectory){throw 'Usa una carpeta de destino nueva'}
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
$cfgPath=Join-Path $PSScriptRoot 'tiers.json';$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PSScriptRoot $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw "Base modificada: $src"}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{
  $old=@($z.Entries|Where-Object FullName -like 'overrides/mods/hearthbound-*.jar')
  if($old.Count -ne 1){throw 'Se esperaba una única versión anterior de Hearthbound'}
  $old[0].Delete()
  [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$ModJar,('overrides/mods/'+[IO.Path]::GetFileName($ModJar)))
 }finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath($PSScriptRoot,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath -Encoding utf8
