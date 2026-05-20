param(
    [string]$EnvFile = ".\server.env",
    [string]$JarPath = ".\msr-amis-api\target\msr-amis-api-0.0.1-SNAPSHOT.jar",
    [string]$JavaOpts = ""
)

$ErrorActionPreference = "Stop"

function Resolve-FromRoot([string]$PathValue) {
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    return Join-Path (Get-Location) $PathValue
}

$resolvedEnvFile = Resolve-FromRoot $EnvFile
$resolvedJarPath = Resolve-FromRoot $JarPath

if (-not (Test-Path -LiteralPath $resolvedEnvFile)) {
    throw "Environment file not found: $resolvedEnvFile. Copy server.env.example to server.env and fill in real values."
}

if (-not (Test-Path -LiteralPath $resolvedJarPath)) {
    throw "API jar not found: $resolvedJarPath. Run .\scripts\build-api.cmd first."
}

Get-Content -LiteralPath $resolvedEnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) {
        return
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
        return
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1).Trim()

    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$requiredVariables = @(
    "MSR_AMIS_DB_URL",
    "MSR_AMIS_DB_USERNAME",
    "MSR_AMIS_DB_PASSWORD",
    "MSR_AMIS_JWT_SECRET",
    "MSR_AMIS_PRIMARY_SUPER_ADMIN_EMAIL"
)

foreach ($variable in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($variable, "Process")
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like "replace_with_*") {
        throw "Required server setting is missing or still a placeholder: $variable"
    }
}

Write-Host "Starting MSR-AMIS API from $resolvedJarPath"
Write-Host "API port: $env:MSR_AMIS_API_PORT"
Write-Host "Database: $env:MSR_AMIS_DB_URL"

$javaArguments = @()
if (-not [string]::IsNullOrWhiteSpace($JavaOpts)) {
    $javaArguments += $JavaOpts.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
}
$javaArguments += @("-jar", $resolvedJarPath)

& java @javaArguments
exit $LASTEXITCODE
