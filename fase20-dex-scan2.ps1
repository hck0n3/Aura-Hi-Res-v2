# FASE 20 (pase 2): cobertura de ofuscacion dentro del paquete real iad1tya/echo/music
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path 'app\build\outputs\apk\universalFoss\release\app-universal-foss-release.apk').Path
$z = [IO.Compression.ZipFile]::OpenRead($apk)
$all = New-Object System.Collections.Generic.HashSet[string]
foreach ($e in $z.Entries | Where-Object { $_.FullName -match '^classes.*\.dex$' }) {
    $ms = New-Object IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
    $text = [Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray())
    $ms.Close()
    foreach ($m in [Regex]::Matches($text, 'Liad1tya/echo/music/[A-Za-z0-9$_/]+;')) {
        [void]$all.Add($m.Value)
    }
}
$z.Dispose()
$sorted = $all | Sort-Object
Write-Output ("DESCRIPTORES UNICOS iad1tya/echo/music: {0}" -f $sorted.Count)
Write-Output '--- RENOMBRADAS (nombre corto 1-3 chars en ultimo segmento) ---'
$renamed = $sorted | Where-Object { $_ -match 'Liad1tya/echo/music/(?:[A-Za-z0-9$_]+/)*[A-Za-z0-9$_]{1,3};' }
Write-Output ("renombradas: {0}" -f $renamed.Count)
($renamed | Select-Object -First 10) -join "`n"
Write-Output '--- SIN RENOMBRAR (nombre largo visible) ---'
$kept = $sorted | Where-Object { $_ -notmatch 'Liad1tya/echo/music/(?:[A-Za-z0-9$_]+/)*[A-Za-z0-9$_]{1,3};' }
Write-Output ("sin renombrar: {0}" -f $kept.Count)
($kept | Select-Object -First 80) -join "`n"
Write-Output 'scan-done'
