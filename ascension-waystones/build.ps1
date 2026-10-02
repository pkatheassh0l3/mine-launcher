param([string]$Libraries = "$env:APPDATA/ModrinthApp/meta/libraries", [string]$ModsDirectory = "$env:APPDATA/ModrinthApp/profiles/NeoForge 1.21.1/mods")
$ErrorActionPreference='Stop'
$primary=@(
    "$Libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar",
    "$Libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar"
)
$extra=@('net/neoforged/neoforge/21.1.250/*universal.jar','net/neoforged/bus/8.0.5/*.jar','net/neoforged/fancymodloader/loader/4.0.44/*.jar','net/neoforged/mergetool/2.0.0/*api.jar','org/slf4j/slf4j-api/*/*.jar','com/google/guava/guava/*/*.jar','com/mojang/logging/*/*.jar','com/mojang/datafixerupper/*/*.jar','it/unimi/dsi/fastutil/*/*.jar','org/apache/logging/log4j/log4j-api/*/*.jar','org/apache/logging/log4j/log4j-core/*/*.jar','com/google/code/gson/gson/*/*.jar') | ForEach-Object { Get-ChildItem "$Libraries/$_" | Select-Object -ExpandProperty FullName }
$extra += @((Resolve-Path "$ModsDirectory/waystones-neoforge-1.21.1-21.1.46.jar").Path,(Resolve-Path "$ModsDirectory/balm-neoforge-1.21.1-21.0.65.jar").Path)
$extra += @(Get-ChildItem "$Libraries/org/joml/joml/*/*.jar","$Libraries/com/mojang/brigadier/*/*.jar","$Libraries/org/lwjgl/lwjgl/*/*windows*.jar" -ErrorAction SilentlyContinue|Select-Object -ExpandProperty FullName)
$cp=($primary + $extra) -join ';'
$out=Join-Path $PSScriptRoot 'build/classes'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot/src/main/java" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& javac --release 21 -encoding UTF-8 -cp $cp -d $out @sources
if($LASTEXITCODE){throw 'Compilation failed'}
Copy-Item "$PSScriptRoot/src/main/resources/*" $out -Recurse -Force
$jar=Join-Path $PSScriptRoot 'build/ascension-waystones-1.0.0-neoforge-1.21.1.jar'
& jar --create --file $jar -C $out .
if($LASTEXITCODE){throw 'JAR creation failed'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build/classpath.txt'),$cp)
Write-Output $jar


