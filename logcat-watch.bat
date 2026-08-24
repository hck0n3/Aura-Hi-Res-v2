@echo off
set ADB="C:\Users\AURA\AppData\Local\Android\Sdk\platform-tools\adb.exe"
for /f %%p in ('%ADB% -s e6f2c8eb shell pidof iad1tya.aura.music.debug') do set PID=%%p
if "%PID%"=="" (
  echo APP_NOT_RUNNING
  exit /b 1
)
echo WATCHING_PID=%PID%
%ADB% -s e6f2c8eb logcat -v time --pid=%PID%
