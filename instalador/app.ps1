# ==========================================================================
#  TFC Create - Instalador / actualizador visual (WPF)
#  Lo ejecuta TFC-Create.exe. Funciona con Modrinth App y Migurinth.
# ==========================================================================
$Repo        = 'pkatheassh0l3/mine-launcher'
$PackId      = 'tfc-create'
$PackTitle   = 'TFC Create'
$MarkerName  = 'tfc-create-pack.json'
$AssetPrefix = 'TFC-Create_'
$HelperDir   = Join-Path $env:LOCALAPPDATA 'tfc-create-pack'
# Ajustes del jugador que una actualización NO debe pisar
$Preserve    = @('options.txt', 'config/iris.properties', 'config/sodium-options.json', 'config/sodium-extra-options.json', 'config/DistantHorizons.toml', 'config/jei/jei-client.ini')

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Tiers = [ordered]@{
    baja  = @{ Title = 'Gama baja';  Ram = '4 GB (4096 MB)';  MemMB = 4096;  Region = '8M' }
    media = @{ Title = 'Gama media'; Ram = '8 GB (8192 MB)';  MemMB = 8192;  Region = '8M' }
    alta  = @{ Title = 'Gama alta';  Ram = '10 GB (10240 MB)'; MemMB = 10240; Region = '16M' }
}

$script:Ui = $false
$script:W = $null
function Log($m) { if (-not $script:Ui) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) } }

# ======================= Utilidades =======================
function Pump {
    if (-not $script:Ui) { return }
    $frame = New-Object System.Windows.Threading.DispatcherFrame
    $cb = [System.Windows.Threading.DispatcherOperationCallback] { param($f) $f.Continue = $false; return $null }
    [void][System.Windows.Threading.Dispatcher]::CurrentDispatcher.BeginInvoke([System.Windows.Threading.DispatcherPriority]::Background, $cb, $frame)
    [System.Windows.Threading.Dispatcher]::PushFrame($frame)
}
function Set-Progress($pct, $text, $detail) {
    if (-not $script:Ui) { if ($text) { Log $text }; return }
    if ($pct -ge 0) { $script:W.ProgBar.IsIndeterminate = $false; $script:W.ProgBar.Value = [Math]::Min(100, $pct) }
    else { $script:W.ProgBar.IsIndeterminate = $true }
    if ($null -ne $text) { $script:W.ProgText.Text = $text }
    if ($null -ne $detail) { $script:W.ProgDetail.Text = $detail }
    Pump
}
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
function Download([string]$url, [string]$dest, [double]$pctFrom = 0, [double]$pctTo = 100, [string]$label = '') {
    $req = [Net.HttpWebRequest]::Create($url)
    $req.UserAgent = "$PackId-installer"; $req.AllowAutoRedirect = $true; $req.Timeout = 60000
    $resp = $req.GetResponse()
    $total = [double]$resp.ContentLength
    $in = $resp.GetResponseStream()
    $out = [IO.File]::Create($dest)
    try {
        $buf = New-Object byte[] 262144
        $done = 0.0; $last = [DateTime]::MinValue
        while (($n = $in.Read($buf, 0, $buf.Length)) -gt 0) {
            $out.Write($buf, 0, $n); $done += $n
            if ($script:Ui -and ((Get-Date) - $last).TotalMilliseconds -gt 120) {
                $last = Get-Date
                $frac = 0; if ($total -gt 0) { $frac = $done / $total }
                $mb = "{0:N1} / {1:N1} MB" -f ($done / 1MB), ($total / 1MB)
                Set-Progress ($pctFrom + ($pctTo - $pctFrom) * $frac) $null "$label   $mb"
            }
        }
    } finally { $out.Close(); $in.Close(); $resp.Close() }
}

# ======================= Launchers y perfiles =======================
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
    $gpuText = 'no detectada'
    if ($gpus.Count -gt 0) { $gpuText = $gpus -join ', ' }
    [pscustomobject]@{ Ram = $ram; Gpu = $gpuText; Tier = $tier }
}

