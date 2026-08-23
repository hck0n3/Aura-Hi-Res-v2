param(
  [string]$JsonlPath
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $JsonlPath)) { Write-Output "No existe: $JsonlPath"; exit 1 }
$i = Get-Item $JsonlPath
$age = (Get-Date) - $i.LastWriteTime
Write-Output ('Tamano: ' + [math]::Round($i.Length / 1KB) + ' KB')
Write-Output ('Ultima escritura: ' + $i.LastWriteTime.ToString('HH:mm:ss') + ' (hace ' + [math]::Round($age.TotalSeconds) + ' s)')
$lines = Get-Content $JsonlPath
Write-Output ('Lineas de transcripcion: ' + $lines.Count)
Write-Output '--- Ultimas herramientas usadas por el agente ---'
$lines | Select-Object -Last 20 | ForEach-Object {
  if ($_ -match '"name":"(mcp__[a-z_-]+__[a-zA-Z_0-9]+|[a-zA-Z_0-9]+)"') { $matches[1] }
} | Where-Object { $_ -notin @('agent','general-purpose') }
