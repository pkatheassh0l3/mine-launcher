<# : batch
@echo off
setlocal
set "TFC_SELF=%~f0"
set "TFC_ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -Command "iex ([IO.File]::ReadAllText($env:TFC_SELF,[Text.Encoding]::UTF8))"

exit /b
: #>
# ==========================================================================
#  Instalador / actualizador del modpack
#  Funciona con Modrinth App y con Migurinth.
# ==========================================================================

# ---------------- CONFIGURACIÓN (la rellena el script de publicación) -----
$Repo       = '__REPO__'          # usuario/repositorio de GitHub
$PackId     = 'tfc-create'
$PackTitle  = 'TFC Create'
$MarkerName = 'tfc-create-pack.json'
$AssetPrefix = 'TFC-Create_'
# -------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Tiers = [ordered]@{
    baja  = @{ Label = 'Gama baja  (8 GB RAM, sin gráfica dedicada)';  Ram = '4 GB (4096 MB), 5 GB como mucho' }
    media = @{ Label = 'Gama media (16 GB RAM, gráfica antigua)';      Ram = '8 GB (8192 MB)' }
    alta  = @{ Label = 'Gama alta  (32 GB RAM, gráfica tipo RTX 3060)'; Ram = '10-12 GB (10240-12288 MB)' }
}

$script:AutoMode = $false
function Log($msg) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $msg) }

function Init-Gui {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()
}
function Msg($text, $buttons = 'OK', $icon = 'Information') {
    if ($script:AutoMode) { Log $text; return 'OK' }
    return [string][System.Windows.Forms.MessageBox]::Show($text, "$PackTitle - Instalador", $buttons, $icon)
}