# ======================= GitHub =======================
function Get-LatestRelease {
    Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -UseBasicParsing `
        -Headers @{ 'User-Agent' = "$PackId-installer"; 'Accept' = 'application/vnd.github+json' }
}
function Get-Asset($rel, [string]$tier) {
    $rel.assets | Where-Object { $_.name -eq "$AssetPrefix$tier.mrpack" } | Select-Object -First 1
}

# ======================= Actualizar =======================
function Update-InstanceFromPack($inst, [string]$mrpack, [double]$from = 0, [double]$to = 100) {
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

        $newManaged = New-Object System.Collections.Generic.HashSet[string]([StringComparer]::OrdinalIgnoreCase)
        foreach ($f in $files) { [void]$newManaged.Add(($f.path -replace '\\', '/')) }
        foreach ($e in $overrides) {
            $r = $e.FullName -replace '^(client-)?overrides/', ''
            if ($r -like 'mods/*' -or $r -like 'shaderpacks/*' -or $r -like 'resourcepacks/*') { [void]$newManaged.Add($r) }
        }

        Set-Progress $from 'Quitando mods antiguos...' ''
        foreach ($old in @($inst.Marker.files)) {
            if (-not $old) { continue }
            if (-not $newManaged.Contains(($old -replace '\\', '/'))) {
                $full = Join-SafePath $root $old
                if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Force; Log "Quitado: $old" }
                if (Test-Path -LiteralPath "$full.disabled") { Remove-Item -LiteralPath "$full.disabled" -Force }
            }
        }

        $n = 0; $span = ($to - $from) * 0.9
        foreach ($f in $files) {
            $n++
            $p0 = $from + $span * ($n - 1) / [Math]::Max(1, $files.Count)
            $p1 = $from + $span * $n / [Math]::Max(1, $files.Count)
            $name = Split-Path -Leaf $f.path
            Set-Progress $p0 ("Comprobando archivos ({0}/{1})" -f $n, $files.Count) $name
            $dest = Join-SafePath $root $f.path
            $want = ([string]$f.hashes.sha1).ToLowerInvariant()
            if ((Test-Path -LiteralPath $dest) -and ((Get-Sha1 $dest) -eq $want)) { continue }
            if (Test-Path -LiteralPath "$dest.disabled") { continue }
            [void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest))
            $ok = $false
            foreach ($u in @($f.downloads)) {
                try {
                    Set-Progress $p0 ("Descargando ({0}/{1})" -f $n, $files.Count) $name
                    Download $u "$dest.part" $p0 $p1 $name
                    if ((Get-Sha1 "$dest.part") -eq $want) { Move-Item -LiteralPath "$dest.part" -Destination $dest -Force; $ok = $true; break }
                } catch { }
            }
            if (-not $ok) { Remove-Item -LiteralPath "$dest.part" -Force -ErrorAction SilentlyContinue; throw "No se pudo descargar $($f.path). Si Minecraft está abierto, ciérralo." }
            Log "Descargado: $($f.path)"
        }

        Set-Progress ($from + $span) 'Copiando configuración...' ''
        foreach ($e in $overrides) {
            $r = $e.FullName -replace '^(client-)?overrides/', ''
            $dest = Join-SafePath $root $r
            if ($Preserve -contains $r -and (Test-Path -LiteralPath $dest)) { continue }
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
function Update-Instance($inst, $rel, [double]$from = 0, [double]$to = 100) {
    $asset = Get-Asset $rel $inst.Tier
    if (-not $asset) { throw "La versión $($rel.tag_name) no incluye la gama '$($inst.Tier)'." }
    if (Test-GameRunning $inst.Path) { throw "Minecraft está abierto con el perfil '$($inst.Name)'. Ciérralo y vuelve a intentarlo." }
    [void](New-Item -ItemType Directory -Force -Path $HelperDir)
    $tmp = Join-Path $HelperDir ("update-{0}.mrpack" -f $inst.Tier)
    $mid = $from + ($to - $from) * 0.3
    Set-Progress $from "Descargando $($Tiers[$inst.Tier].Title) $($rel.tag_name)..." ''
    Download $asset.browser_download_url $tmp $from $mid $asset.name
    Update-InstanceFromPack $inst $tmp $mid $to
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    if ($script:Ui) { [void](Set-LauncherSettings $inst $true) }
    Log "Perfil '$($inst.Name)' actualizado a $($rel.tag_name)."
}


# ======================= Ajustes del perfil en el launcher (RAM, Java, auto-actualizar) =======================
# Modrinth App y Migurinth guardan estos ajustes en <datos>\app.db (SQLite). Usamos el SQLite que trae Windows.
$SqliteSig = @"
using System;
using System.Runtime.InteropServices;
public static class TfcSqlite {
    [DllImport("winsqlite3.dll", CharSet = CharSet.Unicode)] public static extern int sqlite3_open16(string filename, out IntPtr db);
    [DllImport("winsqlite3.dll")] public static extern int sqlite3_exec(IntPtr db, byte[] sql, IntPtr cb, IntPtr arg, out IntPtr errmsg);
    [DllImport("winsqlite3.dll")] public static extern int sqlite3_busy_timeout(IntPtr db, int ms);
    [DllImport("winsqlite3.dll")] public static extern int sqlite3_changes(IntPtr db);
    [DllImport("winsqlite3.dll")] public static extern int sqlite3_close(IntPtr db);
}
"@
function Invoke-LauncherSql([string]$db, [string]$sql) {
    if (-not ('TfcSqlite' -as [type])) { Add-Type -TypeDefinition $SqliteSig }
    $h = [IntPtr]::Zero
    if ([TfcSqlite]::sqlite3_open16($db, [ref]$h) -ne 0) { throw 'No se pudo abrir la base de datos del launcher.' }
    try {
        [void][TfcSqlite]::sqlite3_busy_timeout($h, 8000)
        $err = [IntPtr]::Zero
        $rc = [TfcSqlite]::sqlite3_exec($h, [Text.Encoding]::UTF8.GetBytes($sql + [char]0), [IntPtr]::Zero, [IntPtr]::Zero, [ref]$err)
        if ($rc -ne 0) { throw "Error de SQLite ($rc)" }
        return [TfcSqlite]::sqlite3_changes($h)
    } finally { [void][TfcSqlite]::sqlite3_close($h) }
}
function Get-TierMemoryMB([string]$tier) {
    $mb = [int]$Tiers[$tier].MemMB
    $total = 0
    try { $total = [double](Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB } catch { }
    if ($total -gt 0) {
        $cap = [int]([Math]::Floor($total * 0.6 / 512) * 512)
        if ($cap -lt $mb) { $mb = [Math]::Max(3072, $cap) }
    }
    return $mb
}
function Get-JvmArgs([string]$tier) {
    return @('-XX:+UseG1GC', '-XX:+UnlockExperimentalVMOptions', '-XX:+ParallelRefProcEnabled', '-XX:+DisableExplicitGC',
             '-XX:MaxGCPauseMillis=50', '-XX:G1NewSizePercent=30', '-XX:G1MaxNewSizePercent=40',
             "-XX:G1HeapRegionSize=$($Tiers[$tier].Region)", '-XX:G1ReservePercent=20', '-XX:G1HeapWastePercent=5',
             '-XX:G1MixedGCCountTarget=4', '-XX:InitiatingHeapOccupancyPercent=15', '-XX:G1MixedGCLiveThresholdPercent=90',
             '-XX:G1RSetUpdatingPauseTimePercent=5', '-XX:SurvivorRatio=32', '-XX:MaxTenuringThreshold=1', '-XX:+PerfDisableSharedMem')
}
# Devuelve $true si se aplicó. $onlyIfDefault: no pisar lo que el jugador haya cambiado a mano.
function Set-LauncherSettings($inst, [bool]$onlyIfDefault) {
    try {
        $l = @($script:Launchers | Where-Object { $_.Name -eq $inst.Launcher }) | Select-Object -First 1
        if (-not $l) { return $false }
        $db = Join-Path $l.DataDir 'app.db'
        if (-not (Test-Path -LiteralPath $db)) { return $false }
        $name = $inst.Name.Replace("'", "''")
        $mb = Get-TierMemoryMB $inst.Tier
        $jvm = (ConvertTo-Json -InputObject @(Get-JvmArgs $inst.Tier) -Compress).Replace("'", "''")
        $cond = ''
        if ($onlyIfDefault) { $cond = ' AND override_mc_memory_max IS NULL' }
        $n = Invoke-LauncherSql $db ("UPDATE profiles SET override_mc_memory_max = $mb, override_extra_launch_args = '$jvm' " +
                                     "WHERE path = '$name' AND install_stage = 'installed'$cond;")
        # Actualizar solo al pulsar Jugar (si el jugador no tiene ya otro comando puesto)
        $exe = Join-Path $HelperDir 'TFC-Create.exe'
        if (Test-Path -LiteralPath $exe) {
            $hook = ('"' + ($exe -replace '\\', '/') + '" -auto').Replace("'", "''")
            [void](Invoke-LauncherSql $db ("UPDATE profiles SET override_hook_pre_launch = '$hook' " +
                                           "WHERE path = '$name' AND install_stage = 'installed' AND (override_hook_pre_launch IS NULL OR override_hook_pre_launch = '' OR override_hook_pre_launch LIKE '%TFC-Create.exe%');"))
        }
        if ($n -gt 0) { Log "Ajustes del launcher aplicados a '$($inst.Name)': $mb MB" }
        return ($n -gt 0 -or $onlyIfDefault)
    } catch { Log "No se pudieron aplicar los ajustes del launcher: $($_.Exception.Message)"; return $false }
}

# ======================= Acceso directo =======================
function Install-Helper {
    try {
        $self = $env:TFC_SELF
        if (-not $self -or -not (Test-Path -LiteralPath $self)) { return }
        [void](New-Item -ItemType Directory -Force -Path $HelperDir)
        $dest = Join-Path $HelperDir 'TFC-Create.exe'
        if ([IO.Path]::GetFullPath($self) -ne [IO.Path]::GetFullPath($dest)) { Copy-Item -LiteralPath $self -Destination $dest -Force }
        $ws = New-Object -ComObject WScript.Shell
        $places = @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))
        foreach ($pl in $places) {
            if (-not $pl) { continue }
            $lnk = $ws.CreateShortcut((Join-Path $pl "$PackTitle.lnk"))
            $lnk.TargetPath = $dest; $lnk.WorkingDirectory = $HelperDir
            $lnk.IconLocation = "$dest,0"; $lnk.Description = "Instalar o actualizar $PackTitle"
            $lnk.Save()
        }
    } catch { }
}

# ======================= Modo automático (sin ventana) =======================
function Run-Auto {
    try {
        $launchers = @(Get-Launchers)
        $rel = Get-LatestRelease
        $latest = ConvertTo-Ver $rel.tag_name
        foreach ($i in @(Get-Instances $launchers)) {
            if ((ConvertTo-Ver $i.Version) -lt $latest) { try { Update-Instance $i $rel } catch { Log "ERROR: $($_.Exception.Message)" } }
        }
    } catch { Log "ERROR: $($_.Exception.Message)" }
}

# ======================= Interfaz =======================
$Xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="TFC Create" Width="860" Height="590" WindowStartupLocation="CenterScreen" ResizeMode="CanMinimize"
        Background="#15171B" FontFamily="Segoe UI" Foreground="#E9E7E3" UseLayoutRounding="True">
  <Window.Resources>
    <Style x:Key="Btn" TargetType="Button">
      <Setter Property="Foreground" Value="White"/>
      <Setter Property="Background" Value="#3FA35F"/>
      <Setter Property="FontSize" Value="15"/>
      <Setter Property="FontWeight" Value="SemiBold"/>
      <Setter Property="Padding" Value="24,11"/>
      <Setter Property="Margin" Value="0,0,12,0"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="Button">
            <Border x:Name="bd" Background="{TemplateBinding Background}" CornerRadius="9" Padding="{TemplateBinding Padding}">
              <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="bd" Property="Opacity" Value="0.85"/></Trigger>
              <Trigger Property="IsEnabled" Value="False"><Setter TargetName="bd" Property="Opacity" Value="0.35"/></Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <Style x:Key="Btn2" TargetType="Button" BasedOn="{StaticResource Btn}">
      <Setter Property="Background" Value="#2B3038"/>
    </Style>
    <Style x:Key="Card" TargetType="Border">
      <Setter Property="Background" Value="#1F2329"/>
      <Setter Property="BorderBrush" Value="#2E343D"/>
      <Setter Property="BorderThickness" Value="2"/>
      <Setter Property="CornerRadius" Value="14"/>
      <Setter Property="Padding" Value="18,16"/>
      <Setter Property="Margin" Value="0,0,14,0"/>
      <Setter Property="Cursor" Value="Hand"/>
    </Style>
    <Style x:Key="H1" TargetType="TextBlock">
      <Setter Property="FontSize" Value="24"/>
      <Setter Property="FontWeight" Value="Bold"/>
      <Setter Property="Foreground" Value="White"/>
      <Setter Property="Margin" Value="0,0,0,8"/>
    </Style>
    <Style x:Key="P" TargetType="TextBlock">
      <Setter Property="FontSize" Value="14.5"/>
      <Setter Property="Foreground" Value="#B9BEC6"/>
      <Setter Property="TextWrapping" Value="Wrap"/>
      <Setter Property="LineHeight" Value="22"/>
    </Style>
    <Style TargetType="ProgressBar">
      <Setter Property="Height" Value="14"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ProgressBar">
            <Grid>
              <Border x:Name="PART_Track" Background="#2B3038" CornerRadius="7"/>
              <Border x:Name="PART_Indicator" HorizontalAlignment="Left" CornerRadius="7">
                <Border.Background>
                  <LinearGradientBrush StartPoint="0,0" EndPoint="1,0">
                    <GradientStop Color="#3FA35F" Offset="0"/>
                    <GradientStop Color="#8BD36A" Offset="1"/>
                  </LinearGradientBrush>
                </Border.Background>
              </Border>
            </Grid>
            <ControlTemplate.Triggers>
              <Trigger Property="IsIndeterminate" Value="True">
                <Trigger.EnterActions>
                  <BeginStoryboard x:Name="Pulse">
                    <Storyboard>
                      <DoubleAnimation Storyboard.TargetName="PART_Indicator" Storyboard.TargetProperty="Opacity" From="0.2" To="0.9" Duration="0:0:0.9" AutoReverse="True" RepeatBehavior="Forever"/>
                    </Storyboard>
                  </BeginStoryboard>
                </Trigger.EnterActions>
                <Trigger.ExitActions>
                  <StopStoryboard BeginStoryboardName="Pulse"/>
                </Trigger.ExitActions>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
  </Window.Resources>

  <Grid>
    <Grid.RowDefinitions>
      <RowDefinition Height="112"/>
      <RowDefinition Height="*"/>
    </Grid.RowDefinitions>

    <!-- Cabecera -->
    <Border Grid.Row="0">
      <Border.Background>
        <LinearGradientBrush StartPoint="0,0" EndPoint="1,1">
          <GradientStop Color="#2F6E43" Offset="0"/>
          <GradientStop Color="#3E5A2C" Offset="0.55"/>
          <GradientStop Color="#7A5530" Offset="1"/>
        </LinearGradientBrush>
      </Border.Background>
      <Grid Margin="30,0">
        <StackPanel Orientation="Horizontal" VerticalAlignment="Center">
          <Image x:Name="Logo" Width="68" Height="68" Margin="0,0,18,0"/>
          <StackPanel VerticalAlignment="Center">
            <TextBlock Text="TFC CREATE" FontSize="32" FontWeight="Black" Foreground="White"/>
            <TextBlock Text="Modpack · Minecraft 1.21.1 · NeoForge" FontSize="13.5" Foreground="#DCE9DA"/>
          </StackPanel>
        </StackPanel>
        <TextBlock x:Name="VersionTag" HorizontalAlignment="Right" VerticalAlignment="Center" FontSize="13" Foreground="#E8F2E4"/>
      </Grid>
    </Border>

    <Grid Grid.Row="1" Margin="30,24,30,24">

      <!-- Cargando -->
      <StackPanel x:Name="PageLoading" VerticalAlignment="Center" HorizontalAlignment="Center">
        <TextBlock x:Name="LoadingText" Text="Comprobando tu PC..." FontSize="19" HorizontalAlignment="Center"/>
        <ProgressBar IsIndeterminate="True" Width="320" Margin="0,18,0,0"/>
      </StackPanel>

      <!-- Sin launcher -->
      <StackPanel x:Name="PageNoLauncher" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock Style="{StaticResource H1}" Text="Primero necesitas un launcher"/>
        <TextBlock Style="{StaticResource P}" Margin="0,0,0,22"
                   Text="Para jugar hace falta Modrinth App (o Migurinth, una versión alternativa). Descárgalo, instálalo y ábrelo una vez. Después vuelve aquí y pulsa &quot;Ya lo he instalado&quot;."/>
        <StackPanel Orientation="Horizontal">
          <Button x:Name="BtnGetModrinth" Style="{StaticResource Btn}" Content="Descargar Modrinth App"/>
          <Button x:Name="BtnGetMigu" Style="{StaticResource Btn2}" Content="Descargar Migurinth"/>
          <Button x:Name="BtnRecheck" Style="{StaticResource Btn2}" Content="Ya lo he instalado"/>
        </StackPanel>
      </StackPanel>

      <!-- Elegir gama -->
      <Grid x:Name="PageTier" Visibility="Collapsed">
        <Grid.RowDefinitions>
          <RowDefinition Height="Auto"/>
          <RowDefinition Height="*"/>
          <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>
        <StackPanel Grid.Row="0" Margin="0,0,0,16">
          <TextBlock Style="{StaticResource H1}" Text="Elige la versión para tu PC"/>
          <TextBlock x:Name="HwText" Style="{StaticResource P}"/>
        </StackPanel>
        <UniformGrid Grid.Row="1" Columns="3" Margin="0,0,-14,0">
          <Border x:Name="CardBaja" Style="{StaticResource Card}" Tag="baja">
            <StackPanel>
              <Border x:Name="BadgeBaja" Background="#3FA35F" CornerRadius="6" Padding="8,2" HorizontalAlignment="Left" Margin="0,0,0,10" Visibility="Hidden">
                <TextBlock Text="RECOMENDADA" FontSize="11" FontWeight="Bold" Foreground="White"/>
              </Border>
              <TextBlock Text="Gama baja" FontSize="21" FontWeight="Bold" Foreground="White"/>
              <TextBlock Text="8 GB RAM · sin gráfica dedicada" FontSize="13" Foreground="#8FD18A" Margin="0,2,0,12"/>
              <TextBlock Style="{StaticResource P}" FontSize="13.5" Text="• Distancia de visión 6&#10;• Gráficos rápidos&#10;• Máximo rendimiento"/>
              <TextBlock Text="Memoria: 4 GB" FontSize="13" Foreground="#8A919B" Margin="0,12,0,0"/>
            </StackPanel>
          </Border>
          <Border x:Name="CardMedia" Style="{StaticResource Card}" Tag="media">
            <StackPanel>
              <Border x:Name="BadgeMedia" Background="#3FA35F" CornerRadius="6" Padding="8,2" HorizontalAlignment="Left" Margin="0,0,0,10" Visibility="Hidden">
                <TextBlock Text="RECOMENDADA" FontSize="11" FontWeight="Bold" Foreground="White"/>
              </Border>
              <TextBlock Text="Gama media" FontSize="21" FontWeight="Bold" Foreground="White"/>
              <TextBlock Text="16 GB RAM · gráfica antigua" FontSize="13" Foreground="#E0C36A" Margin="0,2,0,12"/>
              <TextBlock Style="{StaticResource P}" FontSize="13.5" Text="• Distancia de visión 10&#10;• Gráficos detallados&#10;• Equilibrado"/>
              <TextBlock Text="Memoria: 8 GB" FontSize="13" Foreground="#8A919B" Margin="0,12,0,0"/>
            </StackPanel>
          </Border>
          <Border x:Name="CardAlta" Style="{StaticResource Card}" Tag="alta">
            <StackPanel>
              <Border x:Name="BadgeAlta" Background="#3FA35F" CornerRadius="6" Padding="8,2" HorizontalAlignment="Left" Margin="0,0,0,10" Visibility="Hidden">
                <TextBlock Text="RECOMENDADA" FontSize="11" FontWeight="Bold" Foreground="White"/>
              </Border>
              <TextBlock Text="Gama alta" FontSize="21" FontWeight="Bold" Foreground="White"/>
              <TextBlock Text="32 GB RAM · RTX 3060 o similar" FontSize="13" Foreground="#E88F6A" Margin="0,2,0,12"/>
              <TextBlock Style="{StaticResource P}" FontSize="13.5" Text="• Distancia de visión 16&#10;• Shaders (Iris)&#10;• Distant Horizons"/>
              <TextBlock Text="Memoria: 10-12 GB" FontSize="13" Foreground="#8A919B" Margin="0,12,0,0"/>
            </StackPanel>
          </Border>
        </UniformGrid>
        <DockPanel Grid.Row="2" Margin="0,18,0,0" LastChildFill="False">
          <StackPanel x:Name="LauncherPanel" Orientation="Horizontal" DockPanel.Dock="Left" VerticalAlignment="Center">
            <TextBlock Text="Instalar en:" FontSize="14" VerticalAlignment="Center" Margin="0,0,10,0" Foreground="#B9BEC6"/>
            <ComboBox x:Name="LauncherBox" Width="170" FontSize="14"/>
          </StackPanel>
          <Button x:Name="BtnInstall" DockPanel.Dock="Right" Style="{StaticResource Btn}" Margin="0" Content="Instalar"/>
        </DockPanel>
      </Grid>

      <!-- Progreso -->
      <StackPanel x:Name="PageProgress" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock x:Name="ProgTitle" Style="{StaticResource H1}" Text="Instalando..."/>
        <TextBlock x:Name="ProgText" Style="{StaticResource P}" Margin="0,4,0,14"/>
        <ProgressBar x:Name="ProgBar" Minimum="0" Maximum="100"/>
        <TextBlock x:Name="ProgDetail" FontSize="12.5" Foreground="#80868F" Margin="0,10,0,0" TextTrimming="CharacterEllipsis"/>
      </StackPanel>

      <!-- Esperando al launcher -->
      <StackPanel x:Name="PageWait" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock x:Name="WaitTitle" Style="{StaticResource H1}" Text="Termina la instalación en el launcher"/>
        <TextBlock x:Name="WaitText" Style="{StaticResource P}" Margin="0,0,0,18"/>
        <ProgressBar IsIndeterminate="True" Width="320" HorizontalAlignment="Left" Margin="0,0,0,22"/>
        <StackPanel Orientation="Horizontal">
          <Button x:Name="BtnWaitDone" Style="{StaticResource Btn2}" Content="Ya ha terminado"/>
        </StackPanel>
      </StackPanel>

      <!-- Hecho -->
      <StackPanel x:Name="PageDone" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock Text="✓" FontSize="54" FontWeight="Bold" Foreground="#5CC27A" Margin="0,0,0,4"/>
        <TextBlock x:Name="DoneTitle" Style="{StaticResource H1}" Text="¡Listo!"/>
        <TextBlock x:Name="DoneText" Style="{StaticResource P}" Margin="0,0,0,22"/>
        <StackPanel Orientation="Horizontal">
          <Button x:Name="BtnOpenLauncher" Style="{StaticResource Btn}" Content="Abrir el launcher"/>
          <Button x:Name="BtnClose" Style="{StaticResource Btn2}" Content="Cerrar"/>
        </StackPanel>
      </StackPanel>

      <!-- Actualización disponible -->
      <Grid x:Name="PageUpdate" Visibility="Collapsed">
        <Grid.RowDefinitions>
          <RowDefinition Height="Auto"/>
          <RowDefinition Height="*"/>
          <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>
        <StackPanel Grid.Row="0">
          <TextBlock x:Name="UpdTitle" Style="{StaticResource H1}" Text="¡Hay una actualización!"/>
          <TextBlock x:Name="UpdList" Style="{StaticResource P}" Margin="0,0,0,12"/>
        </StackPanel>
        <Border Grid.Row="1" Background="#1F2329" CornerRadius="12" Padding="16,12">
          <ScrollViewer VerticalScrollBarVisibility="Auto">
            <StackPanel>
              <TextBlock Text="NOVEDADES" FontSize="11.5" FontWeight="Bold" Foreground="#8FD18A" Margin="0,0,0,6"/>
              <TextBlock x:Name="UpdNotes" Style="{StaticResource P}"/>
            </StackPanel>
          </ScrollViewer>
        </Border>
        <StackPanel Grid.Row="2" Orientation="Horizontal" Margin="0,18,0,0">
          <Button x:Name="BtnUpdate" Style="{StaticResource Btn}" Content="Actualizar ahora"/>
          <Button x:Name="BtnOtherTier" Style="{StaticResource Btn2}" Content="Instalar otra versión"/>
        </StackPanel>
      </Grid>

      <!-- Todo al día -->
      <StackPanel x:Name="PageUpToDate" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock Text="✓" FontSize="54" FontWeight="Bold" Foreground="#5CC27A" Margin="0,0,0,4"/>
        <TextBlock x:Name="UtdTitle" Style="{StaticResource H1}" Text="Todo al día"/>
        <TextBlock x:Name="UtdText" Style="{StaticResource P}" Margin="0,0,0,22"/>
        <StackPanel Orientation="Horizontal">
          <Button x:Name="BtnPlay" Style="{StaticResource Btn}" Content="Abrir el launcher"/>
          <Button x:Name="BtnOtherTier2" Style="{StaticResource Btn2}" Content="Instalar otra versión"/>
          <Button x:Name="BtnClose2" Style="{StaticResource Btn2}" Content="Cerrar"/>
        </StackPanel>
      </StackPanel>

      <!-- Error -->
      <StackPanel x:Name="PageError" Visibility="Collapsed" VerticalAlignment="Center">
        <TextBlock Text="!" FontSize="54" FontWeight="Bold" Foreground="#E8745A" Margin="0,0,0,4"/>
        <TextBlock Style="{StaticResource H1}" Text="Algo ha fallado"/>
        <TextBlock x:Name="ErrText" Style="{StaticResource P}" Margin="0,0,0,22"/>
        <StackPanel Orientation="Horizontal">
          <Button x:Name="BtnRetry" Style="{StaticResource Btn}" Content="Reintentar"/>
          <Button x:Name="BtnClose3" Style="{StaticResource Btn2}" Content="Cerrar"/>
        </StackPanel>
      </StackPanel>

    </Grid>
  </Grid>
</Window>
'@

$Pages = 'PageLoading', 'PageNoLauncher', 'PageTier', 'PageProgress', 'PageWait', 'PageDone', 'PageUpdate', 'PageUpToDate', 'PageError'

function Show-Page([string]$name) {
    foreach ($p in $Pages) {
        if ($p -eq $name) { $script:W.$p.Visibility = 'Visible' } else { $script:W.$p.Visibility = 'Collapsed' }
    }
    Pump
}
function Show-Error([string]$msg) {
    if ($script:Timer) { $script:Timer.Stop() }
    $script:W.ErrText.Text = $msg
    Show-Page 'PageError'
}
function Guard([scriptblock]$sb) {
    try { & $sb } catch { Show-Error $_.Exception.Message }
}
function Open-Launcher {
    $l = @($script:Launchers | Where-Object { $_.Installed -and $_.Exe }) | Select-Object -First 1
    if ($script:UsedLauncher -and $script:UsedLauncher.Exe) { $l = $script:UsedLauncher }
    if ($l) { Start-Process -FilePath $l.Exe }
}

function Select-Tier([string]$tier) {
    $script:SelTier = $tier
    $map = @{ baja = 'CardBaja'; media = 'CardMedia'; alta = 'CardAlta' }
    $conv = New-Object System.Windows.Media.BrushConverter
    foreach ($k in $map.Keys) {
        $c = $script:W[$map[$k]]
        if ($k -eq $tier) { $c.BorderBrush = $conv.ConvertFromString('#3FA35F'); $c.Background = $conv.ConvertFromString('#1F2E24') }
        else { $c.BorderBrush = $conv.ConvertFromString('#2E343D'); $c.Background = $conv.ConvertFromString('#1F2329') }
    }
    $script:W.BtnInstall.Content = "Instalar $($Tiers[$tier].Title.ToLower())"
}

function Show-TierPage {
    $hw = $script:Hw
    $script:W.HwText.Text = "Tu PC: $($hw.Ram) GB de RAM · Gráfica: $($hw.Gpu).  Te marcamos la recomendada, pero puedes elegir otra."
    $script:W.BadgeBaja.Visibility = 'Hidden'; $script:W.BadgeMedia.Visibility = 'Hidden'; $script:W.BadgeAlta.Visibility = 'Hidden'
    switch ($hw.Tier) { 'baja' { $script:W.BadgeBaja.Visibility = 'Visible' } 'media' { $script:W.BadgeMedia.Visibility = 'Visible' } 'alta' { $script:W.BadgeAlta.Visibility = 'Visible' } }
    Select-Tier $hw.Tier
    $inst = @($script:Launchers | Where-Object Installed)
    $script:W.LauncherBox.Items.Clear()
    foreach ($l in $inst) { [void]$script:W.LauncherBox.Items.Add($l.Name) }
    $script:W.LauncherBox.SelectedIndex = 0
    if ($inst.Count -gt 1) { $script:W.LauncherPanel.Visibility = 'Visible' } else { $script:W.LauncherPanel.Visibility = 'Collapsed' }
    Show-Page 'PageTier'
}

function Start-Check {
    Show-Page 'PageLoading'
    $script:W.LoadingText.Text = 'Comprobando tu PC...'; Pump
    $script:Launchers = @(Get-Launchers)
    if (-not ($script:Launchers | Where-Object Installed)) { Show-Page 'PageNoLauncher'; return }
    $script:Hw = Get-Hardware
    $script:W.LoadingText.Text = 'Buscando la última versión...'; Pump
    try { $script:Rel = Get-LatestRelease }
    catch {
        if ($_.Exception.Message -match '404') {
            Show-Error "Todavía no hay ninguna versión publicada del modpack (o el repositorio de GitHub es privado).`n`nAvisa a quien te pasó el instalador."
        } else {
            Show-Error "No he podido conectar con GitHub. Comprueba tu conexión a internet.`n`n($($_.Exception.Message))"
        }
        return
    }
    $script:W.VersionTag.Text = "Última versión: $($script:Rel.tag_name)"
    $latest = ConvertTo-Ver $script:Rel.tag_name
    $script:Instances = @(Get-Instances $script:Launchers)
    $script:Outdated = @($script:Instances | Where-Object { (ConvertTo-Ver $_.Version) -lt $latest })

    if ($script:Outdated.Count -gt 0) {
        $script:W.UpdTitle.Text = "¡Hay una actualización! ($($script:Rel.tag_name))"
        $script:W.UpdList.Text = 'Se actualizará: ' + (($script:Outdated | ForEach-Object { "$($_.Name) (v$($_.Version))" }) -join ', ')
        $body = [string]$script:Rel.body
        if (-not $body.Trim()) { $body = 'Mejoras y correcciones.' }
        $script:W.UpdNotes.Text = $body
        Show-Page 'PageUpdate'
    } elseif ($script:Instances.Count -gt 0) {
        $script:W.UtdTitle.Text = "Todo al día ($($script:Rel.tag_name))"
        $script:W.UtdText.Text = 'Tienes instalado: ' + (($script:Instances | ForEach-Object { "$($_.Name) en $($_.Launcher)" }) -join ', ') + '. ¡A jugar!'
        Show-Page 'PageUpToDate'
    } else {
        Show-TierPage
    }
}

function Do-Install {
    $tier = $script:SelTier
    $lname = [string]$script:W.LauncherBox.SelectedItem
    $l = @($script:Launchers | Where-Object { $_.Installed -and $_.Name -eq $lname }) | Select-Object -First 1
    if (-not $l) { $l = @($script:Launchers | Where-Object Installed)[0] }
    $script:UsedLauncher = $l
    $asset = Get-Asset $script:Rel $tier
    if (-not $asset) { throw "La versión $($script:Rel.tag_name) no incluye la $($Tiers[$tier].Title.ToLower())." }

    $script:W.ProgTitle.Text = "Descargando $($Tiers[$tier].Title.ToLower())"
    Show-Page 'PageProgress'
    Set-Progress 0 "Versión $($script:Rel.tag_name) · $([Math]::Round($asset.size / 1MB, 1)) MB" ''
    [void](New-Item -ItemType Directory -Force -Path $HelperDir)
    $file = Join-Path $HelperDir $asset.name
    Download $asset.browser_download_url $file 0 100 $asset.name
    Set-Progress 100 'Descarga completa' ''

    $script:Before = @(Get-Instances $script:Launchers | ForEach-Object { $_.Path })
    if ($l.Exe) { Start-Process -FilePath $l.Exe -ArgumentList ('"{0}"' -f $file) } else { Start-Process -FilePath $file }
    Install-Helper

    $script:W.WaitText.Text = "Se ha abierto $($l.Name) con el modpack.`n`n1.  Si te pregunta, confirma la instalación.`n2.  Espera a que termine de descargar (puede tardar unos minutos).`n`nEsta ventana detectará sola cuándo ha terminado."
    Show-Page 'PageWait'
    $script:WaitTier = $tier
    $script:WaitTicks = 0
    $script:Timer = New-Object System.Windows.Threading.DispatcherTimer
    $script:Timer.Interval = [TimeSpan]::FromSeconds(3)
    $script:Timer.Add_Tick({ Guard { Check-Installed $false } })
    $script:Timer.Start()
}

