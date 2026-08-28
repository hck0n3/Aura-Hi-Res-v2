# FASE 19: localiza los XML de backup/network/provider dentro del APK (nombres ofuscados por R8)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path 'app\build\outputs\apk\universalFoss\release\app-universal-foss-release.apk').Path
$tmp = Join-Path $env:TEMP 'fase19-xml'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Path $tmp | Out-Null

$targets = @{
    'full-backup-rule'        = 'backup_rules'
    'full-backup-content'     = 'backup_rules'
    'data-extraction-rules'   = 'data_extraction_rules'
    'network-security-config' = 'network_security_config'
}

$z = [IO.Compression.ZipFile]::OpenRead($apk)
foreach ($e in $z.Entries | Where-Object { $_.FullName -match '^res/[^/]+\.xml$' }) {
    $dest = Join-Path $tmp ($e.FullName.Replace('/', '_'))
    $s = $e.Open(); $f = [IO.File]::Create($dest); $s.CopyTo($f); $f.Close(); $s.Close()
    $bytes = [IO.File]::ReadAllBytes($dest)
    $utf8 = [Text.Encoding]::UTF8.GetString($bytes)
    $utf16 = [Text.Encoding]::Unicode.GetString($bytes)
    foreach ($k in $targets.Keys) {
        if ($utf8.Contains($k) -or $utf16.Contains($k)) {
            Write-Output ("{0}  ->  {1}" -f $e.FullName, $targets[$k])
        }
    }
    # provider_paths: raíz <paths> con external-path/cache-path
    if (($utf8.Contains('external-path') -or $utf16.Contains('external-path')) -and ($utf8.Contains('cache-path') -or $utf16.Contains('cache-path'))) {
        Write-Output ("{0}  ->  provider_paths" -f $e.FullName)
    }
}
$z.Dispose()
