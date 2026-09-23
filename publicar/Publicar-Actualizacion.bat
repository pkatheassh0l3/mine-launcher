<# : batch
@echo off
setlocal
set "TFC_SELF=%~f0"
set "TFC_ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -Command "iex ([IO.File]::ReadAllText($env:TFC_SELF,[Text.Encoding]::UTF8))"
pause
exit /b
: #>
# ==========================================================================
#  Publicar una actualización del modpack
#  - Lee tu perfil de Modrinth (mods + configs)
#  - Genera las 3 versiones (.mrpack): baja, media y alta
#  - Crea el "release" en GitHub para que a todos les llegue la actualización
# ==========================================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Here = Split-Path -Parent $env:TFC_SELF
function Log($m) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) }
$Gui = -not $env:TFC_TEST
if ($Gui) { Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName Microsoft.VisualBasic }
function Ask($prompt, $default) {
    if (-not $Gui) { return $default }
    return [Microsoft.VisualBasic.Interaction]::InputBox($prompt, 'Publicar actualización', $default)
}
function Msg($text, $buttons = 'OK', $icon = 'Information') {
    if (-not $Gui) { Log $text; return 'Yes' }
    return [string][System.Windows.Forms.MessageBox]::Show($text, 'Publicar actualización', $buttons, $icon)
}
function Read-Text($p) { [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8) }
$Utf8 = New-Object System.Text.UTF8Encoding($false)

