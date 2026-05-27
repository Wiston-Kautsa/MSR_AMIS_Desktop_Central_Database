@echo off
setlocal

set "APP_NAME=MSR AMIS"
set "APP_VERSION=1.0.0"
set "MAIN_JAR=MSR-AMIS-1.0-SNAPSHOT.jar"
set "MAIN_CLASS=com.mycompany.msr.amis.App"
set "BUILD_ROOT=%~dp0..\"
set "TARGET_DIR=%BUILD_ROOT%target"
set "INPUT_DIR=%TARGET_DIR%\desktop-input"
set "DIST_DIR=%BUILD_ROOT%dist"
set "APP_DIR=%DIST_DIR%\%APP_NAME%"
set "INSTALLER_BASENAME=%APP_NAME%-%APP_VERSION%"
set "INSTALLER_EXE=%DIST_DIR%\%INSTALLER_BASENAME%.exe"
set "INSTALLER_MSI=%DIST_DIR%\%INSTALLER_BASENAME%.msi"
set "ICON_PATH=%BUILD_ROOT%packaging\msr-amis-icon.ico"
set "JAVAFX_VERSION=17.0.8"
set "M2_REPO=%USERPROFILE%\.m2\repository"
set "JAVAFX_MODULE_PATH=%M2_REPO%\org\openjfx\javafx-base\%JAVAFX_VERSION%\javafx-base-%JAVAFX_VERSION%-win.jar;%M2_REPO%\org\openjfx\javafx-graphics\%JAVAFX_VERSION%\javafx-graphics-%JAVAFX_VERSION%-win.jar;%M2_REPO%\org\openjfx\javafx-controls\%JAVAFX_VERSION%\javafx-controls-%JAVAFX_VERSION%-win.jar;%M2_REPO%\org\openjfx\javafx-fxml\%JAVAFX_VERSION%\javafx-fxml-%JAVAFX_VERSION%-win.jar"
set "RUNTIME_MODULES=java.sql,java.net.http,java.prefs,javafx.controls,javafx.fxml"
set "UPGRADE_UUID=7a94ac66-f88f-4a0d-86b5-34c407af6f22"
set "WIX_HOME=%ProgramFiles%\WiX Toolset v7.0\bin"

if exist "%WIX_HOME%\wix.exe" set "PATH=%WIX_HOME%;%PATH%"

if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
if exist "%APP_DIR%" rmdir /s /q "%APP_DIR%"
if exist "%APP_DIR%" (
  echo.
  echo Close the packaged app and try again. Output folder is locked:
  echo %APP_DIR%
  exit /b 1
)
if exist "%INSTALLER_EXE%" del /f /q "%INSTALLER_EXE%"
if exist "%INSTALLER_EXE%" (
  echo.
  echo Close the generated installer and try again. Output file is locked:
  echo %INSTALLER_EXE%
  exit /b 1
)
if exist "%INSTALLER_MSI%" del /f /q "%INSTALLER_MSI%"
if exist "%INSTALLER_MSI%" (
  echo.
  echo Close the generated installer and try again. Output file is locked:
  echo %INSTALLER_MSI%
  exit /b 1
)

call "%BUILD_ROOT%mvnw.cmd" -q -DskipTests clean package dependency:copy-dependencies -DoutputDirectory="%INPUT_DIR%"
if errorlevel 1 goto :fail

if not exist "%INPUT_DIR%" mkdir "%INPUT_DIR%"
copy /Y "%TARGET_DIR%\%MAIN_JAR%" "%INPUT_DIR%\%MAIN_JAR%" >nul
if errorlevel 1 goto :fail

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --input "%INPUT_DIR%" ^
  --dest "%DIST_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --icon "%ICON_PATH%" ^
  --module-path "%JAVAFX_MODULE_PATH%" ^
  --add-modules %RUNTIME_MODULES% ^
  --java-options "--add-modules=%RUNTIME_MODULES%" ^
  --java-options "--enable-native-access=javafx.graphics"

if errorlevel 1 goto :fail

if defined MSR_AMIS_PACKAGE_API_BASE_URL (
  > "%APP_DIR%\.env" echo MSR_AMIS_DATA_MODE=REMOTE_API
  >> "%APP_DIR%\.env" echo MSR_AMIS_API_BASE_URL=%MSR_AMIS_PACKAGE_API_BASE_URL%
  >> "%APP_DIR%\.env" echo APP_MODE=REMOTE_API
  >> "%APP_DIR%\.env" echo API_BASE_URL=%MSR_AMIS_PACKAGE_API_BASE_URL%
) else (
  echo MSR_AMIS_PACKAGE_API_BASE_URL must be set before packaging the desktop app.
  echo Example: set MSR_AMIS_PACKAGE_API_BASE_URL=https://api.example.com
  goto :fail
)

if not exist "%APP_DIR%\.env" (
  copy /Y "%BUILD_ROOT%desktop-client.env.example" "%APP_DIR%\desktop-client.env.example" >nul
  if errorlevel 1 goto :fail
)

set "HAS_WIX="
where wix.exe >nul 2>nul && set "HAS_WIX=1"
if not defined HAS_WIX (
  where candle.exe >nul 2>nul && where light.exe >nul 2>nul && set "HAS_WIX=1"
)
if not defined HAS_WIX goto :no_wix

jpackage ^
  --type msi ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --dest "%DIST_DIR%" ^
  --app-image "%APP_DIR%" ^
  --icon "%ICON_PATH%" ^
  --vendor "MSR AMIS" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-menu-group "%APP_NAME%" ^
  --win-shortcut ^
  --win-shortcut-prompt ^
  --win-per-user-install ^
  --win-upgrade-uuid "%UPGRADE_UUID%"

if errorlevel 1 goto :fail

jpackage ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --dest "%DIST_DIR%" ^
  --app-image "%APP_DIR%" ^
  --icon "%ICON_PATH%" ^
  --vendor "MSR AMIS" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-menu-group "%APP_NAME%" ^
  --win-shortcut ^
  --win-shortcut-prompt ^
  --win-per-user-install ^
  --win-upgrade-uuid "%UPGRADE_UUID%"

if errorlevel 1 goto :fail

echo.
echo Desktop app image created successfully:
echo %APP_DIR%
echo.
echo Windows MSI installer created successfully:
echo %INSTALLER_MSI%
echo.
echo Windows installer created successfully:
echo %INSTALLER_EXE%
goto :eof

:no_wix
echo.
echo Desktop app image created successfully:
echo %APP_DIR%
echo.
echo Installer was not created because WiX is not installed.
echo Install WiX Toolset and add it to PATH, then rerun this script.
echo WiX v4/v5: wix.exe
echo WiX v3: candle.exe and light.exe
goto :eof

:fail
echo.
echo Desktop app build failed.
exit /b 1
