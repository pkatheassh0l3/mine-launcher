param([string]$Package)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ids=@{minecraft=$true;neoforge=$true;java=$true}
$required=[Collections.Generic.List[object]]::new()
function Inspect-Jar($z,$name){
 $meta=$z.GetEntry('META-INF/neoforge.mods.toml');if(!$meta){$meta=$z.GetEntry('META-INF/mods.toml')}
 if($meta){
  $r=[IO.StreamReader]::new($meta.Open());try{$t=$r.ReadToEnd()}finally{$r.Dispose()}
  $inline=[regex]::Match($t,'(?ms)^\s*mods\s*=\s*\[(.*?)\]')
  foreach($m in [regex]::Matches($inline.Groups[1].Value,'modId\s*=\s*[''"]([^''"]+)[''"]')){$ids[$m.Groups[1].Value]=$true}
  foreach($section in [regex]::Matches($t,'(?ms)^\[\[(?<kind>[^\]]+)\]\](?<body>.*?)(?=^\[|\z)')){
   $body=$section.Groups['body'].Value;$id=[regex]::Match($body,'(?m)^\s*modId\s*=\s*"([^"]+)"').Groups[1].Value
   if($section.Groups['kind'].Value -eq 'mods' -and $id){$ids[$id]=$true}
   if($section.Groups['kind'].Value.StartsWith('dependencies.') -and $body -match '(?m)^\s*(type\s*=\s*"required"|mandatory\s*=\s*true)' -and $body -notmatch '(?m)^\s*side\s*=\s*"SERVER"'){$required.Add(@{Id=$id;Source=$name})}
  }
 }
 foreach($nested in @($z.Entries | Where-Object FullName -match '\.jar$')){
  $s=[IO.MemoryStream]::new();$e=$nested.Open();try{$e.CopyTo($s)}finally{$e.Dispose()};$s.Position=0
  $n=[IO.Compression.ZipArchive]::new($s,[IO.Compression.ZipArchiveMode]::Read)
  try{Inspect-Jar $n "$name/$($nested.Name)"}finally{$n.Dispose();$s.Dispose()}
 }
}
$pack=[IO.Compression.ZipFile]::OpenRead($Package)
try{foreach($entry in @($pack.Entries | Where-Object FullName -match '^overrides/mods/.*\.jar$')){
 $s=[IO.MemoryStream]::new();$e=$entry.Open();try{$e.CopyTo($s)}finally{$e.Dispose()};$s.Position=0
 $jar=[IO.Compression.ZipArchive]::new($s,[IO.Compression.ZipArchiveMode]::Read)
 try{Inspect-Jar $jar $entry.Name}finally{$jar.Dispose();$s.Dispose()}
}
 $reader=[IO.StreamReader]::new($pack.GetEntry('modrinth.index.json').Open())
 try{$index=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
 $cache=Join-Path $env:TEMP 'ascension-dependency-cache'
 [void](New-Item -ItemType Directory -Path $cache -Force)
 foreach($file in @($index.files|Where-Object {$_.path -match '^mods/.*\.jar$' -and $_.env.client -ne 'unsupported'})){
  if($file.hashes.sha512 -notmatch '^[a-fA-F0-9]{128}$'){throw 'Falta SHA512 válido para una dependencia'}
  $local=Join-Path $cache ($file.hashes.sha512+'.jar')
  if(!(Test-Path $local) -or (Get-FileHash $local -Algorithm SHA512).Hash -ne $file.hashes.sha512){Invoke-WebRequest $file.downloads[0] -OutFile $local}
  if((Get-FileHash $local -Algorithm SHA512).Hash -ne $file.hashes.sha512){throw 'Descarga de dependencia dañada'}
  $jar=[IO.Compression.ZipFile]::OpenRead($local)
  try{Inspect-Jar $jar $file.path}finally{$jar.Dispose()}
 }
}finally{$pack.Dispose()}
$missing=@($required | Where-Object {!$ids.ContainsKey($_.Id)})
if($missing.Count){$missing | ForEach-Object {Write-Host "FALTA $($_.Id) requerido por $($_.Source)"};throw 'Dependencias obligatorias ausentes'}
Write-Host "PASS $(Split-Path $Package -Leaf): $($ids.Count) IDs presentes; $($required.Count) dependencias obligatorias de cliente resueltas."
