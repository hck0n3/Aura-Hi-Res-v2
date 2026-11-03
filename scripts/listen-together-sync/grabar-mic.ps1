# Graba N segundos del micrófono de la laptop a un WAV mono de 44,1 kHz (winmm, sin instalar nada).
# Uso:  .\grabar-mic.ps1 -Segundos 40 -Salida C:\Users\PC\mic.wav
# Nota: si el guardado devuelve 304, guarda en una ruta corta (por ejemplo C:\Users\PC\x.wav) y mueve el archivo después.
param([int]$Segundos = 40, [string]$Salida = "$env:USERPROFILE\mic.wav")
$sig = @'
[DllImport("winmm.dll", CharSet = CharSet.Auto)]
public static extern int mciSendString(string command, System.Text.StringBuilder ret, int retLen, IntPtr cb);
'@
Add-Type -MemberDefinition $sig -Name MciRec -Namespace Win32 | Out-Null
$sb = New-Object System.Text.StringBuilder 256
[void][Win32.MciRec]::mciSendString('open new Type waveaudio Alias lt', $sb, 256, [IntPtr]::Zero)
[void][Win32.MciRec]::mciSendString('set lt bitspersample 16 channels 1 samplespersec 44100 alignment 2 bytespersec 88200', $sb, 256, [IntPtr]::Zero)
[void][Win32.MciRec]::mciSendString('record lt', $sb, 256, [IntPtr]::Zero)
Start-Sleep -Seconds $Segundos
[void][Win32.MciRec]::mciSendString('stop lt', $sb, 256, [IntPtr]::Zero)
$r = [Win32.MciRec]::mciSendString("save lt `"$Salida`"", $sb, 256, [IntPtr]::Zero)
[void][Win32.MciRec]::mciSendString('close lt', $sb, 256, [IntPtr]::Zero)
"guardado=$r existe=$(Test-Path $Salida)"
