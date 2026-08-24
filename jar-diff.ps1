$old = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jsoup\jsoup\1.22.2\3e7aa5e79f51efa3d0ec15140e186140173119f7\jsoup-1.22.2.jar"
$new = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jsoup\jsoup\1.23.1\c0350bb325da274f0508349109516a7855d01ab\jsoup-1.23.1.jar"
Write-Output ("old sha256 = " + (Get-FileHash $old -Algorithm SHA256).Hash)
Write-Output ("new sha256 = " + (Get-FileHash $new -Algorithm SHA256).Hash)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zo = [IO.Compression.ZipFile]::OpenRead($old)
$zn = [IO.Compression.ZipFile]::OpenRead($new)
$mo = @{}; foreach ($e in $zo.Entries) { $mo[$e.FullName] = $e.Crc32 }
$mn = @{}; foreach ($e in $zn.Entries) { $mn[$e.FullName] = $e.Crc32 }
$zo.Dispose(); $zn.Dispose()
$changed = @()
foreach ($k in $mn.Keys) {
  if (-not $mo.ContainsKey($k)) { Write-Output "ADDED   $k"; $changed += $k }
  elseif ($mo[$k] -ne $mn[$k]) { Write-Output "CHANGED $k"; $changed += $k }
}
foreach ($k in $mo.Keys) { if (-not $mn.ContainsKey($k)) { Write-Output "REMOVED $k" } }
Write-Output ("total changed/added = " + $changed.Count)
