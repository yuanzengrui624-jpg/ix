param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('update-config', 'init-db', 'upgrade-db', 'prepare-db')]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [string]$AppPropsPath,

    [Parameter(Mandatory = $false)]
    [string]$InitSqlPath,

    [Parameter(Mandatory = $false)]
    [string]$BackupDir
)

$ErrorActionPreference = 'Stop'

$dbUser = $env:NETMGMT_DB_USER
$dbPass = $env:NETMGMT_DB_PASS

if ([string]::IsNullOrWhiteSpace($dbUser)) {
    throw 'MySQL 用户名不能为空。'
}

if ($null -eq $dbPass) {
    $dbPass = ''
}

function New-LoginFile {
    $path = Join-Path $env:TEMP ("netmgmt_mysql_{0}.cnf" -f [guid]::NewGuid().ToString('N'))
    $content = "[client]`nuser=$dbUser`npassword=$dbPass`n"
    [System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::ASCII)
    return $path
}

function Invoke-MySqlQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query
    )

    $args = @(
        "--defaults-extra-file=$script:LoginFile"
        '--default-character-set=utf8mb4'
        '-N'
        '-s'
        '-e'
        $Query
    )

    $output = & mysql @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw (($output | Out-String).Trim())
    }

    return (($output | Out-String).Trim())
}

function Invoke-MySqlSqlText {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SqlText
    )

    $output = $SqlText | & mysql "--defaults-extra-file=$script:LoginFile" '--default-character-set=utf8mb4' 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw (($output | Out-String).Trim())
    }
}

function Invoke-MySqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -Path $Path)) {
        throw "SQL 文件不存在：$Path"
    }

    $sql = Get-Content -Raw -Path $Path
    Invoke-MySqlSqlText -SqlText $sql
}

function Update-AppProperties {
    if (-not (Test-Path -Path $AppPropsPath)) {
        throw "配置文件不存在：$AppPropsPath"
    }

    Invoke-MySqlQuery -Query 'SELECT 1;' | Out-Null

    $backupPath = "$AppPropsPath.bak"
    if (-not (Test-Path -Path $backupPath)) {
        Copy-Item -Path $AppPropsPath -Destination $backupPath -Force
    }

    $updatedLines = foreach ($line in (Get-Content -Path $AppPropsPath)) {
        if ($line -like 'db.username=*') {
            "db.username=$dbUser"
        }
        elseif ($line -like 'db.password=*') {
            "db.password=$dbPass"
        }
        else {
            $line
        }
    }

    [System.IO.File]::WriteAllLines($AppPropsPath, $updatedLines, [System.Text.Encoding]::ASCII)
}

function Ensure-ColumnExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TableName,

        [Parameter(Mandatory = $true)]
        [string]$ColumnName,

        [Parameter(Mandatory = $true)]
        [string]$Sql
    )

    $exists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='$TableName' AND COLUMN_NAME='$ColumnName';"
    if ([int]$exists -eq 0) {
        Invoke-MySqlQuery -Query $Sql | Out-Null
        return $true
    }

    return $false
}

function Test-DatabaseExists {
    $exists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='net_manage';"
    return [int]$exists -gt 0
}

function Test-TableExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TableName
    )

    $exists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='$TableName';"
    return [int]$exists -gt 0
}

function Get-DatabaseState {
    if (-not (Test-DatabaseExists)) {
        return 'MISSING_DB'
    }

    foreach ($table in @('device', 'monitor_log', 'alarm', 'config_backup')) {
        if (-not (Test-TableExists -TableName $table)) {
            return 'INCOMPLETE_SCHEMA'
        }
    }

    $filePathNullable = Invoke-MySqlQuery -Query "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='config_backup' AND COLUMN_NAME='file_path';"
    if ($filePathNullable -ne 'YES') {
        return 'NEEDS_UPGRADE'
    }

    $contentExists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='config_backup' AND COLUMN_NAME='content';"
    if ([int]$contentExists -eq 0) {
        return 'NEEDS_UPGRADE'
    }

    $contentNullable = Invoke-MySqlQuery -Query "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='config_backup' AND COLUMN_NAME='content';"
    if ($contentNullable -ne 'NO') {
        return 'NEEDS_UPGRADE'
    }

    $nullContentCount = Invoke-MySqlQuery -Query 'SELECT COUNT(*) FROM net_manage.config_backup WHERE content IS NULL;'
    if ([int]$nullContentCount -gt 0) {
        return 'NEEDS_UPGRADE'
    }

    $recoveredExists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='alarm' AND COLUMN_NAME='recovered';"
    if ([int]$recoveredExists -eq 0) {
        return 'NEEDS_UPGRADE'
    }

    $recoverTimeExists = Invoke-MySqlQuery -Query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='alarm' AND COLUMN_NAME='recover_time';"
    if ([int]$recoverTimeExists -eq 0) {
        return 'NEEDS_UPGRADE'
    }

    return 'READY'
}

