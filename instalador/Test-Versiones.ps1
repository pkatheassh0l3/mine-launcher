param([string]$ReleaseDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$stage=$root
$release=if($ReleaseDirectory){$ReleaseDirectory}else{Join-Path $root 'publicar/salida/v2026.10.2.1'}
$env:TFC_NO_MAIN='1'
$env:TFC_SELF=Join-Path $release 'Instalar-TFC-Create.exe'
. "$stage/instalador/app.ps1"
function Assert($value,$message){if(-not $value){throw $message}}
$rel=Get-LatestRelease
Assert ($rel.tag_name -eq 'v2026.10.2.1' -and $rel.assets.Count -eq 5) 'Catálogo local incorrecto'
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
@{packId=$PackId;tier='alta';version='2026.10.2.1';contentRevision=$ContentRevision;files=@()}|ConvertTo-Json|Set-Content "$data/profiles/Current/$MarkerName"
$instances=@(Get-Instances @([pscustomobject]@{DataDir=$data;Name='Test'}))
Assert ($instances.Count -eq 1 -and $instances[0].Name -eq 'Current') 'Se está migrando un perfil antiguo'
foreach($tier in @('patata','baja','intermedia','media','alta')){
    $path=Join-Path $data $tier
    [void](New-Item -ItemType Directory -Path "$path/mods","$path/saves/Partida","$path/config" -Force)
    'partida intacta'|Set-Content "$path/saves/Partida/level.dat"
    'mod retirado'|Set-Content "$path/mods/retirado.jar"
    'mod del jugador'|Set-Content "$path/mods/personal.jar"
    'lang:es_es'|Set-Content "$path/options.txt"
    'enableShaders=false'|Set-Content "$path/config/iris.properties"
    $inst=[pscustomobject]@{Path=$path;Tier=$tier;Marker=[pscustomobject]@{files=@('mods/retirado.jar');contentRevision=$ContentRevision}}
    $asset=Get-Asset $rel $tier
    $local=Join-Path $data "$tier.mrpack"
    Download $asset.browser_download_url $local
    Update-InstanceFromPack $inst $local
    Assert (-not(Test-Path "$path/mods/retirado.jar")) 'No se retiran mods gestionados obsoletos'
    Assert (Test-Path "$path/mods/personal.jar") 'Se ha tocado un mod del jugador'
    Assert ((Get-Content "$path/saves/Partida/level.dat") -eq 'partida intacta') 'Se ha tocado el mundo'
    Assert ((Get-Content "$path/options.txt") -eq 'lang:es_es') 'No se conservan las opciones'
    Assert ((Get-Content "$path/config/iris.properties") -eq 'enableShaders=false') 'No se conservan shaders personales'
    $marker=Get-Content "$path/$MarkerName" -Raw|ConvertFrom-Json
    Assert ($marker.tier -eq $tier -and $marker.contentRevision -eq $ContentRevision -and $marker.version -eq '2026.10.2.1') 'Marcador incorrecto'
    Assert (@(Get-ChildItem "$path/mods" -Filter '*.jar').Count -eq $(if($tier -in @('patata','baja','intermedia')){61}else{62})) 'Cantidad incorrecta de mods'
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


