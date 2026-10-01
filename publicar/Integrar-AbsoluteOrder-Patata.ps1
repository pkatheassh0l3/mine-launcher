param([string]$BaseDirectory,[string]$AbsoluteOrderJar)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$utf8=[Text.UTF8Encoding]::new($false)
$cfgPath=Join-Path $PSScriptRoot 'tiers.json'
$cfg=Get-Content $cfgPath -Raw|ConvertFrom-Json
$packFile=Get-Content (Join-Path $PSScriptRoot 'f8thful-version.json') -Raw|ConvertFrom-Json
$f=$packFile.files | Where-Object primary | Select-Object -First 1
$cfg.tiers | Add-Member patata ([pscustomobject]@{name='Ascension - PC patata';package='paquetes-base/Ascension-PC-patata.mrpack';sha256='';memoryMB=4096}) -Force
function Read-ZipText($z,$path){$r=[IO.StreamReader]::new($z.GetEntry($path).Open());try{$r.ReadToEnd()}finally{$r.Dispose()}}
function Write-ZipText($z,$path,$text){$old=$z.GetEntry($path);if($old){$old.Delete()};$e=$z.CreateEntry($path);$w=[IO.StreamWriter]::new($e.Open(),$utf8);try{$w.Write($text)}finally{$w.Dispose()}}
New-Item -ItemType Directory (Join-Path $PSScriptRoot 'paquetes-base') -Force|Out-Null
foreach($tier in $cfg.tiers.PSObject.Properties){
 $base=if($tier.Name -eq 'patata'){'Ascension-Bajos-recursos.mrpack'}else{Split-Path $tier.Value.package -Leaf}
 $out=Join-Path $PSScriptRoot $tier.Value.package
 if(Test-Path $out){throw "Salida existente: $out"}
 Copy-Item (Join-Path $BaseDirectory $base) $out
 $z=[IO.Compression.ZipFile]::Open($out,'Update')
 try{
  foreach($e in @($z.Entries | Where-Object FullName -match '(?i)^overrides/mods/(absolute.?order|chestseparators).*\.jar$')){$e.Delete()}
  [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$AbsoluteOrderJar,('overrides/mods/'+(Split-Path $AbsoluteOrderJar -Leaf)))|Out-Null
  $index=Read-ZipText $z 'modrinth.index.json'|ConvertFrom-Json
  $index.files=@($index.files|Where-Object path -notmatch '(?i)^mods/(absolute.?order|chestseparators).*\.jar$')
  if($tier.Name -eq 'patata'){
   $index.name=$tier.Value.name
   $index.files+= [pscustomobject]@{path='resourcepacks/F8thful.zip';hashes=$f.hashes;env=@{client='required';server='unsupported'};downloads=@($f.url);fileSize=$f.size}
   $options=Read-ZipText $z 'overrides/options.txt'
   $settings=@{renderDistance='4';simulationDistance='4';mipmapLevels='0';ao='false';entityDistanceScaling='0.5';graphicsMode='0';particles='2';resourcePacks='["vanilla","file/F8thful.zip"]'}
   foreach($setting in $settings.GetEnumerator()){$options=[regex]::Replace($options,'(?m)^'+$setting.Key+':[^\r\n]*',$setting.Key+':'+$setting.Value)}
   Write-ZipText $z 'overrides/options.txt' $options
   Write-ZipText $z 'overrides/LEEME-PC-PATATA.txt' "F8thful 8x8 por Ewan Howell. https://modrinth.com/resourcepack/f8thful`nDescarga oficial mediante Modrinth; no se redistribuye su ZIP dentro de este paquete. Texturas reducidas de Minecraft; no sustituye todas las texturas de los mods. 4 GB asignados. Sin shaders."
  }
  Write-ZipText $z 'modrinth.index.json' ($index|ConvertTo-Json -Depth 30)
 }finally{$z.Dispose()}
 $tier.Value.sha256=(Get-FileHash $out -Algorithm SHA256).Hash
 Write-Host "PASS preparado $($tier.Name)"
}
$cfg.installerSha256=(Get-FileHash (Join-Path $PSScriptRoot 'Instalar-TFC-Create.exe') -Algorithm SHA256).Hash
[IO.File]::WriteAllText($cfgPath,($cfg|ConvertTo-Json -Depth 15),$utf8)
