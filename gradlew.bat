@echo off
setlocal
set GRADLE_VERSION=8.13
set ROOT_DIR=%~dp0
set BOOT_DIR=%ROOT_DIR%.gradle-bootstrap
set GRADLE_DIR=%BOOT_DIR%\gradle-%GRADLE_VERSION%
set ZIP=%BOOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
set EXPECTED_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  if not exist "%BOOT_DIR%" mkdir "%BOOT_DIR%"
  if not exist "%ZIP%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP%'"
    if errorlevel 1 exit /b 1
  )
  for /f %%H in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLowerInvariant()"') do set ACTUAL_SHA256=%%H
  if /I not "%ACTUAL_SHA256%"=="%EXPECTED_SHA256%" (
    echo Error: Gradle distribution checksum mismatch.
    del /q "%ZIP%"
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BOOT_DIR%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_DIR%\bin\gradle.bat" %*
endlocal
