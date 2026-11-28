# ZEDGROUND - dedicated server setup (Windows)
# Sets up a Project Zomboid dedicated server for the ZEDGROUND mods in one go.
#   You pick the .bat file you use to start the server (StartServer64.bat,
#   or your own copy such as API-Test.bat).
#   Install: copies next to that .bat
#              HaloDropAgent.jar              ([ZEDGROUND] HALO Drop: terrain preloading while falling)
#              ZombieBuddy.jar + zbNative.dll (Java mods on the server: [ZEDGROUND] GunWorks hit validation)
#            and puts on its java line
#              -javaagent:"<full path>\HaloDropAgent.jar"
#              -agentpath:"<full path>\zbNative.dll"=policy=allow-all
#              ... --   (ZombieBuddy needs the java line to end with --)
#            A backup <name>.bat.zedground-backup is made once.
#   Remove : takes those options out of that .bat and deletes the copied files.
# Run it again after a SteamCMD server update (StartServer64.bat may be reset)
# or after a Halo Drop / ZombieBuddy update (the copied files may be old).
# Replaces what HaloDrop-ServerSetup.bat did (its agent option is taken over, not doubled).
# ASCII only on purpose: Windows PowerShell 5 reads BOM-less files as CP949.

$ErrorActionPreference = 'Stop'
$here     = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $here
$zbWorkshopId = '3619862853'

# java launcher at the start of a line: "...\java.exe", ...\java.exe, java.exe or java (javaw too)
$javaRe = New-Object System.Text.RegularExpressions.Regex('(?im)^(\s*(?:"[^"\r\n]*\\?javaw?\.exe"|[^\s"\r\n]*\\javaw?\.exe|javaw?\.exe|javaw?))(?=\s)')
# options this script owns, quoted or not, with the space before them
$haloRe = New-Object System.Text.RegularExpressions.Regex(' ?-javaagent:(?:"[^"\r\n]*HaloDropAgent\.jar"|[^\s"\r\n]*HaloDropAgent\.jar)', 'IgnoreCase')
$zbRe   = New-Object System.Text.RegularExpressions.Regex(' ?(?:-agent(?:path|lib):(?:"[^"\r\n]*zbNative(?:\.dll)?"|[^\s"=\r\n]*zbNative(?:\.dll)?)|-javaagent:(?:"[^"\r\n]*ZombieBuddy\.jar"|[^\s"=\r\n]*ZombieBuddy\.jar))(?:=[^\s\r\n]*)?', 'IgnoreCase')

function Say([string]$m)  { Write-Host ('[ZEDGROUND] ' + $m) }
function Warn([string]$m) { Write-Host ('[ZEDGROUND] WARNING: ' + $m) -ForegroundColor Yellow }
function Fail([string]$m) { Write-Host ('[ZEDGROUND] ERROR: ' + $m) -ForegroundColor Red }

function Pick-BatFile {
    $path = $null
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $d = New-Object System.Windows.Forms.OpenFileDialog
        $d.Title = 'Select the .bat file you use to start the dedicated server (e.g. StartServer64.bat)'
        $d.Filter = 'Batch files (*.bat;*.cmd)|*.bat;*.cmd|All files (*.*)|*.*'
        $d.CheckFileExists = $true
        $d.Multiselect = $false
        if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { $path = $d.FileName }
    } catch {
        Say 'File window could not open. Type or paste the path of the .bat instead.'
        $path = Read-Host 'Server start .bat file'
    }
    if ($path) { $path = $path.Trim().Trim('"') }
    return $path
}

function Read-Bat([string]$file) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $enc = New-Object System.Text.UTF8Encoding($bom)
    $text = $enc.GetString($bytes)
    if ($bom) { $text = $text.Substring(1) }
    return @{ Text = $text; Enc = $enc }
}

function Write-Bat([string]$file, $bat, [string]$text) {
    [System.IO.File]::WriteAllText($file, $text, $bat.Enc)
}

