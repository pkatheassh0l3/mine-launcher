param([string]$ModsDirectory)
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
   if($section.Groups['kind'].Value.StartsWith('dependencies.') -and $body -match '(?m)^\s*(type\s*=\s*"required"|mandatory\s*=\s*true)' -and $body -notmatch '(?m)^\s*side\s*=\s*"CLIENT"'){$required.Add(@{Id=$id;Source=$name})}
  }
 }
 foreach($nested in @($z.Entries | Where-Object FullName -match '\.jar$')){
  $s=[IO.MemoryStream]::new();$e=$nested.Open();try{$e.CopyTo($s)}finally{$e.Dispose()};$s.Position=0
  $n=[IO.Compression.ZipArchive]::new($s,[IO.Compression.ZipArchiveMode]::Read)
  try{Inspect-Jar $n "$name/$($nested.Name)"}finally{$n.Dispose();$s.Dispose()}
 }
}
foreach($file in Get-ChildItem $ModsDirectory -Filter *.jar){
 $jar=[IO.Compression.ZipFile]::OpenRead($file.FullName)
 try{Inspect-Jar $jar $file.Name}finally{$jar.Dispose()}
}if(!$ids.ContainsKey("yet_another_config_lib_v3")){throw "Falta YetAnotherConfigLib: es obligatorio en las entregas de servidor de Ascension por petición del usuario."}
if(!$ids.ContainsKey("skinrestorer")){throw "Falta SkinRestorer: obligatorio en futuras entregas por petición del usuario."}
$missing=@($required | Where-Object {!$ids.ContainsKey($_.Id)})
if($missing.Count){$missing | ForEach-Object {Write-Host "FALTA $($_.Id) requerido por $($_.Source)"};throw 'Dependencias obligatorias ausentes'}
Write-Host "PASS servidor: $($ids.Count) IDs presentes; $($required.Count) dependencias obligatorias de servidor resueltas."


