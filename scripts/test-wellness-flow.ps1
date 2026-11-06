param([string]$GatewayUrl = 'http://localhost:8080')
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.Net.Http
$wellnessClient = [System.Net.Http.HttpClient]::new()
$wellnessClient.Timeout = [TimeSpan]::FromSeconds(15)
$wellnessToken = ''
$wellnessDates = @()

function Invoke-WellnessApi {
    param([string]$Method, [string]$Path, [int[]]$Expected, $Body = $null, [string]$Token = '')
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$GatewayUrl$Path")
    $requestId = "wellness-qa-$([Guid]::NewGuid().ToString('N'))"
    $request.Headers.Add('X-Request-ID', $requestId)
    $response = $null
    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    }
    if ($null -ne $Body) {
        $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 12 -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $wellnessClient.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = if ($text.Trim()) { $text | ConvertFrom-Json } else { $null }
        $status = [int]$response.StatusCode
        if ($status -notin $Expected) {
            $code = if ($null -ne $data -and $data.PSObject.Properties['code']) { $data.code } else { 'UNKNOWN' }
            throw "$Method $Path returned $status, expected $($Expected -join '/') (code=$code)."
        }
        if (-not $response.Headers.Contains('X-Request-ID') -or (@($response.Headers.GetValues('X-Request-ID')) -join ',') -ne $requestId) {
            throw "$Method $Path did not preserve a single request ID through Gateway."
        }
        return [pscustomobject]@{ data = $data; status = $status }
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}

