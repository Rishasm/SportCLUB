param([string]$GatewayUrl = 'http://localhost:8080')
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(15)

function Invoke-ScenarioApi {
    param([string]$Method, [string]$Path, [int]$Expected, $Body = $null, [string]$Token = '')
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$GatewayUrl$Path")
    $request.Headers.Add('X-Request-ID', "scenario-$([Guid]::NewGuid().ToString('N'))")
    if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    if ($null -ne $Body) {
        $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = if ($text.Trim()) { $text | ConvertFrom-Json } else { $null }
        if ([int]$response.StatusCode -ne $Expected) {
            $code = if ($data -and $data.PSObject.Properties['code']) { $data.code } else { 'UNKNOWN' }
            throw "$Method $Path returned $([int]$response.StatusCode), expected $Expected (code=$code)."
        }
        return [pscustomobject]@{ data = $data; status = [int]$response.StatusCode }
    } finally { $request.Dispose() }
}

try {
    $email = "qa-$([Guid]::NewGuid().ToString('N'))@sportclub.local"
    $password = 'ScenarioPass123!'
    $auth = (Invoke-ScenarioApi POST '/api/members/auth/register' 201 @{ name = 'Booking QA'; email = $email; password = $password }).data
    $token = $auth.token
    if (-not $token) { throw 'Registration did not return a token.' }
    Invoke-ScenarioApi GET '/api/members/me' 401 | Out-Null
    Invoke-ScenarioApi POST '/api/members/auth/login' 401 @{ email = $email; password = 'WrongPassword1!' } | Out-Null
    Invoke-ScenarioApi POST '/api/members/auth/register' 409 @{ name = 'Duplicate Booking QA'; email = $email; password = $password } | Out-Null
    $tomorrow = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(3)).AddDays(1).ToString('yyyy-MM-dd')
    $classes = (Invoke-ScenarioApi GET "/api/club/classes?date=$tomorrow&type=ALL" 200).data
    $class = @($classes | Where-Object availableSpots -GT 0)[0]
    if (-not $class) { throw 'No future class with free places. Enable local demo data.' }
    Invoke-ScenarioApi POST '/api/club/bookings' 403 @{ classId = $class.id } $token | Out-Null
    $membership = (Invoke-ScenarioApi POST '/api/members/me/membership' 201 @{ planId = 'rhythm' } $token).data
    if ($membership.status -ne 'ACTIVE') { throw 'Membership is not active.' }
    Invoke-ScenarioApi POST '/api/members/me/membership' 409 @{ planId = 'start' } $token | Out-Null
    $booking = (Invoke-ScenarioApi POST '/api/club/bookings' 201 @{ classId = $class.id } $token).data
    Invoke-ScenarioApi POST '/api/club/bookings' 409 @{ classId = $class.id } $token | Out-Null
    $myBookings = (Invoke-ScenarioApi GET '/api/club/me/bookings' 200 $null $token).data
    if (@($myBookings | Where-Object id -EQ $booking.id).Count -ne 1) { throw 'Booking does not appear in the cabinet.' }
    $other = (Invoke-ScenarioApi POST '/api/members/auth/register' 201 @{ name = 'Other Booking QA'; email = "other-$email"; password = $password }).data
    Invoke-ScenarioApi DELETE "/api/club/bookings/$($booking.id)" 404 $null $other.token | Out-Null
    if (@((Invoke-ScenarioApi GET '/api/club/me/bookings' 200 $null $other.token).data | Where-Object { $null -ne $_ }).Count -ne 0) { throw 'Another member can see private bookings.' }
    $login = (Invoke-ScenarioApi POST '/api/members/auth/login' 200 @{ email = $email; password = $password }).data
    $profile = (Invoke-ScenarioApi GET '/api/members/me' 200 $null $login.token).data
    if ($profile.membership.id -ne $membership.id) { throw 'Membership was not persisted.' }
    Invoke-ScenarioApi DELETE "/api/club/bookings/$($booking.id)" 204 $null $token | Out-Null
    if (@((Invoke-ScenarioApi GET '/api/club/me/bookings' 200 $null $token).data | Where-Object { $null -ne $_ }).Count -ne 0) { throw 'Cancellation did not update the cabinet.' }
    $rebooked = (Invoke-ScenarioApi POST '/api/club/bookings' 201 @{ classId = $class.id } $token).data
    Invoke-ScenarioApi DELETE "/api/club/bookings/$($rebooked.id)" 204 $null $token | Out-Null
    Write-Host 'PASS: register -> login -> membership -> booking -> cabinet -> cancellation -> rebooking.'
    Write-Host 'PASS: unauthorized access, inactive membership, duplicate email/membership/booking and cancellation by another member are rejected.'
    Write-Host 'The scenario creates two QA accounts and leaves no active bookings.'
} finally { $client.Dispose() }
