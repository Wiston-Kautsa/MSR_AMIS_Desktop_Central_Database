@echo off
setlocal

set "PROJECT_ROOT=%~dp0.."
set "APP_EXE=%PROJECT_ROOT%\dist\MSR AMIS\MSR AMIS.exe"

if not exist "%APP_EXE%" (
  echo Desktop app image not found.
  echo Build it first with:
  echo   scripts\build-desktop.cmd
  exit /b 1
)

start "" "%APP_EXE%"
