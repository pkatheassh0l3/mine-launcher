param([string]$ReleaseDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$stage=$root
$release=if($ReleaseDirectory){$ReleaseDirectory}else{Join-Path $root 'publicar/salida/v2026.10.3'}
$env:TFC_NO_MAIN='1'
$env:TFC_SELF=Join-Path $release 'Instalar-TFC-Create.exe'
. "$stage/instalador/app.ps1"
function Assert($value,$message){if(-not $value){throw $message}}
$rel=Get-LatestRelease
Assert ($rel.tag_name -eq 'v2026.10.3' -and $rel.assets.Count -eq 5) 'Catálogo local incorrecto'
Assert ($Tiers.baja.MemMB -eq 4096 -and $Tiers.media.MemMB -eq 6144 -and $Tiers.alta.MemMB -eq 8192) 'RAM incorrecta'
Add-Type -AssemblyName PresentationFramework
$window=[Windows.Markup.XamlReader]::Parse($Xaml)
Assert ($window.Title -eq 'Ascension') 'Título incorrecto'
Assert ($null -ne $window.FindName('CardPatata')) 'Falta tarjeta PC patata'
Assert ($Tiers.patata.MemMB -eq 4096) 'RAM patata incorrecta'
$window.Close()
$data=Join-Path $env:TEMP ('ascension-launcher-test-'+[guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path "$data/profiles/Legacy","$data/profiles/Current" -Force)
@{packId=$PackId;tier='alta';version='1.0.0';files=@()}|ConvertTo-Json|Set-Content "$data/profiles/Legacy/$MarkerName"
@{packId=$PackId;tier='alta';version='2026.10.3';contentRevision=$ContentRevision;files=@()}|ConvertTo-Json|Set-Content "$data/profiles/Current/$MarkerName"
$instances=@(Get-Instances @([pscustomobject]@{DataDir=$data;Name='Test'}))
Assert ($instances.Count -eq 1 -and $instances[0].Name -eq 'Current') 'Se está migrando un perfil antiguo'
foreach($tier in @('patata','baja','intermedia','media','alta')){
    $path=Join-Path $data $tier
    [void](New-Item -ItemType Directory -Path "$path/mods","$path/saves/Partida","$path/config" -Force)
    'partida intacta'|Set-Content "$path/saves/Partida/level.dat"
    'Hearthbound anterior'|Set-Content "$path/mods/hearthbound-neoforge-1.21.1-1.6.4.jar"
    'mod retirado'|Set-Content "$path/mods/retirado.jar"
    'mod del jugador'|Set-Content "$path/mods/personal.jar"
    'lista personal intacta'|Set-Content "$path/servers.dat"
    'lang:es_es'|Set-Content "$path/options.txt"
    'enableShaders=false'|Set-Content "$path/config/iris.properties"
    $inst=[pscustomobject]@{Path=$path;Tier=$tier;Marker=[pscustomobject]@{files=@('mods/retirado.jar','mods/hearthbound-neoforge-1.21.1-1.6.4.jar');contentRevision=$ContentRevision}}
    $asset=Get-Asset $rel $tier
    $local=Join-Path $data "$tier.mrpack"
    Download $asset.browser_download_url $local
    Update-InstanceFromPack $inst $local
    Assert (Test-Path "$path/mods/FallingTree-1.21.1-1.21.1.11.jar") 'Falta FallingTree'
    Assert (Test-Path "$path/mods/xaeroworldmap-neoforge-1.21.1-1.46.0.jar") 'Falta el mapa'
    Assert (Test-Path "$path/mods/hearthbound-neoforge-1.21.1-1.6.5.jar") 'Falta Hearthbound nuevo'
    Assert (-not(Test-Path "$path/mods/hearthbound-neoforge-1.21.1-1.6.4.jar")) 'Sigue el Hearthbound anterior'
    Assert (-not(Test-Path "$path/mods/retirado.jar")) 'No se retiran mods gestionados obsoletos'
    Assert (Test-Path "$path/mods/personal.jar") 'Se ha tocado un mod del jugador'
    Assert ((Get-Content "$path/servers.dat" -Raw).Trim() -eq 'lista personal intacta') 'Se ha sobrescrito la lista de servidores'
    Assert (Test-Path "$path/mods/ascension-server-defaults-1.0.0-neoforge-1.21.1.jar") 'Falta el servidor automático'
    Assert ((Get-Content "$path/saves/Partida/level.dat") -eq 'partida intacta') 'Se ha tocado el mundo'
    Assert ((Get-Content "$path/options.txt") -eq 'lang:es_es') 'No se conservan las opciones'
    Assert ((Get-Content "$path/config/iris.properties") -eq 'enableShaders=false') 'No se conservan shaders personales'
    Assert (Test-Path "$path/mods/waystones-neoforge-1.21.1-21.1.46.jar") 'Falta Waystones'
    Assert (Test-Path "$path/mods/xaerominimap-neoforge-1.21.1-26.5.0.jar") 'Falta Minimap'
    Assert (@(Get-ChildItem "$path/mods" -Filter 'xmxw*').Count -eq 0) 'Integración duplicada incompatible'
    Assert (Test-Path "$path/mods/ascension-waystones-1.0.0-neoforge-1.21.1.jar") 'Falta menú de rutas'
    $wc=Get-Content "$path/config/waystones-common.toml" -Raw
    Assert ($wc -match 'defaultVisibility = "GLOBAL"' -and $wc -match 'chunksBetweenWildWaystones = 0' -and $wc -match 'spawnInVillages = "DISABLED"') 'Waystones públicas y sin generación mal configuradas'
    $recipe=Get-Content "$path/kubejs/data/waystones/recipe/waystone.json" -Raw|ConvertFrom-Json
    Assert (($recipe.pattern -join ',') -eq 'SCS,SFS,SCS' -and $recipe.key.S.item -eq 'minecraft:cobblestone' -and $recipe.key.C.item -eq 'minecraft:copper_ingot' -and $recipe.key.F.tag -eq 'minecraft:coals' -and $recipe.result.id -eq 'waystones:waystone') 'Receta de cobre incorrecta'
    $copper=Get-Content "$path/kubejs/data/ascension/ascension/ages/copper.json" -Raw|ConvertFrom-Json
    Assert ($copper.locks.items -contains 'waystones:waystone' -and $copper.showcase -contains 'waystones:waystone') 'Waystone no se desbloquea en cobre'
    Assert (Test-Path "$path/mods/pale-garden-remastered-v1.3.4.jar") 'Falta el mod oficial Pale Garden Remastered'
    Assert ((Get-FileHash "$path/mods/pale-garden-remastered-v1.3.4.jar" -Algorithm SHA512).Hash -eq 'c207a58175220ed70f1f3c9876d7b8d848d3aee636a0f72f9e8c0f2d2d293f8f43f2a28fdcba826a4030bb91bbf0d0f8fc54e17536796a9ffb610dcd91e13e3e') 'El JAR oficial ha sido modificado'
    Assert (Test-Path "$path/mods/ascension-pale-compat-1.0.0-neoforge-1.21.1.jar") 'Falta compatibilidad 1.21.1'
    $ft=Get-Content "$path/config/fallingtree.json" -Raw|ConvertFrom-Json
    Assert ($ft.trees.allowedLogs -contains 'palegardenbackport:pale_oak_log' -and $ft.trees.allowedLeaves -contains 'palegardenbackport:pale_oak_leaves' -and $ft.trees.maxSize -ge 512) 'FallingTree no preparado para robles pálidos'
    Assert (-not(Test-Path "$path/kubejs/data/ascension_pale/function/tick.mcfunction")) 'Se ha incluido la recreación descartada'
    $marker=Get-Content "$path/$MarkerName" -Raw|ConvertFrom-Json
    Assert ($marker.tier -eq $tier -and $marker.contentRevision -eq $ContentRevision -and $marker.version -eq '2026.10.3') 'Marcador incorrecto'
    Assert (@(Get-ChildItem "$path/mods" -Filter '*.jar').Count -eq $(if($tier -in @('patata','baja','intermedia')){71}else{72})) 'Cantidad incorrecta de mods'
    $inst.Marker=$marker
    Update-InstanceFromPack $inst $local
    Assert ((Get-Content "$path/saves/Partida/level.dat") -eq 'partida intacta') 'La segunda actualización altera el mundo'
    Write-Host "PASS $tier : instalación local, actualización repetida, mods gestionados, mundos y ajustes."
}
$legacy=[pscustomobject]@{Path="$data/profiles/Legacy";Tier='alta';Marker=[pscustomobject]@{files=@()}}
$blocked=$false
try{Update-InstanceFromPack $legacy (Join-Path $release 'TFC-Create_alta.mrpack')}catch{$blocked=$true}
Assert $blocked 'No se protege el pack antiguo'
Write-Host 'PASS catálogo local, RAM, interfaz WPF y separación de perfiles antiguos.'









