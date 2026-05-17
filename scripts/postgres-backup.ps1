param(
    [string]$DatabaseName = $(if ($env:MSR_AMIS_DB_NAME) { $env:MSR_AMIS_DB_NAME } else { "msr_amis" }),
    [string]$DatabaseHost = $(if ($env:MSR_AMIS_DB_HOST) { $env:MSR_AMIS_DB_HOST } else { "localhost" }),
    [int]$DatabasePort = $(if ($env:MSR_AMIS_DB_PORT) { [int]$env:MSR_AMIS_DB_PORT } else { 5432 }),
    [string]$DatabaseUser = $(if ($env:MSR_AMIS_DB_USERNAME) { $env:MSR_AMIS_DB_USERNAME } else { "msr_amis_user" }),
    [string]$BackupDirectory = $(if ($env:MSR_AMIS_BACKUP_DIR) { $env:MSR_AMIS_BACKUP_DIR } else { "C:\Backups\MSR-AMIS" }),
    [int]$RetentionDays = $(if ($env:MSR_AMIS_BACKUP_RETENTION_DAYS) { [int]$env:MSR_AMIS_BACKUP_RETENTION_DAYS } else { 30 }),
    [string]$PgDumpPath = $(if ($env:PG_DUMP_PATH) { $env:PG_DUMP_PATH } else { "pg_dump" })
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $BackupDirectory)) {
    New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = Join-Path $BackupDirectory "$DatabaseName-$timestamp.dump"
$logFile = Join-Path $BackupDirectory "backup.log"

function Write-BackupLog {
    param([string]$Message)
    $line = "$(Get-Date -Format "yyyy-MM-dd HH:mm:ss") $Message"
    Add-Content -LiteralPath $logFile -Value $line
}

try {
    Write-BackupLog "Starting backup for database '$DatabaseName' on '$DatabaseHost:$DatabasePort'."

    & $PgDumpPath `
        --host $DatabaseHost `
        --port $DatabasePort `
        --username $DatabaseUser `
        --format custom `
        --blobs `
        --file $backupFile `
        $DatabaseName

    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with exit code $LASTEXITCODE."
    }

    $backupInfo = Get-Item -LiteralPath $backupFile
    Write-BackupLog "Backup created: $backupFile ($($backupInfo.Length) bytes)."

    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -LiteralPath $BackupDirectory -Filter "$DatabaseName-*.dump" |
        Where-Object { $_.LastWriteTime -lt $cutoff } |
        ForEach-Object {
            Write-BackupLog "Deleting expired backup: $($_.FullName)."
            Remove-Item -LiteralPath $_.FullName -Force
        }

    Write-BackupLog "Backup completed successfully."
    Write-Output $backupFile
} catch {
    Write-BackupLog "Backup failed: $($_.Exception.Message)"
    throw
}