function Backup-Database {
    if ([string]::IsNullOrWhiteSpace($BackupDir)) {
        throw '未提供数据库备份目录。'
    }

    if (-not (Get-Command mysqldump -ErrorAction SilentlyContinue)) {
        throw '检测到旧库需要备份，但未找到 mysqldump 命令。'
    }

    if (-not (Test-Path -Path $BackupDir)) {
        New-Item -Path $BackupDir -ItemType Directory -Force | Out-Null
    }

    $timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $backupPath = Join-Path $BackupDir "net_manage_backup_$timestamp.sql"
    $args = @(
        "--defaults-extra-file=$script:LoginFile"
        '--default-character-set=utf8mb4'
        '--single-transaction'
        '--skip-lock-tables'
        "--result-file=$backupPath"
        'net_manage'
    )

    $output = & mysqldump @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        if (Test-Path -Path $backupPath) {
            Remove-Item -Path $backupPath -Force -ErrorAction SilentlyContinue
        }
        throw "自动备份旧库失败：$((($output | Out-String).Trim()))"
    }

    Write-Host "已自动备份旧库：$backupPath"
}

function Upgrade-Database {
    Invoke-MySqlFile -Path $InitSqlPath

    $filePathNullable = Invoke-MySqlQuery -Query "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='config_backup' AND COLUMN_NAME='file_path';"
    if ($filePathNullable -eq 'NO') {
        Invoke-MySqlQuery -Query 'ALTER TABLE net_manage.config_backup MODIFY COLUMN file_path VARCHAR(512) NULL;' | Out-Null
    }

    Ensure-ColumnExists -TableName 'config_backup' -ColumnName 'content' -Sql 'ALTER TABLE net_manage.config_backup ADD COLUMN content TEXT NULL;' | Out-Null

    $nullContentCount = Invoke-MySqlQuery -Query 'SELECT COUNT(*) FROM net_manage.config_backup WHERE content IS NULL;'
    if ([int]$nullContentCount -gt 0) {
        Invoke-MySqlQuery -Query "UPDATE net_manage.config_backup SET content = '' WHERE content IS NULL;" | Out-Null
    }

    $contentNullable = Invoke-MySqlQuery -Query "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='net_manage' AND TABLE_NAME='config_backup' AND COLUMN_NAME='content';"
    if ($contentNullable -eq 'YES') {
        Invoke-MySqlQuery -Query 'ALTER TABLE net_manage.config_backup MODIFY COLUMN content TEXT NOT NULL;' | Out-Null
    }

    Ensure-ColumnExists -TableName 'alarm' -ColumnName 'recovered' -Sql 'ALTER TABLE net_manage.alarm ADD COLUMN recovered TINYINT NOT NULL DEFAULT 0;' | Out-Null
    Ensure-ColumnExists -TableName 'alarm' -ColumnName 'recover_time' -Sql 'ALTER TABLE net_manage.alarm ADD COLUMN recover_time DATETIME NULL;' | Out-Null
}

function Prepare-Database {
    $state = Get-DatabaseState

    switch ($state) {
        'MISSING_DB' {
            Write-Host '未检测到 net_manage 库，正在自动初始化...'
            Invoke-MySqlFile -Path $InitSqlPath
            Write-Host '数据库初始化完成'
        }
        'INCOMPLETE_SCHEMA' {
            Backup-Database
            Write-Host '检测到数据库结构不完整，正在自动补齐并升级...'
            Upgrade-Database
            Write-Host '数据库修复完成'
        }
        'NEEDS_UPGRADE' {
            Backup-Database
            Write-Host '检测到旧库，正在自动升级...'
            Upgrade-Database
            Write-Host '旧数据库升级完成'
        }
        'READY' {
            Write-Host '检测到数据库已经可用，直接继续启动...'
        }
        default {
            throw "无法识别数据库状态：$state"
        }
    }
}

try {
    $script:LoginFile = New-LoginFile

    switch ($Action) {
        'update-config' {
            Update-AppProperties
            Write-Host '数据库配置已写入 application.properties'
        }
        'init-db' {
            Invoke-MySqlFile -Path $InitSqlPath
            Write-Host '数据库初始化完成'
        }
        'upgrade-db' {
            Upgrade-Database
            Write-Host '旧数据库升级完成'
        }
        'prepare-db' {
            Prepare-Database
        }
    }
}
finally {
    if ($script:LoginFile -and (Test-Path -Path $script:LoginFile)) {
        Remove-Item -Path $script:LoginFile -Force -ErrorAction SilentlyContinue
    }
}
