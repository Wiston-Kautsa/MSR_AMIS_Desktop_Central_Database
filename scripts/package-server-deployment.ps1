param(
    [string]$OutputDirectory = ".\dist\server"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory
} else {
    Join-Path $repoRoot $OutputDirectory
}

Push-Location $repoRoot
try {
    & ".\scripts\build-api.cmd"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

    Copy-Item ".\msr-amis-api\target\msr-amis-api-0.0.1-SNAPSHOT.jar" (Join-Path $outputPath "msr-amis-api.jar") -Force
    Copy-Item ".\server.env.example" (Join-Path $outputPath "server.env.example") -Force
    Copy-Item ".\scripts\run-api-server.ps1" (Join-Path $outputPath "run-api-server.ps1") -Force
    Copy-Item ".\documentation\docs\server-runtime-deployment.md" (Join-Path $outputPath "README-server-runtime.md") -Force

    Write-Host ""
    Write-Host "Server deployment files prepared in:"
    Write-Host $outputPath
    Write-Host ""
    Write-Host "On the server, copy server.env.example to server.env, fill in real values, then run:"
    Write-Host ".\run-api-server.ps1 -EnvFile .\server.env -JarPath .\msr-amis-api.jar"
} finally {
    Pop-Location
}