try {
    $cfg = Read-Text (Join-Path $Here 'tiers.json') | ConvertFrom-Json

    # ---------- Perfil de origen ----------
    $srcFile = Join-Path $Here 'perfil-origen.txt'
    if (Test-Path -LiteralPath $srcFile) { $Source = (Read-Text $srcFile).Trim() }
    else { $Source = [IO.Path]::GetFullPath((Join-Path $Here '..\..')) }
    if ($env:TFC_TEST_SOURCE) { $Source = $env:TFC_TEST_SOURCE }
    if (-not (Test-Path -LiteralPath (Join-Path $Source 'mods'))) { throw "No encuentro la carpeta mods en el perfil de origen: $Source`n(Puedes poner otra ruta en perfil-origen.txt)" }
    Log "Perfil de origen: $Source"

    # ---------- Repositorio ----------
    $repoFile = Join-Path $Here 'repo.txt'
    $Repo = ''
    if (Test-Path -LiteralPath $repoFile) { $Repo = (Read-Text $repoFile).Trim() }
    if (-not $Repo) {
        $Repo = Ask "Escribe tu repositorio de GitHub como usuario/nombre`n(por ejemplo: pKa/tfc-create-pack)" ''
        if (-not $Repo) { return }
        [IO.File]::WriteAllText($repoFile, $Repo, $Utf8)
    }
    if ($Repo -notmatch '^[\w.-]+/[\w.-]+$') { throw "Repositorio no válido: '$Repo' (edita repo.txt)" }

    # ---------- Versión ----------
    $verFile = Join-Path $Here 'ultima-version.txt'
    $suggest = '1.0.0'
    if (Test-Path -LiteralPath $verFile) {
        $last = (Read-Text $verFile).Trim()
        $p = $last.Split('.'); if ($p.Count -eq 3) { $suggest = "$($p[0]).$($p[1]).$([int]$p[2] + 1)" }
    }
    $Version = Ask 'Número de la nueva versión (tiene que ser MAYOR que la anterior):' $suggest
    if ($env:TFC_TEST_VERSION) { $Version = $env:TFC_TEST_VERSION }
    if (-not $Version) { return }
    $Version = $Version.Trim() -replace '^[vV]', ''
    if ($Version -notmatch '^\d+\.\d+(\.\d+)?$') { throw "Versión no válida: $Version (usa el formato 1.2.3)" }
    $Notes = Ask '¿Qué ha cambiado? (se mostrará a los jugadores al actualizar)' 'Mejoras y correcciones.'
    if (-not $Notes) { $Notes = 'Mejoras y correcciones.' }

    # ---------- Versión de NeoForge ----------
    $NeoForge = $cfg.neoforgeFallback
    $log = Join-Path $Source 'logs\latest.log'
    if (Test-Path -LiteralPath $log) {
        $m = [regex]::Match((Read-Text $log), 'neoforge-(\d+\.\d+\.\d+(?:-beta)?)-universal')
        if ($m.Success) { $NeoForge = $m.Groups[1].Value }
    }
    Log "Minecraft $($cfg.minecraft) / NeoForge $NeoForge"

    # ---------- Mods del perfil ----------
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $Source 'mods') -Filter *.jar -File)
    Log "Calculando huellas de $($jars.Count) mods..."
    $sha = @{}
    foreach ($j in $jars) { $sha[$j.Name] = (Get-FileHash -LiteralPath $j.FullName -Algorithm SHA1).Hash.ToLowerInvariant() }

    Log 'Consultando Modrinth...'
    if ($env:TFC_MOCK_VF) { $vf = Read-Text $env:TFC_MOCK_VF | ConvertFrom-Json }
    else {
        $body = @{ hashes = @($sha.Values); algorithm = 'sha1' } | ConvertTo-Json
        $vf = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/version_files' -Method Post -Body $body `
              -ContentType 'application/json' -Headers @{ 'User-Agent' = "$($cfg.packId)-publisher" } -UseBasicParsing
    }

    $extraProjects = @{}
    foreach ($p in $cfg.catalog.PSObject.Properties) { $extraProjects[$p.Value.project] = $p.Name }

    $baseFiles = New-Object System.Collections.ArrayList
    $localJars = New-Object System.Collections.ArrayList
    foreach ($j in $jars) {
        $h = $sha[$j.Name]
        $v = $null
        if ($vf.PSObject.Properties[$h]) { $v = $vf.PSObject.Properties[$h].Value }
        if (-not $v) { [void]$localJars.Add($j); Log "  (no está en Modrinth, va incluido en el pack) $($j.Name)"; continue }
        if ($extraProjects.ContainsKey($v.project_id)) { Log "  (lo gestiona tiers.json, se ignora) $($j.Name)"; continue }
        $f = $v.files | Where-Object { $_.hashes.sha1 -eq $h } | Select-Object -First 1
        [void]$baseFiles.Add([ordered]@{
            path = "mods/$($j.Name)"
            hashes = [ordered]@{ sha1 = $h; sha512 = $f.hashes.sha512 }
            env = [ordered]@{ client = 'required'; server = 'required' }
            downloads = @($f.url)
            fileSize = [long]$f.size
        })
    }
    Log "Mods desde Modrinth: $($baseFiles.Count)   Incluidos en el pack: $($localJars.Count)"

    # ---------- Instalador con el repo ya puesto ----------
    $installer = (Read-Text (Join-Path $Here 'plantilla-instalador.bat')).Replace('__REPO__', $Repo)

    $OutDir = Join-Path $Here "salida\v$Version"
    [void](New-Item -ItemType Directory -Force -Path $OutDir)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    function Add-Text($zip, $name, $text) {
        $e = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
        $w = New-Object IO.StreamWriter($e.Open(), $Utf8); $w.Write($text); $w.Close()
    }

    $assets = @()
    foreach ($tp in $cfg.tiers.PSObject.Properties) {
        $tierKey = $tp.Name; $t = $tp.Value
        Log "Generando gama $tierKey..."
        $files = New-Object System.Collections.ArrayList
        foreach ($b in $baseFiles) { [void]$files.Add($b) }
        foreach ($x in $t.extras) {
            $c = $cfg.catalog.$x
            if (-not $c) { throw "tiers.json: '$x' no está en catalog" }
            $srv = 'optional'; if ($c.clientOnly) { $srv = 'unsupported' }
            [void]$files.Add([ordered]@{
                path = $c.path; hashes = [ordered]@{ sha1 = $c.sha1; sha512 = $c.sha512 }
                env = [ordered]@{ client = 'required'; server = $srv }
                downloads = @($c.url); fileSize = [long]$c.size })
        }
        $managed = @($files | ForEach-Object { $_.path }) + @($localJars | ForEach-Object { "mods/$($_.Name)" })

        $index = [ordered]@{
            formatVersion = 1; game = 'minecraft'; versionId = "$Version-$tierKey"
            name = $t.name; summary = "$($cfg.title) v$Version ($tierKey)"
            files = @($files)
            dependencies = [ordered]@{ minecraft = $cfg.minecraft; neoforge = $NeoForge }
        }
        $marker = [ordered]@{ packId = $cfg.packId; tier = $tierKey; version = $Version; repo = $Repo; files = $managed }

        $out = Join-Path $OutDir "$($cfg.assetPrefix)$tierKey.mrpack"
        if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }
        $zip = [IO.Compression.ZipFile]::Open($out, [IO.Compression.ZipArchiveMode]::Create)
        try {
            Add-Text $zip 'modrinth.index.json' ($index | ConvertTo-Json -Depth 20)
            Add-Text $zip "overrides/$($cfg.markerName)" ($marker | ConvertTo-Json -Depth 20)
            foreach ($j in $localJars) {
                [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $j.FullName, "overrides/mods/$($j.Name)", [IO.Compression.CompressionLevel]::Optimal)
            }
            # options.txt con los ajustes de la gama
            $optPath = Join-Path $Source 'options.txt'
            if (Test-Path -LiteralPath $optPath) {
                $opts = Read-Text $optPath
                foreach ($o in $t.options.PSObject.Properties) {
                    $rx = '(?m)^' + [regex]::Escape($o.Name) + ':.*$'
                    if ([regex]::IsMatch($opts, $rx)) { $opts = [regex]::Replace($opts, $rx, ($o.Name + ':' + $o.Value).Replace('$', '$$')) }
                    else { $opts = $opts.TrimEnd() + "`n$($o.Name):$($o.Value)`n" }
                }
                Add-Text $zip 'overrides/options.txt' $opts
            }
            # carpetas de configuración
            foreach ($folder in $cfg.includeFolders) {
                $fp = Join-Path $Source $folder
                if (-not (Test-Path -LiteralPath $fp)) { continue }
                foreach ($file in (Get-ChildItem -LiteralPath $fp -Recurse -File)) {
                    $rel = $folder + '/' + ($file.FullName.Substring($fp.Length + 1) -replace '\\', '/')
                    $repl = $null
                    if ($folder -eq 'config' -and $t.configs) { $repl = $t.configs.PSObject.Properties[$file.Name] }
                    if ($repl) {
                        $txt = Read-Text $file.FullName
                        foreach ($pair in $repl.Value) {
                            if ($txt.Contains($pair[0])) { $txt = $txt.Replace($pair[0], $pair[1]) }
                            else { Log "  AVISO ($tierKey): no encuentro '$($pair[0])' en $($file.Name)" }
                        }
                        Add-Text $zip "overrides/$rel" $txt
                    } else {
                        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, "overrides/$rel", [IO.Compression.CompressionLevel]::Optimal)
                    }
                }
            }
        } finally { $zip.Dispose() }
        Log ("  {0}  ({1:N1} MB)" -f (Split-Path -Leaf $out), ((Get-Item -LiteralPath $out).Length / 1MB))
        $assets += $out
    }
    $instPath = Join-Path $OutDir "Instalar-$($cfg.assetPrefix.TrimEnd('_')).bat"
    [IO.File]::WriteAllText($instPath, ($installer -replace "`r?`n", "`r`n"), $Utf8)
    $assets += $instPath
    [IO.File]::WriteAllText($verFile, $Version, $Utf8)

    # ---------- Subir a GitHub ----------
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($env:TFC_TEST) { $gh = $null }
    if ($gh) {
        Log 'Subiendo a GitHub con gh...'
        & gh release create "v$Version" @assets --repo $Repo --title "$($cfg.title) v$Version" --notes $Notes
        if ($LASTEXITCODE -ne 0) { throw 'gh no pudo crear el release (¿has hecho "gh auth login"?).' }
        Msg "¡Publicado! v$Version ya está en GitHub.`nLos jugadores la recibirán al abrir 'Actualizar $($cfg.title)'." | Out-Null
    } else {
        if ($Gui) {
            Start-Process explorer.exe $OutDir
            Start-Process ("https://github.com/$Repo/releases/new?tag=v$Version&title=" + [uri]::EscapeDataString("$($cfg.title) v$Version") + '&body=' + [uri]::EscapeDataString($Notes))
        }
        Msg ("Archivos listos en:`n$OutDir`n`n" +
             "Se ha abierto GitHub. Arrastra TODOS los archivos de esa carpeta a la zona 'Attach binaries'`n" +
             "y pulsa 'Publish release'.`n`n(Si instalas GitHub CLI y haces 'gh auth login', esto se hará solo.)") | Out-Null
    }
} catch {
    Msg "Error:`n`n$($_.Exception.Message)" 'OK' 'Error' | Out-Null
    if ($env:TFC_TEST) { throw }
}
