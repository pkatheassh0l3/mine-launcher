param([string]$PublisherDirectory,[string]$OutputDirectory,[string]$AbsoluteJar)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path $OutputDirectory){throw 'Usa una carpeta nueva'}
New-Item $OutputDirectory -ItemType Directory|Out-Null
$cfgPath=Join-Path $PublisherDirectory 'tiers.json'
$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
$mods=@("$PSScriptRoot/palegardenbackport-1.0.0-ascension.1.jar","$PSScriptRoot/compat/build/ascension-pale-compat-1.0.0-neoforge-1.21.1.jar",$AbsoluteJar)
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PublisherDirectory $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw 'Base modificada'}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{
  foreach($e in @($z.Entries|Where-Object FullName -like 'overrides/mods/AbsoluteOrder-*.jar')){$e.Delete()}
  foreach($m in $mods){[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,(Resolve-Path $m).Path,('overrides/mods/'+[IO.Path]::GetFileName($m)))}
  $root=(Resolve-Path "$PSScriptRoot/overrides").Path
  foreach($f in Get-ChildItem $root -Recurse -File){
   $path='overrides/'+[IO.Path]::GetRelativePath($root,$f.FullName).Replace('\','/')
   $e=$z.GetEntry($path);if($e){$e.Delete()}
   [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$f.FullName,$path)
  }
  $e=$z.GetEntry('modrinth.index.json');$r=[IO.StreamReader]::new($e.Open());try{$idx=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()};$e.Delete()
  foreach($meta in @('selected.json','terrablender.json')){
   $v=Get-Content "$PSScriptRoot/$meta" -Raw|ConvertFrom-Json;$f=$v.files|Where-Object primary
   $idx.files+= [pscustomobject]@{path='mods/'+$f.filename;hashes=$f.hashes;env=@{client='required';server='required'};downloads=@($f.url);fileSize=$f.size}
  }
  $w=[IO.StreamWriter]::new($z.CreateEntry('modrinth.index.json').Open());try{$w.Write(($idx|ConvertTo-Json -Depth 30))}finally{$w.Dispose()}
 }finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath((Resolve-Path $PublisherDirectory).Path,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath
