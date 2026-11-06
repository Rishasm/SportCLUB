param([switch]$Build, [switch]$BuildIfMissing)
. "$PSScriptRoot/common.ps1"
$java = Get-SportClubJava
$manifestPath = Join-Path $script:RuntimeRoot 'processes.json'
$logDir = Join-Path $script:RuntimeRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$database = Get-SportClubPostgres
$applications = @(
    @{ Name = 'config-server'; Port = 8888; Profile = 'native' },
    @{ Name = 'discovery-server'; Port = 8761; Profile = 'local' },
    @{ Name = 'members-service'; Port = 8081; Profile = 'local' },
    @{ Name = 'club-service'; Port = 8082; Profile = 'local' },
    @{ Name = 'workouts-service'; Port = 8083; Profile = 'local' },
    @{ Name = 'wellness-service'; Port = 8084; Profile = 'local' },
    @{ Name = 'nutrition-service'; Port = 8085; Profile = 'local' },
    @{ Name = 'api-gateway'; Port = 8080; Profile = 'local' }
)
if (Test-Path -LiteralPath $manifestPath) {
    foreach ($entry in (Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        if (Test-SportClubProcess -ProcessId $entry.processId -JarPath $entry.jarPath) {
            throw 'SportCLUB is already running. Use scripts/stop-local.ps1 first.'
        }
    }
}
foreach ($app in $applications) {
    $listener = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $listener.ConnectAsync('127.0.0.1', $app.Port)
        if ($task.Wait(300) -and $listener.Connected) { throw "Port $($app.Port) is already in use." }
    } catch [System.AggregateException] { } finally { $listener.Dispose() }
}
$missingJars = @(foreach ($app in $applications) {
    $jar = Join-Path $script:ProjectRoot "$($app.Name)/target/$($app.Name)-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { $jar }
})
if ($Build -or ($BuildIfMissing -and $missingJars.Count -gt 0)) {
    & "$PSScriptRoot/build.ps1"
} elseif ($missingJars.Count -gt 0) {
    throw 'Some SportCLUB JAR files are missing. Run scripts/start-local.ps1 -Build.'
}
$managedEnvironment = @('SPRING_PROFILES_ACTIVE','SERVER_ADDRESS','SERVER_PORT','CONFIG_SERVER_URL','EUREKA_URL','SERVICE_HOSTNAME','CONFIG_REPO_LOCATION','LOG_DIR','JWT_SECRET') + $script:PostgresVariables
$previousEnvironment = @{}
foreach ($key in $managedEnvironment) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$processes = [System.Collections.Generic.List[object]]::new()
try {
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:CONFIG_SERVER_URL = 'http://localhost:8888'
    $env:EUREKA_URL = 'http://localhost:8761/eureka/'
    $env:SERVICE_HOSTNAME = 'localhost'
    $configPath = (Join-Path $script:ProjectRoot 'config-repo') + [IO.Path]::DirectorySeparatorChar
    $env:CONFIG_REPO_LOCATION = ([uri]$configPath).AbsoluteUri
    $env:LOG_DIR = $logDir.Replace('\','/')
    foreach ($app in $applications) {
        if ($app.Name -in @('members-service','club-service','workouts-service','wellness-service','nutrition-service')) {
            [Environment]::SetEnvironmentVariable('JWT_SECRET', $previousEnvironment['JWT_SECRET'], 'Process')
            Set-SportClubBusinessEnvironment $database
        } else {
            foreach ($key in $script:PostgresVariables) { [Environment]::SetEnvironmentVariable($key, $null, 'Process') }
            Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
        }
        $env:SPRING_PROFILES_ACTIVE = $app.Profile
        $env:SERVER_PORT = [string]$app.Port
        $jar = Join-Path $script:ProjectRoot "$($app.Name)/target/$($app.Name)-0.1.0-SNAPSHOT.jar"
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing $jar after build." }
        $arguments = @('-Xms32m', '-Xmx192m', '-XX:MaxMetaspaceSize=192m', '-XX:ReservedCodeCacheSize=64m', '-XX:+UseSerialGC', '-Dfile.encoding=UTF-8', '-jar', "`"$jar`"")
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $script:ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir "$($app.Name).stdout.log") -RedirectStandardError (Join-Path $logDir "$($app.Name).stderr.log")
        $processes.Add(@{ name = $app.Name; processId = $process.Id; jarPath = $jar; port = $app.Port })
        ConvertTo-Json -InputObject @($processes.ToArray()) -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
        Write-Host "Started $($app.Name) (PID $($process.Id), port $($app.Port))."
        # Start sequentially to avoid competing JPA initializations on a local machine.
        $healthPath = if ($app.Name -eq 'config-server') { 'health' } else { 'health/readiness' }
        Wait-SportClubHttp -Url "http://localhost:$($app.Port)/actuator/$healthPath" -TimeoutSeconds 180
        Write-Host "$($app.Name) is ready."
    }
    & "$PSScriptRoot/smoke-test.ps1" -WaitSeconds 90
    Write-Host 'SportCLUB is ready: http://localhost:8080/ (diagnostics: /diagnostics.html)'
} catch {
    Write-Warning 'Startup did not finish. Started processes are recorded in .runtime/processes.json; use stop-local.ps1 to stop them.'
    throw
} finally {
    foreach ($key in $managedEnvironment) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
}

