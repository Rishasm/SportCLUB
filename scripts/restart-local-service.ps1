param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('members-service', 'club-service', 'workouts-service', 'wellness-service', 'nutrition-service')]
    [string]$Name
)
. "$PSScriptRoot/common.ps1"

$manifestPath = Join-Path $script:RuntimeRoot 'processes.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'No existing local SportCLUB manifest. Run scripts/start-local.ps1 first.'
}
$parsedEntries = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($parsedEntries)
$targetEntries = @($entries | Where-Object { $_.name -eq $Name })
if ($targetEntries.Count -ne 1) { throw "Expected exactly one manifest entry for $Name." }
$target = $targetEntries[0]
$expectedJar = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot "$Name/target/$Name-0.1.0-SNAPSHOT.jar"))
if (-not [string]::Equals($expectedJar, [string]$target.jarPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unexpected jar path for $Name. Refusing to restart it."
}
if (-not (Test-Path -LiteralPath $expectedJar -PathType Leaf)) {
    throw "Missing $expectedJar. Build the project before starting the local stand."
}
$servicePorts = @{ 'members-service' = 8081; 'club-service' = 8082; 'workouts-service' = 8083; 'wellness-service' = 8084; 'nutrition-service' = 8085 }
$servicePort = $servicePorts[$Name]
if ([int]$target.port -ne $servicePort -or [int]$target.processId -le 0) {
    throw "Unexpected port or process ID for $Name in the manifest."
}

# Check prerequisites before stopping a working service.
foreach ($port in @(8888, 8761)) {
    try {
        $healthBody = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 5
        if ($healthBody.status -ne 'UP') { throw 'Health is not UP.' }
    } catch { throw "Config Server and Eureka must be healthy before restarting a service; port ${port}: $($_.Exception.Message)" }
}
$java = Get-SportClubJava
$managedId = [int]$target.processId
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $managedId" -ErrorAction Stop
if ($processInfo) {
    $jarArgument = [regex]::Match([string]$processInfo.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))')
    $actualJar = if ($jarArgument.Groups['quoted'].Success) { $jarArgument.Groups['quoted'].Value } else { $jarArgument.Groups['bare'].Value }
    if (-not (Test-SportClubProcess -ProcessId $managedId -JarPath $expectedJar) -or
        -not $jarArgument.Success -or
        -not [string]::Equals($actualJar, $expectedJar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest PID $managedId does not run the exact managed jar for $Name. Refusing to stop it."
    }
}
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $servicePort -ErrorAction SilentlyContinue)
if (@($listeners | Where-Object { -not $processInfo -or $_.OwningProcess -ne $managedId }).Count -gt 0) {
    throw "Port $servicePort belongs to another process. No process was stopped."
}

$logDir = Join-Path $script:RuntimeRoot 'logs'
$database = Get-SportClubPostgres
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$managedEnvironment = @('SPRING_PROFILES_ACTIVE', 'SERVER_ADDRESS', 'SERVER_PORT', 'CONFIG_SERVER_URL', 'EUREKA_URL', 'SERVICE_HOSTNAME', 'CONFIG_REPO_LOCATION', 'LOG_DIR', 'JWT_SECRET') + $script:PostgresVariables
$previousEnvironment = @{}
foreach ($key in $managedEnvironment) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    if ($processInfo) {
        Stop-Process -Id $managedId
        Wait-Process -Id $managedId -Timeout 15 -ErrorAction SilentlyContinue
        $remainingProcess = Get-Process -Id $managedId -ErrorAction SilentlyContinue
        if ($remainingProcess -and -not $remainingProcess.HasExited) { throw "Managed process $managedId did not stop." }
        Write-Host "Stopped $Name."
    }
    if (@(Get-NetTCPConnection -State Listen -LocalPort $servicePort -ErrorAction SilentlyContinue).Count -gt 0) {
        throw "Port $servicePort is still occupied. No other process will be stopped."
    }
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = [string]$servicePort
    $env:CONFIG_SERVER_URL = 'http://localhost:8888'
    $env:EUREKA_URL = 'http://localhost:8761/eureka/'
    $env:SERVICE_HOSTNAME = 'localhost'
    $configPath = (Join-Path $script:ProjectRoot 'config-repo') + [IO.Path]::DirectorySeparatorChar
    $env:CONFIG_REPO_LOCATION = ([uri]$configPath).AbsoluteUri
    $env:LOG_DIR = $logDir.Replace('\', '/')
    Set-SportClubBusinessEnvironment $database
    $arguments = @('-Xms32m', '-Xmx192m', '-XX:MaxMetaspaceSize=192m', '-XX:ReservedCodeCacheSize=64m', '-XX:+UseSerialGC', '-Dfile.encoding=UTF-8', '-jar', "`"$expectedJar`"")
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $script:ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir "$Name.stdout.log") -RedirectStandardError (Join-Path $logDir "$Name.stderr.log")
    $target.processId = $process.Id
    ConvertTo-Json -InputObject @($entries) -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Write-Host "Started $Name (PID $($process.Id), port $servicePort)."
    Wait-SportClubHttp -Url "http://localhost:$servicePort/actuator/health" -TimeoutSeconds 120
    Write-Host "$Name is ready."
} finally {
    foreach ($key in $managedEnvironment) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
}