# ---------------- Utilidades ----------------
function Join-SafePath([string]$root, [string]$rel) {
    if ([string]::IsNullOrWhiteSpace($rel) -or $rel -match '(^|[\\/])\.\.([\\/]|$)' -or $rel -match '^[\\/]' -or $rel -match '^[A-Za-z]:') {
        throw "Ruta no válida dentro del paquete: $rel"
    }
    return [IO.Path]::Combine($root, ($rel -replace '/', [IO.Path]::DirectorySeparatorChar))
}
function Get-Sha1([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() }
function ConvertTo-Ver([string]$v) {
    $clean = ($v -replace '^[vV]', '') -replace '[^0-9.].*$', ''
    $parts = @($clean.Split('.') | Where-Object { $_ -ne '' })
    while ($parts.Count -lt 2) { $parts += '0' }
    try { return [version]($parts[0..([Math]::Min(3, $parts.Count - 1))] -join '.') } catch { return [version]'0.0' }
}
function Download([string]$url, [string]$dest) {
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -Headers @{ 'User-Agent' = "$PackId-installer" }
}

# ---------------- Launchers ----------------
function Get-Launchers {
    $defs = @(
        @{ Name = 'Modrinth App'; Data = (Join-Path $env:APPDATA 'ModrinthApp'); Reg = 'Modrinth App*';
           Exe = @("$env:LOCALAPPDATA\Modrinth App\Modrinth App.exe", "$env:LOCALAPPDATA\Programs\Modrinth App\Modrinth App.exe", "$env:ProgramFiles\Modrinth App\Modrinth App.exe") },
        @{ Name = 'Migurinth'; Data = (Join-Path $env:APPDATA 'Migurinth'); Reg = 'Migurinth*';
           Exe = @("$env:LOCALAPPDATA\Migurinth\Migurinth.exe", "$env:LOCALAPPDATA\Programs\Migurinth\Migurinth.exe", "$env:ProgramFiles\Migurinth\Migurinth.exe") }
    )
    $keys = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    $reg = @(Get-ItemProperty $keys -ErrorAction SilentlyContinue)
    foreach ($d in $defs) {
        $exe = $null
        foreach ($r in ($reg | Where-Object { $_.DisplayName -like $d.Reg })) {
            $cands = @()
            if ($r.DisplayIcon) { $cands += (($r.DisplayIcon -replace '"', '') -replace ',\s*-?\d+$', '') }
            if ($r.InstallLocation) {
                $il = $r.InstallLocation.Trim('"')
                $cands += @(Get-ChildItem -LiteralPath $il -Filter *.exe -ErrorAction SilentlyContinue |
                            Where-Object { $_.Name -notmatch 'uninst' } | Select-Object -ExpandProperty FullName)
            }
            foreach ($c in $cands) { if ($c -and $c -like '*.exe' -and $c -notmatch 'uninst' -and (Test-Path -LiteralPath $c)) { $exe = $c; break } }
            if ($exe) { break }
        }
        if (-not $exe) { $exe = $d.Exe | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1 }
        [pscustomobject]@{ Name = $d.Name; Exe = $exe; DataDir = $d.Data
                           Installed = [bool]($exe -or (Test-Path -LiteralPath (Join-Path $d.Data 'profiles'))) }
    }
}

function Get-Instances($launchers) {
    $list = @()
    foreach ($l in $launchers) {
        $p = Join-Path $l.DataDir 'profiles'
        if (-not (Test-Path -LiteralPath $p)) { continue }
        foreach ($dir in (Get-ChildItem -LiteralPath $p -Directory -ErrorAction SilentlyContinue)) {
            $m = Join-Path $dir.FullName $MarkerName
            if (-not (Test-Path -LiteralPath $m)) { continue }
            try {
                $j = [IO.File]::ReadAllText($m, [Text.Encoding]::UTF8) | ConvertFrom-Json
                if ($j.packId -eq $PackId) {
                    $list += [pscustomobject]@{ Launcher = $l.Name; Path = $dir.FullName; Name = $dir.Name
                                                Tier = $j.tier; Version = $j.version; Marker = $j }
                }
            } catch { }
        }
    }
    return $list
}

# ---------------- Hardware ----------------
function Get-Hardware {
    $ram = 0; $gpus = @()
    try { $ram = [int][Math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB) } catch { }
    try { $gpus = @(Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name) } catch { }
    $gpus = @($gpus | Where-Object { $_ -and $_ -notmatch 'Microsoft Basic|Remote|Virtual|Parsec|Meta Virtual' })
    $strong = @($gpus | Where-Object { $_ -match 'RTX\s*[A-Z]?\d{3,4}|RX\s*[5-9]\d{3}|Arc\s*[AB]\d{3}' })
    $dedicated = @($gpus | Where-Object {
        $_ -match 'NVIDIA|GeForce|Quadro|RTX|GTX|Radeon\s*(RX|R9|R7|Pro)|Arc\s*[AB]\d' -and
        $_ -notmatch 'Radeon\(TM\)\s*Graphics|Vega\s*\d+\s*Graphics|Radeon\s*\d+M' })
    if ($ram -ge 24 -and $strong.Count -gt 0) { $tier = 'alta' }
    elseif ($ram -ge 12 -and $dedicated.Count -gt 0) { $tier = 'media' }
    else { $tier = 'baja' }
    $gpuText = '(no detectada)'
    if ($gpus.Count -gt 0) { $gpuText = $gpus -join ', ' }
    [pscustomobject]@{ Ram = $ram; Gpu = $gpuText; Tier = $tier }
}

function Show-TierDialog($hw) {
    $f = New-Object System.Windows.Forms.Form
    $f.Text = "$PackTitle - Elegir versión"
    $f.ClientSize = New-Object System.Drawing.Size(470, 270)
    $f.StartPosition = 'CenterScreen'; $f.FormBorderStyle = 'FixedDialog'; $f.MaximizeBox = $false; $f.MinimizeBox = $false
    $f.Font = New-Object System.Drawing.Font('Segoe UI', 9.5)

    $lbl = New-Object System.Windows.Forms.Label
    $lbl.Location = New-Object System.Drawing.Point(15, 12); $lbl.Size = New-Object System.Drawing.Size(440, 70)
    $lbl.Text = "He detectado en tu PC:`n  RAM: $($hw.Ram) GB`n  Gráfica: $($hw.Gpu)`nTe recomiendo la opción marcada. Puedes cambiarla:"
    $f.Controls.Add($lbl)

    $radios = @{}; $y = 92
    foreach ($k in $Tiers.Keys) {
        $rb = New-Object System.Windows.Forms.RadioButton
        $rb.Location = New-Object System.Drawing.Point(25, $y); $rb.Size = New-Object System.Drawing.Size(430, 26)
        $txt = $Tiers[$k].Label
        if ($k -eq $hw.Tier) { $txt += '   <- recomendada'; $rb.Checked = $true }
        $rb.Text = $txt
        $f.Controls.Add($rb); $radios[$k] = $rb; $y += 32
    }

    $ok = New-Object System.Windows.Forms.Button
    $ok.Text = 'Instalar'; $ok.Location = New-Object System.Drawing.Point(255, 222); $ok.Size = New-Object System.Drawing.Size(95, 30)
    $ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancelar'; $cancel.Location = New-Object System.Drawing.Point(360, 222); $cancel.Size = New-Object System.Drawing.Size(95, 30)
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $f.Controls.Add($ok); $f.Controls.Add($cancel); $f.AcceptButton = $ok; $f.CancelButton = $cancel
    $f.TopMost = $true

    if ($f.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { return $null }
    foreach ($k in $radios.Keys) { if ($radios[$k].Checked) { return $k } }
    return $null
}

# ---------------- GitHub ----------------
function Get-LatestRelease {
    Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -UseBasicParsing `
        -Headers @{ 'User-Agent' = "$PackId-installer"; 'Accept' = 'application/vnd.github+json' }
}
function Get-Asset($rel, [string]$tier) {
    $rel.assets | Where-Object { $_.name -eq "$AssetPrefix$tier.mrpack" } | Select-Object -First 1
}

# ---------------- Actualizar un perfil existente ----------------
function Update-InstanceFromPack($inst, [string]$mrpack) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $root = $inst.Path
    $zip = [IO.Compression.ZipFile]::OpenRead($mrpack)
    try {
        $entry = $zip.GetEntry('modrinth.index.json')
        if (-not $entry) { throw 'El paquete no tiene modrinth.index.json' }
        $sr = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
        $idx = $sr.ReadToEnd() | ConvertFrom-Json; $sr.Close()

        $files = @($idx.files | Where-Object { -not ($_.env -and $_.env.client -eq 'unsupported') })
        $overrides = @($zip.Entries | Where-Object { $_.FullName -match '^(client-)?overrides/' -and $_.Name })

        # Lista de archivos gestionados por el pack en la versión nueva
        $newManaged = New-Object System.Collections.Generic.HashSet[string]([StringComparer]::OrdinalIgnoreCase)
        foreach ($f in $files) { [void]$newManaged.Add(($f.path -replace '\\', '/')) }
        foreach ($e in $overrides) {
            $r = $e.FullName -replace '^(client-)?overrides/', ''
            if ($r -like 'mods/*' -or $r -like 'shaderpacks/*' -or $r -like 'resourcepacks/*') { [void]$newManaged.Add($r) }
        }

        # 1) Borrar lo que el pack tenía antes y ya no tiene
        foreach ($old in @($inst.Marker.files)) {
            if (-not $old) { continue }
            if (-not $newManaged.Contains(($old -replace '\\', '/'))) {
                $full = Join-SafePath $root $old
                if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Force; Log "Quitado: $old" }
                if (Test-Path -LiteralPath "$full.disabled") { Remove-Item -LiteralPath "$full.disabled" -Force }
            }
        }

        # 2) Descargar archivos nuevos o cambiados
        $n = 0
        foreach ($f in $files) {
            $n++
            $dest = Join-SafePath $root $f.path
            $want = ([string]$f.hashes.sha1).ToLowerInvariant()
            if ((Test-Path -LiteralPath $dest) -and ((Get-Sha1 $dest) -eq $want)) { continue }
            if (Test-Path -LiteralPath "$dest.disabled") { continue }   # el jugador lo desactivó a propósito
            [void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest))
            $ok = $false
            foreach ($u in @($f.downloads)) {
                try {
                    Download $u "$dest.part"
                    if ((Get-Sha1 "$dest.part") -eq $want) { Move-Item -LiteralPath "$dest.part" -Destination $dest -Force; $ok = $true; break }
                } catch { }
            }
            if (-not $ok) { Remove-Item -LiteralPath "$dest.part" -Force -ErrorAction SilentlyContinue; throw "No se pudo descargar $($f.path)" }
            Log ("Descargado ({0}/{1}): {2}" -f $n, $files.Count, $f.path)
        }

        # 3) Copiar overrides (configs, mods sueltos...). options.txt solo si no existe
        foreach ($e in $overrides) {
            $r = $e.FullName -replace '^(client-)?overrides/', ''
            $dest = Join-SafePath $root $r
            if ($r -eq 'options.txt' -and (Test-Path -LiteralPath $dest)) { continue }
            [void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
        }
    } finally { $zip.Dispose() }
}

function Test-GameRunning([string]$path) {
    try {
        $procs = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop |
                   Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($path, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
        return ($procs.Count -gt 0)
    } catch { return $false }
}

function Update-Instance($inst, $rel) {
    $asset = Get-Asset $rel $inst.Tier
    if (-not $asset) { throw "La versión $($rel.tag_name) no incluye la gama '$($inst.Tier)'." }
    if (Test-GameRunning $inst.Path) { throw "Minecraft está abierto con el perfil '$($inst.Name)'. Ciérralo y vuelve a intentarlo." }
    $tmp = Join-Path $env:TEMP ("{0}-{1}.mrpack" -f $PackId, $inst.Tier)
    Log "Descargando $($asset.name) ($([Math]::Round($asset.size / 1MB, 1)) MB)..."
    Download $asset.browser_download_url $tmp
    Update-InstanceFromPack $inst $tmp
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    Log "Perfil '$($inst.Name)' actualizado a $($rel.tag_name)."
}

# ---------------- Instalación nueva ----------------
function Install-Helper {
    # Copia este instalador a %LOCALAPPDATA% y crea un acceso directo en el escritorio
    try {
        if (-not $env:TFC_SELF -or -not (Test-Path -LiteralPath $env:TFC_SELF)) { return }
        $dir = Join-Path $env:LOCALAPPDATA "$PackId-pack"
        [void](New-Item -ItemType Directory -Force -Path $dir)
        $dest = Join-Path $dir "$PackId.bat"
        if ($env:TFC_SELF -ne $dest) { Copy-Item -LiteralPath $env:TFC_SELF -Destination $dest -Force }
        $ws = New-Object -ComObject WScript.Shell
        $lnk = $ws.CreateShortcut((Join-Path ([Environment]::GetFolderPath('Desktop')) "Actualizar $PackTitle.lnk"))
        $lnk.TargetPath = $dest; $lnk.WorkingDirectory = $dir
        $lnk.IconLocation = "$env:SystemRoot\System32\shell32.dll,238"
        $lnk.Description = "Instalar o actualizar $PackTitle"
        $lnk.Save()
    } catch { Log "No se pudo crear el acceso directo: $($_.Exception.Message)" }
}

function Install-New($launchers, $rel) {
    $hw = Get-Hardware
    $tier = Show-TierDialog $hw
    if (-not $tier) { return }

    $avail = @($launchers | Where-Object Installed)
    $l = $avail[0]
    if ($avail.Count -gt 1) {
        $r = Msg "Tienes instalados Modrinth App y Migurinth.`n`n¿Dónde lo instalo?`n  Sí = Modrinth App`n  No = Migurinth" 'YesNoCancel' 'Question'
        if ($r -eq 'Cancel') { return }
        if ($r -eq 'Yes') { $l = $avail | Where-Object Name -eq 'Modrinth App' } else { $l = $avail | Where-Object Name -eq 'Migurinth' }
    }

    $asset = Get-Asset $rel $tier
    if (-not $asset) { throw "La versión $($rel.tag_name) no incluye la gama '$tier'." }
    $dlDir = Join-Path $env:LOCALAPPDATA "$PackId-pack"
    [void](New-Item -ItemType Directory -Force -Path $dlDir)
    $file = Join-Path $dlDir $asset.name
    Log "Descargando $($asset.name) ($([Math]::Round($asset.size / 1MB, 1)) MB)..."
    Download $asset.browser_download_url $file

    $before = @(Get-Instances $launchers | ForEach-Object { $_.Path })
    Log "Abriendo $($l.Name)..."
    if ($l.Exe) { Start-Process -FilePath $l.Exe -ArgumentList ('"{0}"' -f $file) }
    else { Start-Process -FilePath $file }

    Msg ("Se ha abierto $($l.Name) con el modpack.`n`n" +
         "1. Si te pregunta, confirma la instalación.`n" +
         "2. Espera a que termine de descargar (puede tardar varios minutos).`n" +
         "3. Cuando termine, pulsa Aceptar aquí.") | Out-Null

    $new = $null
    for ($i = 0; $i -lt 3 -and -not $new; $i++) {
        $new = Get-Instances $launchers | Where-Object { $before -notcontains $_.Path } | Select-Object -First 1
        if (-not $new) { Start-Sleep -Seconds 2 }
    }
    Install-Helper
    $ramTip = $Tiers[$tier].Ram
    if ($new) {
        Msg ("¡Instalado! Perfil: $($new.Name)`n`n" +
             "IMPORTANTE - memoria RAM:`nEn $($l.Name), abre el perfil > Ajustes (icono de engranaje) > Java y memoria`ny pon la memoria en $ramTip.`n`n" +
             "Para actualizar en el futuro usa el acceso directo 'Actualizar $PackTitle' del escritorio.") | Out-Null
    } else {
        Msg ("No encuentro todavía el perfil instalado (puede que siga descargando).`n`n" +
             "Cuando termine, recuerda poner la memoria en $ramTip`n(perfil > Ajustes > Java y memoria).`n`n" +
             "Para actualizar en el futuro usa el acceso directo 'Actualizar $PackTitle' del escritorio.") 'OK' 'Warning' | Out-Null
    }
}

# ---------------- Programa principal ----------------
function Main {
    $argLine = [string]$env:TFC_ARGS
    $script:AutoMode = ($argLine -match '(^|\s)-auto(\s|$)')
    if (-not $script:AutoMode) { Init-Gui }

    if ($Repo -like '__*') { Msg 'Este instalador no está configurado (falta el repositorio de GitHub).' 'OK' 'Error' | Out-Null; return }

    $launchers = @(Get-Launchers)
    if (-not ($launchers | Where-Object Installed)) {
        if ($script:AutoMode) { return }
        $r = Msg ("Para jugar necesitas Modrinth App (o Migurinth) y no lo encuentro en este PC.`n`n" +
                  "¿Quieres abrir la página de descarga?`n  Sí = Modrinth App (recomendado)`n  No = Migurinth`n`n" +
                  "Instálalo, ábrelo una vez, y vuelve a ejecutar este instalador.") 'YesNoCancel' 'Warning'
        if ($r -eq 'Yes') { Start-Process 'https://modrinth.com/app' }
        elseif ($r -eq 'No') { Start-Process 'https://github.com/MiguVT/migurinth/releases/latest' }
        return
    }

    Log 'Buscando la última versión en GitHub...'
    try { $rel = Get-LatestRelease }
    catch { Msg "No he podido consultar las actualizaciones (¿sin internet?).`n`n$($_.Exception.Message)" 'OK' 'Error' | Out-Null; return }
    $latest = ConvertTo-Ver $rel.tag_name
    Log "Última versión publicada: $($rel.tag_name)"

    $instances = @(Get-Instances $launchers)
    $outdated = @($instances | Where-Object { (ConvertTo-Ver $_.Version) -lt $latest })

    if ($script:AutoMode) {
        foreach ($i in $outdated) { try { Update-Instance $i $rel } catch { Log "ERROR: $($_.Exception.Message)" } }
        return
    }

    if ($outdated.Count -gt 0) {
        $names = ($outdated | ForEach-Object { "  - $($_.Name)  (v$($_.Version), $($_.Launcher))" }) -join "`n"
        $notes = ''
        if ($rel.body) { $notes = "`n`nNovedades:`n" + ([string]$rel.body).Substring(0, [Math]::Min(600, ([string]$rel.body).Length)) }
        $r = Msg "Hay una actualización: $($rel.tag_name)`n`nPerfiles a actualizar:`n$names$notes`n`n¿Actualizar ahora?" 'YesNo' 'Question'
        if ($r -eq 'Yes') {
            $errs = @()
            foreach ($i in $outdated) { try { Update-Instance $i $rel } catch { $errs += "$($i.Name): $($_.Exception.Message)" } }
            if ($errs.Count) { Msg ("Algunos perfiles no se pudieron actualizar:`n`n" + ($errs -join "`n")) 'OK' 'Error' | Out-Null }
            else { Msg "¡Listo! Ya tienes la versión $($rel.tag_name)." | Out-Null }
        }
        return
    }

    if ($instances.Count -gt 0) {
        $names = ($instances | ForEach-Object { "  - $($_.Name)  (v$($_.Version))" }) -join "`n"
        $r = Msg "Ya tienes la última versión ($($rel.tag_name)):`n$names`n`n¿Quieres instalar además otra versión (otra gama)?" 'YesNo' 'Information'
        if ($r -ne 'Yes') { return }
    }
    Install-New $launchers $rel
}

if (-not $env:TFC_NO_MAIN) {
    try { Main }
    catch {
        if ($script:AutoMode) { Log "ERROR: $($_.Exception.Message)" }
        else { Msg "Ha ocurrido un error:`n`n$($_.Exception.Message)" 'OK' 'Error' | Out-Null }
    }
}
