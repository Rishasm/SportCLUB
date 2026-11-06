param([switch]$SkipTests)
. "$PSScriptRoot/common.ps1"
$manifestPath = Join-Path $script:RuntimeRoot 'processes.json'
if (Test-Path -LiteralPath $manifestPath) {
    foreach ($entry in (Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        if (Test-SportClubProcess -ProcessId $entry.processId -JarPath $entry.jarPath) {
            throw 'Stop SportCLUB with scripts/stop-local.ps1 before building; Windows locks running jar files.'
        }
    }
}
$mavenArguments = @('clean', 'package')
if ($SkipTests) {
    & "$PSScriptRoot/maven.ps1" -MavenArgs ($mavenArguments + '-DskipTests')
    return
}
$database = Get-SportClubPostgres
$previousEnvironment = @{}
foreach ($key in $script:PostgresVariables) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    Set-SportClubPostgresEnvironment $database
    & "$PSScriptRoot/maven.ps1" -MavenArgs $mavenArguments
} finally {
    foreach ($key in $script:PostgresVariables) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
}

