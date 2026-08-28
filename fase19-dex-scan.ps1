# FASE 19: busca telemetría Firebase/GMS analytics dentro de los dex del APK release
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = (Resolve-Path 'app\build\outputs\apk\universalFoss\release\app-universal-foss-release.apk').Path
$patterns = @(
    'com/google/firebase',
    'com/google/android/gms/measurement',
    'com/google/android/gms/analytics',
    'firebase-analytics',
    'com/google/firebase/analytics'
)
$z = [IO.Compression.ZipFile]::OpenRead($apk)
foreach ($e in $z.Entries | Where-Object { $_.FullName -match '^classes.*\.dex$' }) {
    $ms = New-Object IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
    $text = [Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray())
    $ms.Close()
    foreach ($p in $patterns) {
        $hits = [Regex]::Matches($text, [Regex]::Escape($p)).Count
        if ($hits -gt 0) { Write-Output ("{0}: {1} -> {2} hits" -f $e.FullName, $p, $hits) }
    }
}
$z.Dispose()
Write-Output 'scan-done'
