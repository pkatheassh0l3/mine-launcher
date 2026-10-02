$ErrorActionPreference='Stop'
$registry=Get-Content "$PSScriptRoot/registry.json" -Raw|ConvertFrom-Json
$ages=@{}
Get-ChildItem "$PSScriptRoot/baseline-data/ages/*.json" | ForEach-Object {$ages[$_.BaseName]=Get-Content $_.FullName -Raw|ConvertFrom-Json -AsHashtable}
# Preserve existing eras and progression; fill equipment gaps at its material's era.
$extra=@{
 copper=@('ramadandelight:copper_fanous')
 chainmail=@('spawn:casting_net','crittersandcompanions:pearl_necklace_1')
 iron=@('ramadandelight:redstone_fanous','ramadandelight:fanous')
 nether=@('ramadandelight:soul_fanous')
}
foreach($age in $extra.Keys){$ages[$age].locks.items+= $extra[$age]}
$assigned=@{}
foreach($id in $registry.items){
 $best='wood'
 foreach($age in $ages.Keys){foreach($pattern in $ages[$age].locks.items){
  $matches=if($pattern.StartsWith('@')){($id -split ':')[0] -eq $pattern.Substring(1)}else{$id -like $pattern}
  if($matches -and $ages[$age].order -gt $ages[$best].order){$best=$age}
 }}
 if ($id -like 'palegardenbackport:*resin*') { $best='stone' }
 if ($id -eq 'palegardenbackport:creaking_heart') { $best='iron' }
 $assigned[$id]=$best
}
# Every mod item has exactly one effective era, avoiding lower-era previews for higher-tier items.
foreach($age in $ages.Keys){
 $vanilla=@($ages[$age].locks.items | Where-Object {$_ -like 'minecraft:*' -or $_.StartsWith('#')})
 $modded=@($registry.items | Where-Object {$_ -notlike 'minecraft:*' -and $assigned[$_] -eq $age} | Sort-Object -Unique)
 $ages[$age].locks.items=@($modded)+@($vanilla)
}
$showcase=@{
 wood=@('minecraft:crafting_table','minecraft:chest','farmersdelight:cabbage','farmersdelight:rice','moredelight:wooden_knife','ramadandelight:chickpea','veggiesdelight:broccoli','stackedblocks:stacked_oak_logs','crittersandcompanions:silk','ribbits:swamp_daisy')
 stone=@('minecraft:stone_pickaxe','minecraft:furnace','farmersdelight:flint_knife','farmersdelight:cutting_board','farmersdelight:stove','moredelight:stone_knife')
 copper=@('minecraft:copper_pickaxe','create:andesite_alloy','create:shaft','create:cogwheel','create:large_cogwheel','create:water_wheel','create:andesite_casing','create:wrench','ramadandelight:copper_fanous','waystones:waystone')
 chainmail=@('minecraft:chainmail_chestplate','sophisticatedbackpacks:backpack','sophisticatedbackpacks:copper_backpack','spawn:casting_net','crittersandcompanions:pearl_necklace_1')
 iron=@('minecraft:iron_pickaxe','farmersdelight:iron_knife','farmersdelight:cooking_pot','create:mechanical_press','create:mechanical_mixer','create:millstone','create:encased_fan','create:belt_connector','sophisticatedbackpacks:iron_backpack','sophisticatedbackpacks:gold_backpack','naturalist:capture_net','crittersandcompanions:grappling_hook','spawn:blue_footed_boots')
 diamond=@('minecraft:diamond_pickaxe','farmersdelight:diamond_knife','sophisticatedbackpacks:diamond_backpack','sophisticatedbackpacks:advanced_pickup_upgrade','sophisticatedbackpacks:stack_upgrade_tier_2','sophisticatedbackpacks:stack_upgrade_tier_3','crittersandcompanions:diamond_dragonfly_armor','crittersandcompanions:pearl_necklace_2')
 nether=@('minecraft:netherite_pickaxe','farmersdelight:netherite_knife','create:brass_ingot','create:precision_mechanism','create:mechanical_crafter','create:mechanical_arm','create:steam_engine','create:track','create:crushing_wheel','sophisticatedbackpacks:netherite_backpack','crittersandcompanions:netherite_dragonfly_armor','ramadandelight:soul_fanous')
 end=@('simulated:navigation_table','simulated:altitude_sensor','simulated:gimbal_sensor','simulated:gyroscopic_mechanism','sophisticatedbackpacks:inception_upgrade','sophisticatedbackpacks:advanced_mob_catcher_upgrade','sophisticatedbackpacks:xp_pump_upgrade')
 ascended=@('minecraft:elytra','aeronautics:levitite','aeronautics:pearlescent_levitite','aeronautics:smart_propeller','aeronautics:gyroscopic_propeller_bearing','sophisticatedbackpacks:survival_infinity_upgrade')
}
# Add representative items for larger technical mods, without using creative-only entries.
foreach($entry in @(@('iron','hearthbound'),@('iron','whaleborne'),@('nether','railways'),@('nether','offroad'),@('nether','simulated'),@('end','aeronautics'))){
 $candidate=@($registry.items | Where-Object {$_ -like "$($entry[1]):*" -and $assigned[$_] -eq $entry[0] -and $_ -notmatch 'creative|spawn_egg|incomplete'} | Sort-Object)[0]
 if($candidate){$showcase[$entry[0]]+= $candidate}
}
$out=Join-Path $PSScriptRoot 'generated/kubejs/data/ascension/ascension/ages'
New-Item -ItemType Directory -Force $out | Out-Null
foreach($age in $ages.Keys){
 $preview=@($showcase[$age] | Where-Object {$registry.items -contains $_})
 foreach($id in $preview){if($assigned[$id] -ne $age -and -not ($age -eq 'wood' -and $assigned[$id] -eq 'wood')){throw "Preview has wrong era: $age -> $id ($($assigned[$id]))"}}
 if($preview.Count -gt 16){throw "Preview too long: $age"}
 $ages[$age].showcase=$preview
 $ages[$age]|ConvertTo-Json -Depth 30|Set-Content "$out/$age.json" -Encoding utf8NoBOM
}
$rows=foreach($id in $registry.items | Sort-Object){[pscustomobject]@{Item=$id;Mod=$id.Split(':')[0];Era=$assigned[$id];Order=$ages[$assigned[$id]].order}}
$rows|Export-Csv "$PSScriptRoot/item-eras.csv" -NoTypeInformation -Encoding utf8
$rows|Group-Object Era|ForEach-Object {"$($_.Name): $($_.Count) items"}



