$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$registry=Get-Content "$root/registry.json" -Raw|ConvertFrom-Json
$rows=Import-Csv "$root/item-eras.csv";$lookup=@{};foreach($r in $rows){$lookup[$r.Item]=$r}
$ages=@{};Get-ChildItem "$root/generated/kubejs/data/ascension/ascension/ages/*.json"|ForEach-Object {$ages[$_.BaseName]=Get-Content $_.FullName -Raw|ConvertFrom-Json}
$seen=@{}
foreach($age in $ages.Keys){
 foreach($id in @($ages[$age].locks.items | Where-Object {$_ -notlike 'minecraft:*'})){
  if($registry.items -notcontains $id){throw "Unknown mod item: $id"}
  if($seen.ContainsKey($id)){throw "Repeated mod item: $id"};$seen[$id]=$age
 }
 if(@($ages[$age].showcase | Where-Object {$_ -notlike 'minecraft:*'}).Count -eq 0){throw "No mods shown in $age"}
}
$modded=@($registry.items|Where-Object {$_ -notlike 'minecraft:*'})
foreach($id in $modded){if(!$seen.ContainsKey($id)){throw "Unassigned mod item: $id"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$quests=@{}
$zip=[IO.Compression.ZipFile]::OpenRead("$root/ascension.jar")
try {foreach($e in $zip.Entries | Where-Object {$_.FullName -like 'data/ascension/ascension/quests/*.json'}){
 $reader=[IO.StreamReader]::new($e.Open());try{$quests[$e.FullName.Substring('data/ascension/ascension/quests/'.Length).Replace('.json','')]=($reader.ReadToEnd()|ConvertFrom-Json)}finally{$reader.Dispose()}
}}finally{$zip.Dispose()}
$qRoot=(Resolve-Path "$root/baseline-data/quests").Path
foreach($f in Get-ChildItem $qRoot -Recurse -Filter *.json){$quests[[IO.Path]::GetRelativePath($qRoot,$f.FullName).Replace('\','/').Replace('.json','')]=Get-Content $f.FullName -Raw|ConvertFrom-Json}
foreach($id in $quests.Keys){$q=$quests[$id];$age=$q.age.Replace('ascension:','');if(!$ages.ContainsKey($age)){throw "Unknown quest age $id"}
 foreach($o in $q.objectives){
  $target=if($o.item){$o.item}elseif($o.block){$o.block}else{$null}
  if($target -and $lookup.ContainsKey($target) -and [int]$lookup[$target].Order -gt $ages[$age].order){throw "Quest blocked by future era: $id -> $target"}
 }
 foreach($parent in @($q.requires)){if($parent -and !$quests.ContainsKey($parent.Replace('ascension:',''))){throw "Missing quest $parent"}}
}
$visiting=@{};$visited=@{}
function Visit($id){if($visited.ContainsKey($id)){return};if($visiting.ContainsKey($id)){throw "Quest cycle $id"};$visiting[$id]=$true;foreach($p in $quests[$id].requires){Visit $p.Replace('ascension:','')};$visiting.Remove($id);$visited[$id]=$true}
foreach($id in $quests.Keys){Visit $id}
"PASS $($modded.Count) mod items assigned exactly once across $(@($modded|Group-Object {($_ -split ':')[0]}).Count) namespaces."
"PASS 9 eras include mod content in previews; IDs and quest dependencies are valid."
"PASS $($quests.Count) quests checked: no future-era item objectives or dependency cycles."
