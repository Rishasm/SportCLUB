Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:RuntimeRoot = Join-Path $script:ProjectRoot '.runtime'
$script:PostgresVariables = @('SPORTCLUB_DB_PORT', 'SPORTCLUB_DB_USER', 'SPORTCLUB_DB_PASSWORD')

function Get-SportClubPostgres {
    $file = Join-Path $script:RuntimeRoot 'postgres.clixml'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw 'PostgreSQL is not configured. Run scripts/setup-postgres.ps1 first.'
    }
    $database = Import-Clixml -LiteralPath $file
    if ($database.Password -isnot [Security.SecureString]) {
        throw 'Invalid local PostgreSQL settings. Run scripts/setup-postgres.ps1 again.'
    }
    return $database
}

function Set-SportClubPostgresEnvironment($Database) {
    $env:SPORTCLUB_DB_PORT = [string]$Database.Port
    $env:SPORTCLUB_DB_USER = [string]$Database.User
    $env:SPORTCLUB_DB_PASSWORD = [Net.NetworkCredential]::new('', $Database.Password).Password
}

function Set-SportClubBusinessEnvironment($Database) {
    Set-SportClubPostgresEnvironment $Database
    if ($env:JWT_SECRET) { return }
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes("SportCLUB local JWT v1:$env:SPORTCLUB_DB_PASSWORD")
        $env:JWT_SECRET = [Convert]::ToBase64String($hash.ComputeHash($bytes))
    } finally {
        $hash.Dispose()
    }
}

function Get-SportClubJava {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        return (Join-Path $env:JAVA_HOME 'bin/java.exe')
    }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($base in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium")) {
        if (Test-Path -LiteralPath $base) {
            foreach ($jdk in (Get-ChildItem -LiteralPath $base -Directory | Sort-Object Name -Descending)) {
                $candidate = Join-Path $jdk.FullName 'bin/java.exe'
                if (Test-Path -LiteralPath $candidate) { return $candidate }
            }
        }
    }
    throw 'Java 17+ was not found. Install a JDK or set JAVA_HOME.'
}

function Wait-SportClubHttp {
    param([string]$Url, [int]$TimeoutSeconds = 120)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Url. Inspect .runtime/logs."
}

function Test-SportClubProcess {
    param([int]$ProcessId, [string]$JarPath)
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if (-not $processInfo) { return $false }
    return $processInfo.Name -eq 'java.exe' -and $processInfo.CommandLine -and $processInfo.CommandLine.Contains($JarPath)
}