function Check-Bat([string]$file) {
    if (-not $file) { Fail 'No file selected.'; return $false }
    if (-not [System.IO.File]::Exists($file)) { Fail ('File not found: ' + $file); return $false }
    $ext = [System.IO.Path]::GetExtension($file).ToLowerInvariant()
    if ($ext -ne '.bat' -and $ext -ne '.cmd') { Fail ('Not a .bat / .cmd file: ' + $file); return $false }
    $bat = Read-Bat $file
    if (-not $javaRe.IsMatch($bat.Text)) {
        Fail 'This file has no line that starts with java / java.exe. Pick the .bat that actually starts the server.'
        return $false
    }
    return $true
}

# Steam library folders on this PC (for files a client installation has subscribed)
function Steam-Libraries {
    $libs = New-Object System.Collections.Generic.List[string]
    try {
        $steam = (Get-ItemProperty -Path 'HKCU:\Software\Valve\Steam' -Name 'SteamPath' -ErrorAction Stop).SteamPath
        if ($steam) {
            $steam = $steam -replace '/', '\'
            $libs.Add($steam)
            $vdf = Join-Path $steam 'steamapps\libraryfolders.vdf'
            if ([System.IO.File]::Exists($vdf)) {
                foreach ($m in [regex]::Matches([System.IO.File]::ReadAllText($vdf), '"path"\s+"([^"]+)"')) {
                    $libs.Add(($m.Groups[1].Value -replace '\\\\', '\'))
                }
            }
        }
    } catch { }
    return $libs
}

# The newest file among the given candidates that exist.
function Newest($paths) {
    $best = $null
    foreach ($p in $paths) {
        if ($p -and [System.IO.File]::Exists($p)) {
            if (-not $best -or [System.IO.File]::GetLastWriteTimeUtc($p) -gt [System.IO.File]::GetLastWriteTimeUtc($best)) { $best = $p }
        }
    }
    return $best
}

function Find-HaloAgent([string]$serverDir) {
    $c = New-Object System.Collections.Generic.List[string]
    # this repository (the setup was started from the ZEDGROUND folder)
    $c.Add((Join-Path $repoRoot 'Contents\mods\[ZEDGROUND] HALO Drop\server-agent\HaloDropAgent.jar'))
    # what the server downloaded from the Workshop, and what a client on this PC subscribed
    $roots = @((Join-Path $serverDir 'steamapps\workshop\content\108600'))
    foreach ($l in (Steam-Libraries)) { $roots += (Join-Path $l 'steamapps\workshop\content\108600') }
    foreach ($r in $roots) {
        if (-not [System.IO.Directory]::Exists($r)) { continue }
        foreach ($item in [System.IO.Directory]::GetDirectories($r)) {
            $mods = Join-Path $item 'mods'
            if (-not [System.IO.Directory]::Exists($mods)) { continue }
            foreach ($mod in [System.IO.Directory]::GetDirectories($mods)) {
                $c.Add((Join-Path $mod 'server-agent\HaloDropAgent.jar'))
            }
        }
    }
    return Newest $c
}

function Find-ZombieBuddyLibs([string]$serverDir) {
    $c = New-Object System.Collections.Generic.List[string]
    $c.Add((Join-Path $serverDir ('steamapps\workshop\content\108600\' + $zbWorkshopId + '\mods\ZombieBuddy\libs\ZombieBuddy.jar')))
    foreach ($l in (Steam-Libraries)) {
        $c.Add((Join-Path $l ('steamapps\workshop\content\108600\' + $zbWorkshopId + '\mods\ZombieBuddy\libs\ZombieBuddy.jar')))
    }
    foreach ($jar in $c) {
        if ([System.IO.File]::Exists($jar) -and [System.IO.File]::Exists((Join-Path (Split-Path -Parent $jar) 'zbNative.dll'))) {
            return (Split-Path -Parent $jar)
        }
    }
    return $null
}

# Removes our options from every java line, and the trailing -- this script added.
function Strip-Options([string]$text, [bool]$dropDashes) {
    $text = $haloRe.Replace($text, '')
    $text = $zbRe.Replace($text, '')
    if ($dropDashes) {
        $lines = $text -split "`n"
        for ($i = 0; $i -lt $lines.Length; $i++) {
            if ($javaRe.IsMatch($lines[$i])) {
                $cr = $lines[$i].EndsWith("`r")
                $l = $lines[$i].TrimEnd("`r").TrimEnd()
                if ($l.EndsWith(' --')) { $l = $l.Substring(0, $l.Length - 3).TrimEnd() }
                $lines[$i] = $l + $(if ($cr) { "`r" } else { '' })
            }
        }
        $text = $lines -join "`n"
    }
    return $text
}

function Server-Name([string]$text) {
    $m = [regex]::Match($text, '-servername\s+"?([^"\s]+)"?', 'IgnoreCase')
    if ($m.Success) { return $m.Groups[1].Value }
    return 'servertest'
}

# Warns when the server's Mods= line misses what the Java part needs (nothing is changed there).
function Check-ServerIni([string]$serverName) {
    $ini = Join-Path $env:USERPROFILE ('Zomboid\Server\' + $serverName + '.ini')
    if (-not [System.IO.File]::Exists($ini)) {
        Say ('Server settings not found yet (' + $ini + '). Run the server once, then check Mods= has ZombieBuddy.')
        return
    }
    $mods = ''
    foreach ($line in [System.IO.File]::ReadAllLines($ini)) {
        if ($line.StartsWith('Mods=')) { $mods = $line.Substring(5) }
    }
    $list = @($mods -split ';' | ForEach-Object { $_.Trim().TrimStart('\') })
    foreach ($need in @('ZombieBuddy')) {
        if ($list -notcontains $need) { Warn ($serverName + '.ini: Mods= has no ' + $need + '. Add it, or the Java mods will not load.') }
    }
    Say ('Checked ' + $serverName + '.ini (Mods=).')
}

function Install([string]$batFile) {
    $dir = Split-Path -Parent $batFile
    if ($dir -match '[^\x00-\x7F]') {
        Warn 'the server folder path has non-English characters. If the server cannot find the agents, move the server to an English-only path.'
    }

    $haloSrc = Find-HaloAgent $dir
    if (-not $haloSrc) {
        Fail 'HaloDropAgent.jar not found (not in this ZEDGROUND folder, not in the server''s Workshop downloads, not in Steam''s).'
        Say  'Start the server once with [ZEDGROUND] HALO Drop in Mods= / WorkshopItems= so it downloads, then run this again.'
        return
    }
    $zbLibs = Find-ZombieBuddyLibs $dir
    if (-not $zbLibs) {
        Fail ('ZombieBuddy (Workshop ' + $zbWorkshopId + ') not found in the server''s Workshop downloads or in Steam''s.')
        Say  'Start the server once with ZombieBuddy in Mods= / WorkshopItems= so it downloads, then run this again.'
        return
    }

    $haloDst = Join-Path $dir 'HaloDropAgent.jar'
    $zbJar   = Join-Path $dir 'ZombieBuddy.jar'
    $zbDll   = Join-Path $dir 'zbNative.dll'
    [System.IO.File]::Copy($haloSrc, $haloDst, $true)
    Say ('Copied HaloDropAgent.jar  <- ' + $haloSrc)
    [System.IO.File]::Copy((Join-Path $zbLibs 'ZombieBuddy.jar'), $zbJar, $true)
    [System.IO.File]::Copy((Join-Path $zbLibs 'zbNative.dll'), $zbDll, $true)
    $zbs = Join-Path $zbLibs 'ZombieBuddy.jar.zbs'
    if ([System.IO.File]::Exists($zbs)) { [System.IO.File]::Copy($zbs, ($zbJar + '.zbs'), $true) }
    Say ('Copied ZombieBuddy files  <- ' + $zbLibs)

    $backup = $batFile + '.zedground-backup'
    if (-not [System.IO.File]::Exists($backup)) {
        [System.IO.File]::Copy($batFile, $backup, $false)
        Say ('Backup made -> ' + $backup)
    }

    # full paths, so it works whatever folder the .bat changes into (cd /d ...)
    $options = '-javaagent:"' + $haloDst + '" -agentpath:"' + $zbDll + '"=policy=allow-all'
    $bat  = Read-Bat $batFile
    $text = Strip-Options $bat.Text $false
    $m = $javaRe.Match($text)
    if (-not $m.Success) {
        Fail 'Could not find the java line in the .bat. Nothing was changed.'
        return
    }
    $text = $text.Substring(0, $m.Index + $m.Length) + ' ' + $options + $text.Substring($m.Index + $m.Length)

    # the java line must end with --
    $lineEnd = $text.IndexOf("`n", $m.Index)
    if ($lineEnd -lt 0) { $lineEnd = $text.Length }
    $line = $text.Substring($m.Index, $lineEnd - $m.Index)
    $cr = $line.EndsWith("`r")
    $body = $line.TrimEnd("`r").TrimEnd()
    if (-not $body.EndsWith(' --')) { $body = $body + ' --' }
    $text = $text.Substring(0, $m.Index) + $body + $(if ($cr) { "`r" } else { '' }) + $text.Substring($lineEnd)

    if ($text -ne $bat.Text) {
        Write-Bat $batFile $bat $text
        $check = (Read-Bat $batFile).Text
        if (-not $haloRe.IsMatch($check) -or -not $zbRe.IsMatch($check)) { Fail 'Writing the .bat failed.'; return }
        Say ('Options set in ' + [System.IO.Path]::GetFileName($batFile) + ':  ' + $options + ' ... --')
    } else {
        Say ([System.IO.Path]::GetFileName($batFile) + ' already had the options (only the files were updated).')
    }

    Check-ServerIni (Server-Name $text)
    Say ('Done. Start the server with ' + [System.IO.Path]::GetFileName($batFile) + ' as usual.')
    Say 'Check <your user folder>\Zomboid\server-console.txt for:'
    Say '  "[HaloDropAgent] registered HaloDropNative.state/preload"'
    Say '  "[GunWorks] ... loading (dedicated server)"'
}

function Remove([string]$batFile) {
    $dir = Split-Path -Parent $batFile
    $bat = Read-Bat $batFile
    $text = Strip-Options $bat.Text $true
    if ($text -ne $bat.Text) {
        Write-Bat $batFile $bat $text
        Say ('Removed the options from ' + [System.IO.Path]::GetFileName($batFile) + '.')
    } else {
        Say ([System.IO.Path]::GetFileName($batFile) + ' has none of the options.')
    }
    foreach ($f in @('HaloDropAgent.jar', 'ZombieBuddy.jar', 'ZombieBuddy.jar.zbs', 'zbNative.dll')) {
        $p = Join-Path $dir $f
        if ([System.IO.File]::Exists($p)) {
            [System.IO.File]::Delete($p)
            Say ('Deleted -> ' + $p)
        }
    }
    Say 'Done.'
}

try {
    Write-Host ''
    Write-Host '  ZEDGROUND - dedicated server setup'
    Write-Host '  (Halo Drop agent + ZombieBuddy for the server)'
    Write-Host '  1) Install / update'
    Write-Host '  2) Remove'
    Write-Host '  other) Quit'
    Write-Host ''
    $choice = (Read-Host 'Choose').Trim()
    if ($choice -ne '1' -and $choice -ne '2') { Say 'Nothing done.'; return }
    Say 'Select the .bat file you start the dedicated server with.'
    $batFile = Pick-BatFile
    if (-not (Check-Bat $batFile)) { return }
    Say ('Server start file: ' + $batFile)
    if ($choice -eq '1') { Install $batFile } else { Remove $batFile }
} catch {
    Fail $_.Exception.Message
}
