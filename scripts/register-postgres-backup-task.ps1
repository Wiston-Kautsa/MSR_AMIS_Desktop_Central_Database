param(
    [string]$TaskName = "MSR-AMIS PostgreSQL Backup",
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$StartTime = "22:00",
    [string]$BackupDirectory = "C:\Backups\MSR-AMIS",
    [int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"

$backupScript = Join-Path $ProjectRoot "scripts\postgres-backup.ps1"
if (-not (Test-Path -LiteralPath $backupScript)) {
    throw "Backup script not found: $backupScript"
}

$actionArguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$backupScript`"",
    "-BackupDirectory", "`"$BackupDirectory`"",
    "-RetentionDays", $RetentionDays
) -join " "

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $actionArguments
$trigger = New-ScheduledTaskTrigger -Daily -At $StartTime
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Creates daily PostgreSQL backups for MSR-AMIS." `
    -Force | Out-Null

Write-Output "Scheduled task registered: $TaskName at $StartTime"
Write-Output "Backup script: $backupScript"
Write-Output "Backup directory: $BackupDirectory"
