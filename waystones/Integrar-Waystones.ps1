param([string]$PublisherDirectory,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$overrides=Join-Path $PSScriptRoot 'overrides'
$recipeDir=Join-Path $overrides 'kubejs/data/waystones/recipe'
New-Item -ItemType Directory $recipeDir -Force|Out-Null
$jar=[IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot 'waystones-neoforge-1.21.1-21.1.46.jar'))
try {
 $waystoneItems=@($jar.Entries|Where-Object {$_.FullName -match '^assets/waystones/models/item/[^/]+\.json$' -and $_.Name -notmatch '_brushing_'}|ForEach-Object {'waystones:'+ $_.Name.Replace('.json','')})
 foreach($entry in @($jar.Entries|Where-Object FullName -match '^data/waystones/recipe/(waystone|.*_waystone)\.json$')){
  $reader=[IO.StreamReader]::new($entry.Open());try{$recipe=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
  $stone=if($entry.Name -eq 'waystone.json'){'minecraft:cobblestone'}else{$recipe.key.S.item}
  if(!$stone){throw "No se reconoce la variante $($entry.Name)"}
  $new=@{type='minecraft:crafting_shaped';category='misc';pattern=@('SCS','SFS','SCS');key=@{S=@{item=$stone};C=@{item='minecraft:copper_ingot'};F=@{tag='minecraft:coals'}};result=$recipe.result}
  $new|ConvertTo-Json -Depth 10|Set-Content (Join-Path $recipeDir $entry.Name) -Encoding utf8NoBOM
 }
}finally{$jar.Dispose()}
$mods=@('waystones','xaeros-minimap')|ForEach-Object{
 $v=Get-Content (Join-Path $PSScriptRoot "$_-version.json") -Raw|ConvertFrom-Json
 $f=$v.files|Where-Object primary
 if((Get-FileHash (Join-Path $PSScriptRoot $f.filename) -Algorithm SHA512).Hash -ne $f.hashes.sha512){throw 'Hash de mod incorrecto'}
 @{path="mods/$($f.filename)";hashes=$f.hashes;downloads=@($f.url);fileSize=$f.size;env=@{client='required';server=$(if($_ -eq 'waystones'){'required'}else{'unsupported'})}}
}
$cfgPath=Join-Path $PublisherDirectory 'tiers.json';$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
if(Test-Path $OutputDirectory){throw 'Usa destino nuevo'}
New-Item -ItemType Directory $OutputDirectory|Out-Null
foreach($tier in $cfg.tiers.PSObject.Properties){
 $src=Join-Path $PublisherDirectory $tier.Value.package
 if((Get-FileHash $src).Hash -ne $tier.Value.sha256){throw 'Base modificada'}
 $dest=Join-Path $OutputDirectory ([IO.Path]::GetFileName($src));Copy-Item $src $dest
 $z=[IO.Compression.ZipFile]::Open($dest,'Update')
 try{
  foreach($path in @('modrinth.index.json','overrides/kubejs/data/ascension/ascension/ages/copper.json')){
   $entry=$z.GetEntry($path);$r=[IO.StreamReader]::new($entry.Open());try{$json=$r.ReadToEnd()|ConvertFrom-Json}finally{$r.Dispose()}
   if($path -eq 'modrinth.index.json'){$json.files=@($json.files)+@($mods)}else{
    $json.locks.items=@($json.locks.items)+$waystoneItems|Sort-Object -Unique
    $json.showcase=@($json.showcase)+@('waystones:waystone')
    $out=Join-Path $overrides 'kubejs/data/ascension/ascension/ages/copper.json'
    New-Item -ItemType Directory (Split-Path $out) -Force|Out-Null
    $json|ConvertTo-Json -Depth 30|Set-Content $out -Encoding utf8NoBOM
   }
   $entry.Delete();$w=[IO.StreamWriter]::new($z.CreateEntry($path).Open(),[Text.UTF8Encoding]::new($false));try{$w.Write(($json|ConvertTo-Json -Depth 30))}finally{$w.Dispose()}
  }
  foreach($file in Get-ChildItem $recipeDir -Filter *.json){[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$file.FullName,('overrides/kubejs/data/waystones/recipe/'+$file.Name))}
 }finally{$z.Dispose()}
 $tier.Value.package=[IO.Path]::GetRelativePath((Resolve-Path $PublisherDirectory).Path,(Resolve-Path $dest).Path).Replace('\','/')
 $tier.Value.sha256=(Get-FileHash $dest).Hash
}
$cfg|ConvertTo-Json -Depth 10|Set-Content $cfgPath -Encoding utf8