try {
    $wellnessEmail = "qa-wellness-$([Guid]::NewGuid().ToString('N'))@sportclub.local"
    $wellnessPassword = 'WellnessQaPass123!'
    $auth = (Invoke-WellnessApi POST '/api/members/auth/register' 201 @{ name = 'Wellness QA'; email = $wellnessEmail; password = $wellnessPassword }).data
    $login = (Invoke-WellnessApi POST '/api/members/auth/login' 200 @{ email = $wellnessEmail; password = $wellnessPassword }).data
    $wellnessToken = $login.token
    if (-not $wellnessToken -or $login.user.id -ne $auth.user.id) { throw 'Registration/login did not produce the expected identity.' }
    $other = (Invoke-WellnessApi POST '/api/members/auth/register' 201 @{ name = 'Other Wellness QA'; email = "other-$wellnessEmail"; password = $wellnessPassword }).data

    Invoke-WellnessApi GET '/api/wellness/day' 401 | Out-Null
    Invoke-WellnessApi POST '/api/wellness/water' 401 @{ date = '2026-01-01'; amountMl = 250 } | Out-Null
    $initial = (Invoke-WellnessApi GET '/api/wellness/day' 200 $null $wellnessToken).data
    $today = [string]$initial.today
    $todayValue = [DateTime]::ParseExact($today, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
    $yesterday = $todayValue.AddDays(-1).ToString('yyyy-MM-dd')
    $tomorrow = $todayValue.AddDays(1).ToString('yyyy-MM-dd')
    $wellnessDates = @($today, $yesterday)
    if ($initial.date -ne $today -or $initial.waterGoalMl -ne 2000 -or $initial.waterTotalMl -ne 0 -or @($initial.waterEntries).Count -ne 0 -or $null -ne $initial.sleepMinutes -or $null -ne $initial.bmi) {
        throw 'New account must have an empty day and default personal water goal.'
    }
    $settings = (Invoke-WellnessApi PUT '/api/wellness/settings' 200 @{ waterGoalMl = 2500 } $wellnessToken).data
    if ($settings.waterGoalMl -ne 2500) { throw 'Personal water goal was not saved.' }
    $otherDay = (Invoke-WellnessApi GET '/api/wellness/day' 200 $null $other.token).data
    if ($otherDay.waterGoalMl -ne 2000 -or $otherDay.waterTotalMl -ne 0) { throw 'Water settings leaked to another account.' }

    $water = (Invoke-WellnessApi POST '/api/wellness/water' 201 @{ date = $today; amountMl = 250 } $wellnessToken).data
    $firstId = $water.waterEntries[0].id
    $water = (Invoke-WellnessApi POST '/api/wellness/water' 201 @{ date = $today; amountMl = 500 } $wellnessToken).data
    if ($water.waterTotalMl -ne 750 -or @($water.waterEntries).Count -ne 2) { throw 'Water additions were not accumulated correctly.' }
    Invoke-WellnessApi DELETE "/api/wellness/water/$firstId" 404 $null $other.token | Out-Null
    Invoke-WellnessApi DELETE "/api/wellness/water/$firstId" 204 $null $wellnessToken | Out-Null
    Invoke-WellnessApi DELETE "/api/wellness/water/$firstId" 404 $null $wellnessToken | Out-Null
    $water = (Invoke-WellnessApi GET "/api/wellness/day?date=$today" 200 $null $wellnessToken).data
    if ($water.waterTotalMl -ne 500 -or @($water.waterEntries).Count -ne 1) { throw 'Deleting one water entry changed the wrong amount.' }

    $sleep = (Invoke-WellnessApi PUT "/api/wellness/days/$today/sleep" 200 @{ sleepMinutes = 480 } $wellnessToken).data
    if ($sleep.sleepMinutes -ne 480 -or $sleep.waterTotalMl -ne 500) { throw 'Sleep was not saved independently of water.' }
    $body = (Invoke-WellnessApi PUT "/api/wellness/days/$today/body" 200 @{ heightCm = 200; weightKg = 80 } $wellnessToken).data
    if ([decimal]$body.bmi -ne 20 -or [decimal]$body.weightKg -ne 80 -or [decimal]$body.heightCm -ne 200 -or $body.sleepMinutes -ne 480) { throw 'Body measurement or BMI calculation is incorrect.' }
    $bodyAgain = (Invoke-WellnessApi PUT "/api/wellness/days/$today/body" 200 @{ heightCm = 200; weightKg = 80 } $wellnessToken).data
    if ([decimal]$bodyAgain.bmi -ne 20) { throw 'Repeating a body measurement was not idempotent.' }
    Invoke-WellnessApi PUT "/api/wellness/days/$yesterday/body" 200 @{ heightCm = 180; weightKg = 81 } $wellnessToken | Out-Null
    Invoke-WellnessApi PUT "/api/wellness/days/$yesterday/sleep" 200 @{ sleepMinutes = 450 } $wellnessToken | Out-Null
    $history = @((Invoke-WellnessApi GET "/api/wellness/history?from=$yesterday&to=$today" 200 $null $wellnessToken).data | Where-Object { $null -ne $_ })
    $historical = @($history | Where-Object date -EQ $yesterday)
    $current = @($history | Where-Object date -EQ $today)
    if ($history.Count -ne 2 -or $historical.Count -ne 1 -or $current.Count -ne 1 -or [decimal]$historical[0].bmi -ne 25 -or [decimal]$current[0].bmi -ne 20) { throw 'History did not retain independent daily measurements.' }
    $otherHistory = @((Invoke-WellnessApi GET "/api/wellness/history?from=$yesterday&to=$today" 200 $null $other.token).data | Where-Object { $null -ne $_ })
    if ($otherHistory.Count -ne 2 -or @($otherHistory | Where-Object { $_.waterTotalMl -ne 0 -or $null -ne $_.sleepMinutes -or $null -ne $_.bmi }).Count -ne 0) { throw 'Private measurements leaked to another account.' }
    $emptyDate = $todayValue.AddDays(-2).ToString('yyyy-MM-dd')
    $emptyDay = (Invoke-WellnessApi GET "/api/wellness/day?date=$emptyDate" 200 $null $wellnessToken).data
    if ($emptyDay.waterTotalMl -ne 0 -or $null -ne $emptyDay.sleepMinutes -or $null -ne $emptyDay.bmi) { throw 'A missing day was replaced with invented personal measurements.' }

    Invoke-WellnessApi POST '/api/wellness/water' 400 @{ date = $today; amountMl = 0 } $wellnessToken | Out-Null
    Invoke-WellnessApi POST '/api/wellness/water' 400 @{ date = $today; amountMl = 2001 } $wellnessToken | Out-Null
    Invoke-WellnessApi POST '/api/wellness/water' 400 @{ date = $tomorrow; amountMl = 250 } $wellnessToken | Out-Null
    Invoke-WellnessApi PUT '/api/wellness/settings' 400 @{ waterGoalMl = 249 } $wellnessToken | Out-Null
    Invoke-WellnessApi PUT "/api/wellness/days/$today/sleep" 400 @{ sleepMinutes = 1441 } $wellnessToken | Out-Null
    Invoke-WellnessApi PUT "/api/wellness/days/$today/body" 400 @{ heightCm = 0; weightKg = 80 } $wellnessToken | Out-Null
    Invoke-WellnessApi GET "/api/wellness/day?date=$tomorrow" 400 $null $wellnessToken | Out-Null
    Invoke-WellnessApi GET '/api/wellness/day?date=1999-12-31' 400 $null $wellnessToken | Out-Null
    $tooEarly = $todayValue.AddDays(-31).ToString('yyyy-MM-dd')
    Invoke-WellnessApi GET "/api/wellness/history?from=$tooEarly&to=$today" 400 $null $wellnessToken | Out-Null
    Invoke-WellnessApi GET "/api/wellness/history?from=$today&to=$yesterday" 400 $null $wellnessToken | Out-Null

    for ($index = 0; $index -lt 5; $index++) {
        Invoke-WellnessApi POST '/api/wellness/water' 201 @{ date = $yesterday; amountMl = 2000 } $wellnessToken | Out-Null
    }
    Invoke-WellnessApi POST '/api/wellness/water' @(400,409) @{ date = $yesterday; amountMl = 1 } $wellnessToken | Out-Null
    $capped = (Invoke-WellnessApi GET "/api/wellness/day?date=$yesterday" 200 $null $wellnessToken).data
    if ($capped.waterTotalMl -ne 10000) { throw 'Daily input limit was exceeded or corrupted the saved total.' }

    $openApi = (Invoke-WellnessApi GET '/api/wellness/v3/api-docs' 200).data
    $dayOperation = $openApi.paths.PSObject.Properties['/api/wellness/day'].Value.get
    if (@($dayOperation.security | Where-Object { $_.PSObject.Properties['bearerAuth'] }).Count -eq 0) { throw 'OpenAPI does not document authentication for personal indicators.' }

    Invoke-WellnessApi DELETE "/api/wellness/days/$today/sleep" 204 $null $wellnessToken | Out-Null
    Invoke-WellnessApi DELETE "/api/wellness/days/$today/body" 204 $null $wellnessToken | Out-Null
    $removed = (Invoke-WellnessApi GET "/api/wellness/day?date=$today" 200 $null $wellnessToken).data
    if ($null -ne $removed.sleepMinutes -or $null -ne $removed.bmi -or $removed.waterTotalMl -ne 500) { throw 'Removing sleep/body data changed unrelated water entries.' }
    Write-Host 'PASS: register/login -> personal goal -> water additions/deletion -> sleep -> body measurements/BMI -> daily history.'
    Write-Host 'PASS: private account isolation, authentication, validation, empty days, daily input limit and request IDs through Gateway.'
    Write-Host 'PASS: 80 kg at 200 cm gives BMI 20.0; yesterday retains its own 81 kg / 180 cm snapshot and BMI 25.0.'
} finally {
    if ($wellnessToken) {
        foreach ($date in $wellnessDates) {
            try {
                $remaining = (Invoke-WellnessApi GET "/api/wellness/day?date=$date" 200 $null $wellnessToken).data
                foreach ($entry in $remaining.waterEntries) {
                    Invoke-WellnessApi DELETE "/api/wellness/water/$($entry.id)" 204 $null $wellnessToken | Out-Null
                }
                Invoke-WellnessApi DELETE "/api/wellness/days/$date/sleep" 204 $null $wellnessToken | Out-Null
                Invoke-WellnessApi DELETE "/api/wellness/days/$date/body" 204 $null $wellnessToken | Out-Null
            } catch { Write-Warning "Could not remove temporary wellness data for $date; inspect the QA account when services are available." }
        }
        try { Invoke-WellnessApi PUT '/api/wellness/settings' 200 @{ waterGoalMl = 2000 } $wellnessToken | Out-Null }
        catch { Write-Warning 'Could not restore the personal goal of the temporary QA account.' }
    }
    $wellnessClient.Dispose()
}
