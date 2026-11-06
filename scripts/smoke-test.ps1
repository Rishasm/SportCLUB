param([int]$WaitSeconds = 10, [string]$GatewayUrl = 'http://localhost:8080', [string]$EurekaUrl = 'http://localhost:8761')
. "$PSScriptRoot/common.ps1"
$services = @('members','club','workouts','wellness','nutrition')
$deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
$results = @()
do {
    $results = @()
    $lastFailure = ''
    foreach ($service in $services) {
        try {
            $requestId = "smoke-$service-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$GatewayUrl/api/$service/system" -Headers @{ 'X-Request-ID' = $requestId } -TimeoutSec 10
            $body = $response.Content | ConvertFrom-Json
            if ($body.service -ne "$service-service" -or $body.status -ne 'UP') { throw "Unexpected $service response." }
            if ($body.configurationSource -ne 'config-server') { throw 'Central configuration was not loaded.' }
            if ($body.database.engine -ne 'PostgreSQL') { throw 'Expected a PostgreSQL database.' }
            if ($body.database.serviceCode -ne "$service-service" -or $body.database.schemaVersion -lt 1) { throw 'Database migration check failed.' }
            if ($body.brand.name -ne 'SportCLUB' -or $body.brand.accent -ne '#B5FF7A' -or $body.brand.cardRadius -ne '24') { throw 'Unexpected brand configuration.' }
            $requestIdHeaders = @($response.Headers['X-Request-ID'])
            if ($body.requestId -ne $requestId -or $requestIdHeaders.Count -ne 1 -or $requestIdHeaders[0] -ne $requestId) { throw 'Request ID was not propagated as one unambiguous header.' }
            $api = Invoke-RestMethod -Uri "$GatewayUrl/api/$service/v3/api-docs" -TimeoutSec 10
            if (-not $api.paths.PSObject.Properties["/api/$service/system"]) { throw 'API documentation does not contain the system endpoint.' }
            $results += [pscustomobject]@{ Service = $body.service; Status = $body.status; Database = $body.database.engine; Config = $body.configurationSource; RequestId = 'OK'; OpenAPI = 'OK' }
        } catch { $lastFailure = $_.Exception.Message; break }
    }
    if ($results.Count -eq $services.Count) { break }
    Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $deadline)
if ($results.Count -ne $services.Count) { throw "Gateway smoke test failed: $lastFailure" }
$registry = Invoke-RestMethod -Uri "$EurekaUrl/eureka/apps" -Headers @{ Accept = 'application/json' } -TimeoutSec 10
$registered = @($registry.applications.application | ForEach-Object { $_.name })
foreach ($service in (($services | ForEach-Object { "$_-SERVICE".ToUpperInvariant() }) + 'API-GATEWAY')) {
    if ($service -notin $registered) { throw "Missing Eureka registration: $service" }
}
$results | Format-Table -AutoSize
Write-Host 'PASS: five gateway routes, PostgreSQL, Config Server, Eureka, request IDs and OpenAPI.'

