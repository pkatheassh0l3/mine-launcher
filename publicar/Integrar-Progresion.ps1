param([string]$ProgressionDirectory,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$files=@(Get-ChildItem (Join-Path $ProgressionDirectory 'kubejs/data/ascension/ascension/ages') -Filter *.json)
if($files.Count -ne 9){throw 'Se requieren las nueve eras validadas'}
if(Test-Path $OutputDirectory){throw 'La carpeta de destino ya existe'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$cfgPath=Join-Path $PSScriptRoot 'tiers.json'
$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PSScriptRoot $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw "Base modificada: $src"}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{foreach($f in $files){$entry='overrides/kubejs/data/ascension/ascension/ages/'+$f.Name;$old=$z.GetEntry($entry);if($old){$old.Delete()};[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$f.FullName,$entry)}}finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath($PSScriptRoot,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath -Encoding utf8
