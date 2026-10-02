param([string]$MetadataDirectory,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path $OutputDirectory){throw 'Usa una carpeta nueva'}
$mods=@('fallingtree','xaeros-world-map') | ForEach-Object {
 $v=Get-Content (Join-Path $MetadataDirectory "$_-version.json") -Raw|ConvertFrom-Json
 $f=$v.files|Where-Object primary
 if((Get-FileHash (Join-Path $MetadataDirectory $f.filename) -Algorithm SHA512).Hash.ToLowerInvariant() -ne $f.hashes.sha512){throw 'Hash incorrecto'}
 [pscustomobject]@{path="mods/$($f.filename)";hashes=$f.hashes;env=@{client='required';server=$(if($_ -eq 'fallingtree'){'required'}else{'unsupported'})};downloads=@($f.url);fileSize=$f.size}
}
New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
$cfgPath=Join-Path $PSScriptRoot 'tiers.json';$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PSScriptRoot $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw 'Base modificada'}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{
  $e=$z.GetEntry('modrinth.index.json');$r=[IO.StreamReader]::new($e.Open())
  try{$index=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
  foreach($mod in $mods){if(@($index.files|Where-Object path -eq $mod.path).Count -or $z.GetEntry("overrides/$($mod.path)")){throw 'Mod duplicado'}}
  $index.files=@($index.files)+@($mods)
  $e.Delete();$w=[IO.StreamWriter]::new($z.CreateEntry('modrinth.index.json').Open(),[Text.UTF8Encoding]::new($false))
  try{$w.Write(($index|ConvertTo-Json -Depth 30))}finally{$w.Dispose()}
 }finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath($PSScriptRoot,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath -Encoding utf8
