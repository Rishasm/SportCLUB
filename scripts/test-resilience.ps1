param([int]$RecoverySeconds = 90)
. "$PSScriptRoot/common.ps1"
if ($RecoverySeconds -lt 10 -or $RecoverySeconds -gt 300) { throw 'RecoverySeconds must be between 10 and 300.' }
Add-Type -AssemblyName System.Net.Http
$resilienceClient = [System.Net.Http.HttpClient]::new()
$resilienceClient.Timeout = [TimeSpan]::FromSeconds(12)
$resilienceToken = ''
$resilienceWaterId = $null
$failureInjected = $false
$manifestPath = Join-Path $script:RuntimeRoot 'processes.json'
$resultPath = Join-Path $script:RuntimeRoot 'resilience-result.json'
@{ status = 'RUNNING'; startedAtUtc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8

function Invoke-ResilienceApi {
    param([string]$Path, [string]$Method = 'GET', $Body = $null, [string]$Token = '')
    $requestId = "resilience-$([Guid]::NewGuid().ToString('N'))"
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "http://localhost:8080$Path")
    $request.Headers.Add('X-Request-ID', $requestId)
    if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    if ($null -ne $Body) {
        $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 8 -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    $response = $null
    try {
        $response = $resilienceClient.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = if ($content.Trim()) { $content | ConvertFrom-Json } else { $null }
        if ($Path.StartsWith('/api/')) {
            if (-not $response.Headers.Contains('X-Request-ID') -or (@($response.Headers.GetValues('X-Request-ID')) -join ',') -ne $requestId) {
                throw "Request ID did not survive Gateway for $Path."
            }
            if ($null -ne $data -and $data.PSObject.Properties['requestId'] -and $data.requestId -ne $requestId) {
                throw "Response body contains another request ID for $Path."
            }
        }
        return [pscustomobject]@{ status = [int]$response.StatusCode; data = $data }
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}

function Get-ResilienceMetric {
    param([string]$Metric, [string]$Service = 'wellness', [string]$State = '')
    $path = "/actuator/metrics/${Metric}?tag=name:$Service"
    if ($State) { $path += "&tag=state:$State" }
    $response = Invoke-ResilienceApi $path
    if ($response.status -ne 200 -or @($response.data.measurements).Count -ne 1) { throw "Cannot read Circuit Breaker metric $Metric ($Service/$State)." }
    return [double]$response.data.measurements[0].value
}

function Assert-ResilienceStatus {
    param($Response, [int]$Expected, [string]$Description)
    if ($Response.status -ne $Expected) { throw "$Description returned $($Response.status), expected $Expected." }
}

try {
    $independentRoutes = @('members', 'club', 'workouts', 'nutrition')
    foreach ($port in @(8888, 8761)) {
        $prerequisite = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 5
        if ($prerequisite.status -ne 'UP') { throw "Config Server and Eureka must be healthy before failure injection; port $port is not UP." }
    }
    foreach ($name in ($independentRoutes + 'wellness')) {
        Assert-ResilienceStatus (Invoke-ResilienceApi "/api/$name/system") 200 "Initial $name route"
    }
    # Two successful probes also allow an existing HALF_OPEN circuit to close.
    Assert-ResilienceStatus (Invoke-ResilienceApi '/api/wellness/system') 200 'Initial wellness probe'
    if ((Get-ResilienceMetric 'resilience4j.circuitbreaker.state' 'wellness' 'closed') -ne 1) { throw 'Wellness Circuit Breaker must start CLOSED.' }
    $notPermittedBefore = Get-ResilienceMetric 'resilience4j.circuitbreaker.not.permitted.calls'

    $entries = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $targets = @($entries | Where-Object name -EQ 'wellness-service')
    if ($targets.Count -ne 1) { throw 'Expected one managed wellness-service in the local manifest.' }
    $target = $targets[0]
    $expectedJar = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot 'wellness-service/target/wellness-service-0.1.0-SNAPSHOT.jar'))
    if (-not [string]::Equals($expectedJar, [string]$target.jarPath, [StringComparison]::OrdinalIgnoreCase) -or [int]$target.port -ne 8084) {
        throw 'Unexpected managed wellness path or port; no process will be stopped.'
    }
    $managedId = [int]$target.processId
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $managedId" -ErrorAction Stop
    if (-not $processInfo -or -not (Test-SportClubProcess -ProcessId $managedId -JarPath $expectedJar)) { throw 'The managed wellness Java process is not running.' }
    $jarArgument = [regex]::Match([string]$processInfo.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))')
    $actualJar = if ($jarArgument.Groups['quoted'].Success) { $jarArgument.Groups['quoted'].Value } else { $jarArgument.Groups['bare'].Value }
    if (-not $jarArgument.Success -or -not [string]::Equals($actualJar, $expectedJar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The wellness PID does not use the exact managed jar; refusing to stop it.'
    }
    $originalIds = @{}
    foreach ($entry in $entries) { $originalIds[$entry.name] = [int]$entry.processId }

    $email = "qa-resilience-$([Guid]::NewGuid().ToString('N'))@sportclub.local"
    $auth = Invoke-ResilienceApi '/api/members/auth/register' 'POST' @{ name = 'Resilience QA'; email = $email; password = 'ResilienceQa123!' }
    Assert-ResilienceStatus $auth 201 'QA registration'
    $resilienceToken = $auth.data.token
    $day = Invoke-ResilienceApi '/api/wellness/day' 'GET' $null $resilienceToken
    Assert-ResilienceStatus $day 200 'Initial personal day'
    $testDate = [string]$day.data.today
    $water = Invoke-ResilienceApi '/api/wellness/water' 'POST' @{ date = $testDate; amountMl = 250 } $resilienceToken
    Assert-ResilienceStatus $water 201 'QA water entry'
    $resilienceWaterId = $water.data.waterEntries[0].id

    try {
        $failureInjected = $true
        Stop-Process -Id $managedId
        Wait-Process -Id $managedId -Timeout 15 -ErrorAction SilentlyContinue
        $remainingProcess = Get-Process -Id $managedId -ErrorAction SilentlyContinue
        if ($remainingProcess -and -not $remainingProcess.HasExited) { throw 'The managed wellness service did not stop.' }
        Write-Host 'Stopped only wellness-service; testing its Gateway route.'
        for ($index = 0; $index -lt 8; $index++) {
            $unavailable = Invoke-ResilienceApi '/api/wellness/system'
            Assert-ResilienceStatus $unavailable 503 'Unavailable wellness route'
            if ($unavailable.data.code -ne 'SERVICE_UNAVAILABLE' -or $unavailable.data.service -ne 'wellness') { throw 'Expected the wellness Gateway fallback body.' }
        }
        if ((Get-ResilienceMetric 'resilience4j.circuitbreaker.state' 'wellness' 'open') -ne 1) { throw 'The wellness Circuit Breaker did not enter OPEN.' }
        $notPermittedAfter = Get-ResilienceMetric 'resilience4j.circuitbreaker.not.permitted.calls'
        if ($notPermittedAfter -le $notPermittedBefore) { throw 'No requests were rejected by the OPEN Circuit Breaker.' }
        Write-Host "PASS: wellness returns 503 + SERVICE_UNAVAILABLE, OPEN=1; rejected calls increased $notPermittedBefore -> $notPermittedAfter."
        foreach ($name in $independentRoutes) {
            Assert-ResilienceStatus (Invoke-ResilienceApi "/api/$name/system") 200 "Independent $name route"
            if ((Get-ResilienceMetric 'resilience4j.circuitbreaker.state' $name 'closed') -ne 1) { throw "Another Circuit Breaker changed state: $name." }
        }
        Write-Host 'PASS: members, club, workouts and nutrition continue returning 200 with their circuits CLOSED.'
    } catch {
        Write-Warning "Failure check before restoration: $($_.Exception.Message)"
        throw
    } finally {
        if ($failureInjected) {
            & "$PSScriptRoot/restart-local-service.ps1" -Name wellness-service
            $failureInjected = $false
        }
    }

    $recoveryStarted = [DateTime]::UtcNow
    $deadline = $recoveryStarted.AddSeconds($RecoverySeconds)
    $recovered = $false
    do {
        $probe = Invoke-ResilienceApi '/api/wellness/system'
        if ($probe.status -eq 200 -and (Get-ResilienceMetric 'resilience4j.circuitbreaker.state' 'wellness' 'closed') -eq 1) { $recovered = $true; break }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $recovered) { throw 'Wellness did not recover to HTTP200 and CLOSED through Gateway.' }
    $restored = Invoke-ResilienceApi "/api/wellness/day?date=$testDate" 'GET' $null $resilienceToken
    Assert-ResilienceStatus $restored 200 'Personal day after recovery'
    if ($restored.data.waterTotalMl -ne 250 -or @($restored.data.waterEntries).Count -ne 1 -or $restored.data.waterEntries[0].id -ne $resilienceWaterId) {
        throw 'The saved personal water entry changed after recovery.'
    }
    $updatedEntries = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $updatedEntries) {
        if ($entry.name -ne 'wellness-service' -and [int]$entry.processId -ne $originalIds[$entry.name]) { throw "Another service was restarted: $($entry.name)." }
    }
    @{
        status = 'PASS'; testedAtUtc = [DateTime]::UtcNow.ToString('o'); fallbackStatus = 503
        openStateObserved = $true; rejectedCallsBefore = $notPermittedBefore; rejectedCallsAfter = $notPermittedAfter
        independentRoutes = $independentRoutes; recoveryStatus = 200; recoveredState = 'CLOSED'
        savedWaterEntryPreserved = $true; otherProcessIdsUnchanged = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Host 'PASS: wellness recovered to 200/CLOSED; the same JWT reads the same saved entry; other service PIDs are unchanged.'
} catch {
    @{ status = 'FAIL'; testedAtUtc = [DateTime]::UtcNow.ToString('o'); message = $_.Exception.Message } |
        ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8
    throw
} finally {
    if ($resilienceToken -and $resilienceWaterId) {
        try {
            $removed = Invoke-ResilienceApi "/api/wellness/water/$resilienceWaterId" 'DELETE' $null $resilienceToken
            if ($removed.status -ne 204) { throw 'QA cleanup was not confirmed.' }
        } catch { Write-Warning 'Could not remove the temporary QA water entry; inspect it when the service is available.' }
    }
    $resilienceClient.Dispose()
}