function Check-Installed([bool]$manual) {
    $new = @(Get-Instances $script:Launchers | Where-Object { $script:Before -notcontains $_.Path }) | Select-Object -First 1
    $applied = $false
    if ($new) { $applied = Set-LauncherSettings $new $false }
    $script:WaitTicks++
    # Esperar a que el launcher marque el perfil como instalado (hasta ~10 min) salvo que el jugador pulse el botón
    if ($new -and -not $applied -and -not $manual -and $script:WaitTicks -lt 200) {
        $script:W.WaitText.Text = "El launcher está terminando de instalar $($new.Name)...`n`nEn cuanto acabe configuraré la memoria automáticamente."
        return
    }
    if ($new -or $manual) {
        if ($script:Timer) { $script:Timer.Stop() }
        $ram = $Tiers[$script:WaitTier].Ram
        $lname = $script:UsedLauncher.Name
        $head = '¡Instalado!'
        if ($new) { $head = "¡Instalado! Perfil: $($new.Name)" }
        $script:W.DoneTitle.Text = $head
        if ($applied) {
            $gb = [Math]::Round((Get-TierMemoryMB $script:WaitTier) / 1024, 1)
            $script:W.DoneText.Text = "Todo configurado automáticamente:`n  • Memoria para el juego: $gb GB`n  • Ajustes de Java optimizados`n  • Se actualiza solo cada vez que pulses Jugar`n`nSi $lname estaba abierto y no ves los cambios, ciérralo y vuelve a abrirlo."
        } else {
            $script:W.DoneText.Text = "Último paso, muy importante: dale memoria al juego.`nEn $lname abre el perfil → Ajustes (engranaje) → Java y memoria, y pon $ram.`n`nPara actualizar en el futuro, abre el acceso directo `"TFC Create`" del escritorio."
        }
        Show-Page 'PageDone'
    }
}

function Do-Update {
    $script:W.ProgTitle.Text = "Actualizando a $($script:Rel.tag_name)"
    Show-Page 'PageProgress'
    $list = @($script:Outdated); $k = 0
    foreach ($i in $list) {
        if (Test-GameRunning $i.Path) { throw "Minecraft está abierto con el perfil '$($i.Name)'. Ciérralo y pulsa Reintentar." }
        $from = 100.0 * $k / $list.Count; $to = 100.0 * ($k + 1) / $list.Count
        Update-Instance $i $script:Rel $from $to
        $k++
    }
    Install-Helper
    $script:W.DoneTitle.Text = "¡Actualizado a $($script:Rel.tag_name)!"
    $script:W.DoneText.Text = 'Tus mundos y ajustes se han conservado. Ya puedes jugar.'
    Show-Page 'PageDone'
}

function Run-Gui {
    Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Drawing
    $script:Ui = $true
    $win = [Windows.Markup.XamlReader]::Parse($Xaml)
    $script:W = @{ Window = $win }
    $names = [regex]::Matches($Xaml, 'x:Name="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    foreach ($n in $names) { $el = $win.FindName($n); if ($el) { $script:W[$n] = $el } }

    # Icono del exe como logo e icono de ventana
    try {
        if ($env:TFC_SELF -and (Test-Path -LiteralPath $env:TFC_SELF)) {
            $ico = [System.Drawing.Icon]::ExtractAssociatedIcon($env:TFC_SELF)
            $bmp = New-Object System.Drawing.Icon($ico, 256, 256)
            $src = [System.Windows.Interop.Imaging]::CreateBitmapSourceFromHIcon($bmp.Handle, [System.Windows.Int32Rect]::Empty, [System.Windows.Media.Imaging.BitmapSizeOptions]::FromEmptyOptions())
            $win.Icon = $src; $script:W.Logo.Source = $src
        }
    } catch { }
    try {
        $png = Join-Path $HelperDir 'logo.png'
        if ($env:TFC_LOGO -and (Test-Path -LiteralPath $env:TFC_LOGO)) { $png = $env:TFC_LOGO }
        if (Test-Path -LiteralPath $png) {
            $bi = New-Object System.Windows.Media.Imaging.BitmapImage
            $bi.BeginInit(); $bi.UriSource = New-Object Uri($png); $bi.CacheOption = 'OnLoad'; $bi.EndInit()
            $script:W.Logo.Source = $bi
        }
    } catch { }

    foreach ($c in 'CardBaja', 'CardMedia', 'CardAlta') {
        $script:W[$c].Add_MouseLeftButtonUp({ param($s, $e) Select-Tier ([string]$s.Tag) })
    }
    $script:W.BtnGetModrinth.Add_Click({ Start-Process 'https://modrinth.com/app' })
    $script:W.BtnGetMigu.Add_Click({ Start-Process 'https://github.com/MiguVT/migurinth/releases/latest' })
    $script:W.BtnRecheck.Add_Click({ Guard { Start-Check } })
    $script:W.BtnInstall.Add_Click({ Guard { Do-Install } })
    $script:W.BtnWaitDone.Add_Click({ Guard { Check-Installed $true } })
    $script:W.BtnUpdate.Add_Click({ Guard { Do-Update } })
    $script:W.BtnOtherTier.Add_Click({ Guard { Show-TierPage } })
    $script:W.BtnOtherTier2.Add_Click({ Guard { Show-TierPage } })
    $script:W.BtnOpenLauncher.Add_Click({ Guard { Open-Launcher; $script:W.Window.Close() } })
    $script:W.BtnPlay.Add_Click({ Guard { Open-Launcher; $script:W.Window.Close() } })
    $script:W.BtnRetry.Add_Click({ Guard { Start-Check } })
    foreach ($b in 'BtnClose', 'BtnClose2', 'BtnClose3') { $script:W[$b].Add_Click({ $script:W.Window.Close() }) }

    $win.Add_ContentRendered({ $script:W.Window.Activate(); Guard { Start-Check } })
    [void]$win.ShowDialog()
}

# ======================= Arranque =======================
if (-not $env:TFC_NO_MAIN) {
    if ([string]$env:TFC_ARGS -match '(^|\s)-auto(\s|$)') { Run-Auto }
    else {
        try { Run-Gui }
        catch {
            Add-Type -AssemblyName System.Windows.Forms
            [void][System.Windows.Forms.MessageBox]::Show("No se pudo abrir el instalador:`n`n$($_.Exception.Message)", $PackTitle, 'OK', 'Error')
        }
    }
}
