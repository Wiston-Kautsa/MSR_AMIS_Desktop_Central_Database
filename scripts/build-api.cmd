@echo off
setlocal

set "BUILD_ROOT=%~dp0..\"
set "API_JAR=%BUILD_ROOT%msr-amis-api\target\msr-amis-api-0.0.1-SNAPSHOT.jar"

call "%BUILD_ROOT%mvnw.cmd" -q -f "%BUILD_ROOT%msr-amis-api\pom.xml" -DskipTests clean package
if errorlevel 1 goto :fail

echo.
echo API jar created successfully:
echo %API_JAR%
goto :eof

:fail
echo.
echo API build failed.
exit /b 1
