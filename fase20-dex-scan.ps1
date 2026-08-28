# FASE 20: verifica ofuscacion R8 real y presencia de strings sensibles en los dex del APK release
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path 'app\build\outputs\apk\universalFoss\release\app-universal-foss-release.apk').Path
$patterns = @(
    'com/music/echo/',
    'Lcom/music/echo/MainActivity;',
    'Lcom/music/echo/App;',
    'iad1tya/echo/music/',
    'timber/log/Timber',
    'android/util/Log',
    'Superpowered',
    'lastfm',
    'api_key',
    'BuildConfig',
    'Lcom/music/echo/utils/',
    'Lcom/music/echo/playback/',
    'Lcom/music/echo/ui/'
)
$z = [IO.Compression.ZipFile]::OpenRead($apk)
$dexTexts = @{}
foreach ($e in $z.Entries | Where-Object { $_.FullName -match '^classes.*\.dex$' }) {
    $ms = New-Object IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
    $dexTexts[$e.FullName] = [Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray())
    $ms.Close()
}
foreach ($p in $patterns) {
    $total = 0
    foreach ($k in ($dexTexts.Keys | Sort-Object)) {
        $total += [Regex]::Matches($dexTexts[$k], [Regex]::Escape($p)).Count
    }
    Write-Output ("PATTERN {0} -> total {1}" -f $p, $total)
}
# clases renombradas cortas dentro del paquete de la app (evidencia directa de R8)
foreach ($k in ($dexTexts.Keys | Sort-Object)) {
    $short = [Regex]::Matches($dexTexts[$k], 'Lcom/music/echo/[A-Za-z0-9$_]{1,3};')
    Write-Output ("RENAMED-SHORT {0} -> {1} (ej: {2})" -f $k, $short.Count, (($short | Select-Object -First 5 | ForEach-Object Value) -join ' '))
}
# entradas de metadatos que filtran info de build
foreach ($e in $z.Entries) {
    if ($e.FullName -match 'kotlin-module|version-control-info|\.kotlin_builtins$') {
        Write-Output ("META-ENTRY {0} size={1}" -f $e.FullName, $e.Length)
    }
}
$z.Dispose()
Write-Output 'scan-done'
