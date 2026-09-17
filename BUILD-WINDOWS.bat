@echo off
setlocal
cd /d "%~dp0"
if exist "C:\Gradle\bin\gradle.bat" (
    call "C:\Gradle\bin\gradle.bat" build
) else (
    call gradle build
)
if errorlevel 1 (
    echo.
    echo Build failed. Copy the error text when reporting the problem.
) else (
    echo.
    echo Your mod is in build\libs. Use the jar WITHOUT -sources in its name.
)
pause
