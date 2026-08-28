# FASE 20 (pase 3): amplitud global del renombrado R8 + paquetes de app sin renombrar
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path 'app\build\outputs\apk\universalFoss\release\app-universal-foss-release.apk').Path
$z = [IO.Compression.ZipFile]::OpenRead($apk)
$all = New-Object System.Collections.Generic.HashSet[string]
$bc = ''
foreach ($e in $z.Entries | Where-Object { $_.FullName -match '^classes.*\.dex$' }) {
    $ms = New-Object IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
    $text = [Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray())
    $ms.Close()
    foreach ($m in [Regex]::Matches($text, 'L[A-Za-z0-9$_/]+;')) { [void]$all.Add($m.Value) }
    $bc += $text
}
$z.Dispose()
$total = $all.Count
$short = $all | Where-Object { $_ -match 'L(?:[A-Za-z0-9$_]+/)*[A-Za-z0-9$_]{1,3};' }
Write-Output ("DESCRIPTORES UNICOS TOTALES: {0}" -f $total)
Write-Output ("RENOMBRADAS GLOBAL (nombre corto): {0} ({1:P1})" -f $short.Count, ($short.Count / [double]$total))
# paquetes de segundo nivel de la app con clases sin renombrar
$app = $all | Where-Object { $_ -match '^Liad1tya/echo/music/' }
$byPkg = @{}
foreach ($d in $app) {
    $inner = $d -replace '^Liad1tya/echo/music/', '' -replace ';$', ''
    $pkg = if ($inner -match '/') { ($inner -split '/')[0] } else { '(raiz)' }
    if (-not $byPkg.ContainsKey($pkg)) { $byPkg[$pkg] = 0 }
    $byPkg[$pkg]++
}
Write-Output '--- PAQUETES APP (descriptores por paquete) ---'
foreach ($k in ($byPkg.Keys | Sort-Object)) { Write-Output ("{0}: {1}" -f $k, $byPkg[$k]) }
# clases clave especificas: presentes o ausentes
foreach ($c in @('Liad1tya/echo/music/MainActivity;',
                 'Liad1tya/echo/music/MusicService;',
                 'Liad1tya/echo/music/utils/Utils;',
                 'Liad1tya/echo/music/utils/AppLogger;',
                 'Liad1tya/echo/music/App;',
                 'Liad1tya/echo/music/lyrics/',
                 'Liad1tya/echo/music/ui/')) {
    Write-Output ("CHECK {0} -> {1}" -f $c, $(if ($bc.Contains($c)) { 'PRESENTE' } else { 'AUSENTE' }))
}
# contexto del string BuildConfig
$i = $bc.IndexOf('BuildConfig')
if ($i -ge 0) { Write-Output ("BUILDCONFIG-CTX: " + ($bc.Substring([Math]::Max(0, $i - 60), 120) -replace '[^\x20-\x7E]', '.')) }
Write-Output 'scan-done'
