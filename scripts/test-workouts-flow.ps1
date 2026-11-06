param([string]$GatewayUrl = 'http://localhost:8080')
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.Net.Http
$workoutsClient = [System.Net.Http.HttpClient]::new()
$workoutsClient.Timeout = [TimeSpan]::FromSeconds(15)
$workoutsToken = ''
$workoutsProgramId = $null
$workoutsCompletedId = $null

function Invoke-WorkoutApi {
    param([string]$Method, [string]$Path, [int[]]$Expected, $Body = $null, [string]$Token = '')
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$GatewayUrl$Path")
    $response = $null
    $request.Headers.Add('X-Request-ID', "workouts-qa-$([Guid]::NewGuid().ToString('N'))")
    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    }
    if ($null -ne $Body) {
        $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 12 -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $workoutsClient.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = if ($text.Trim()) { $text | ConvertFrom-Json } else { $null }
        $status = [int]$response.StatusCode
        if ($status -notin $Expected) {
            $code = if ($null -ne $data -and $data.PSObject.Properties['code']) { $data.code } else { 'UNKNOWN' }
            throw "$Method $Path returned $status, expected $($Expected -join '/') (code=$code)."
        }
        return [pscustomobject]@{ data = $data; status = $status }
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}

try {
    $workoutsEmail = "qa-workouts-$([Guid]::NewGuid().ToString('N'))@sportclub.local"
    $workoutsPassword = 'WorkoutsQaPass123!'
    $auth = (Invoke-WorkoutApi POST '/api/members/auth/register' 201 @{ name = 'Workout QA'; email = $workoutsEmail; password = $workoutsPassword }).data
    $login = (Invoke-WorkoutApi POST '/api/members/auth/login' 200 @{ email = $workoutsEmail; password = $workoutsPassword }).data
    $workoutsToken = $login.token
    if (-not $workoutsToken -or $login.user.id -ne $auth.user.id) { throw 'Registration/login did not produce the expected identity.' }

    Invoke-WorkoutApi GET '/api/workouts/exercises' 401 | Out-Null
    $catalog = @((Invoke-WorkoutApi GET '/api/workouts/exercises' 200 $null $workoutsToken).data | Where-Object { $null -ne $_ })
    $programs = @((Invoke-WorkoutApi GET '/api/workouts/programs' 200 $null $workoutsToken).data | Where-Object { $null -ne $_ })
    if ($catalog.Count -eq 0 -or @($programs | Where-Object isTemplate -EQ $true).Count -eq 0) { throw 'Exercise catalog or shared templates are missing.' }
    $exerciseId = $catalog[0].id

    Invoke-WorkoutApi POST '/api/workouts/programs' 400 @{ name = 'Invalid empty program'; description = ''; exercises = @() } $workoutsToken | Out-Null
    Invoke-WorkoutApi POST '/api/workouts/programs' @(400,404) @{ name = 'Unknown exercise'; description = ''; exercises = @(@{ exerciseId = 'unknown-qa-exercise'; sets = 2; reps = 10; weightKg = 20 }) } $workoutsToken | Out-Null
    $program = (Invoke-WorkoutApi POST '/api/workouts/programs' 201 @{
        name = 'QA two-set program'
        description = 'Automated local training diary check'
        exercises = @(@{ exerciseId = $exerciseId; sets = 2; reps = 10; weightKg = 20 })
    } $workoutsToken).data
    $workoutsProgramId = $program.id
    if ($program.isTemplate -or @($program.exercises).Count -ne 1) { throw 'Personal program was not created correctly.' }
    $ownPrograms = @((Invoke-WorkoutApi GET '/api/workouts/programs' 200 $null $workoutsToken).data | Where-Object id -EQ $workoutsProgramId)
    if ($ownPrograms.Count -ne 1) { throw 'Personal program is missing from the program list.' }

    $other = (Invoke-WorkoutApi POST '/api/members/auth/register' 201 @{ name = 'Other Workout QA'; email = "other-$workoutsEmail"; password = $workoutsPassword }).data
    $otherPrograms = @((Invoke-WorkoutApi GET '/api/workouts/programs' 200 $null $other.token).data | Where-Object id -EQ $workoutsProgramId)
    if ($otherPrograms.Count -ne 0) { throw 'Another member can see a private program.' }
    Invoke-WorkoutApi DELETE "/api/workouts/programs/$workoutsProgramId" 404 $null $other.token | Out-Null
    Invoke-WorkoutApi POST '/api/workouts/sessions' 404 @{ programId = $workoutsProgramId } $other.token | Out-Null

    $session = (Invoke-WorkoutApi POST '/api/workouts/sessions' 201 @{ programId = $workoutsProgramId } $workoutsToken).data
    $sessionId = $session.id
    $setId = $session.exercises[0].sets[0].id
    if ($session.status -ne 'ACTIVE' -or $session.totalSets -ne 2 -or $session.completedSets -ne 0) { throw 'New session has incorrect state.' }
    Invoke-WorkoutApi POST '/api/workouts/sessions' 409 @{ programId = $workoutsProgramId } $workoutsToken | Out-Null
    Invoke-WorkoutApi POST "/api/workouts/sessions/$sessionId/finish" 409 @{ durationMinutes = 30; weightKg = 80 } $workoutsToken | Out-Null
    Invoke-WorkoutApi GET "/api/workouts/sessions/$sessionId" 404 $null $other.token | Out-Null
    Invoke-WorkoutApi PUT "/api/workouts/sessions/$sessionId/sets/$setId" 404 @{ weightKg = 20; reps = 10; completed = $true } $other.token | Out-Null
    Invoke-WorkoutApi POST "/api/workouts/sessions/$sessionId/finish" 404 @{ durationMinutes = 30; weightKg = 80 } $other.token | Out-Null
    Invoke-WorkoutApi DELETE "/api/workouts/sessions/$sessionId" 404 $null $other.token | Out-Null
    Invoke-WorkoutApi PUT "/api/workouts/sessions/$sessionId/sets/$setId" 400 @{ weightKg = -1; reps = 10; completed = $true } $workoutsToken | Out-Null

    $updated = (Invoke-WorkoutApi PUT "/api/workouts/sessions/$sessionId/sets/$setId" 200 @{ weightKg = 20; reps = 10; completed = $true } $workoutsToken).data
    if ($updated.completedSets -ne 1 -or [decimal]$updated.totalVolumeKg -ne 200) { throw 'Completed approach or training volume was not recorded correctly.' }
    $active = (Invoke-WorkoutApi GET '/api/workouts/sessions/active' 200 $null $workoutsToken).data
    if ($active.id -ne $sessionId -or $active.completedSets -ne 1 -or -not $active.exercises[0].sets[0].completed) { throw 'Active workout cannot be resumed with the stored approach.' }
    $completed = (Invoke-WorkoutApi POST "/api/workouts/sessions/$sessionId/finish" 200 @{ durationMinutes = 30; weightKg = 80 } $workoutsToken).data
    $workoutsCompletedId = $completed.id
    if ($completed.status -ne 'COMPLETED' -or $completed.completedSets -ne 1 -or $completed.durationMinutes -ne 30 -or [decimal]$completed.totalVolumeKg -ne 200) { throw 'Completed workout summary is incorrect.' }
    if ([Math]::Abs([double]$completed.estimatedCalories - 147) -gt 1) { throw 'Approximate calorie estimate is inconsistent with duration and entered body weight.' }
    if ($null -ne (Invoke-WorkoutApi GET '/api/workouts/sessions/active' 200 $null $workoutsToken).data) { throw 'Finished workout still appears as active.' }
    Invoke-WorkoutApi PUT "/api/workouts/sessions/$sessionId/sets/$setId" 409 @{ weightKg = 30; reps = 10; completed = $true } $workoutsToken | Out-Null
    Invoke-WorkoutApi DELETE "/api/workouts/sessions/$sessionId" 409 $null $workoutsToken | Out-Null
    $history = @((Invoke-WorkoutApi GET '/api/workouts/sessions' 200 $null $workoutsToken).data | Where-Object id -EQ $sessionId)
    if ($history.Count -ne 1) { throw 'Completed session did not appear once in history.' }
    $otherHistory = @((Invoke-WorkoutApi GET '/api/workouts/sessions' 200 $null $other.token).data | Where-Object { $null -ne $_ })
    if ($otherHistory.Count -ne 0) { throw 'Another member can see private workout history.' }

    $next = (Invoke-WorkoutApi POST '/api/workouts/sessions' 201 @{ programId = $workoutsProgramId } $workoutsToken).data
    $previous = $next.exercises[0].previousResult
    if ($null -eq $previous -or [decimal]$previous.weightKg -ne 20 -or $previous.reps -ne 10) { throw 'Previous personal result did not carry over from completed history.' }
    Invoke-WorkoutApi DELETE "/api/workouts/sessions/$($next.id)" 204 $null $workoutsToken | Out-Null
    Invoke-WorkoutApi DELETE "/api/workouts/programs/$workoutsProgramId" 204 $null $workoutsToken | Out-Null
    $workoutsProgramId = $null
    $snapshot = (Invoke-WorkoutApi GET "/api/workouts/sessions/$sessionId" 200 $null $workoutsToken).data
    if ($snapshot.programName -ne 'QA two-set program' -or $snapshot.exercises[0].sets[0].weightKg -ne 20) { throw 'Deleting a program altered completed workout history.' }

    Write-Host 'PASS: register/login -> catalog/templates -> own program -> workout -> saved approach -> finish -> history -> previous result.'
    Write-Host 'PASS: authentication, personal data isolation, duplicate active sessions, validation and completed-session immutability.'
    Write-Host 'PASS: two sets planned, one set completed at 20 kg x 10, volume 200 kg, approximate estimate 147 kcal for 30 min at 80 kg.'
    Write-Host 'The scenario keeps two QA accounts and one completed workout; no active workouts or personal test programs remain.'
} finally {
    if ($workoutsToken) {
        try {
            $remaining = (Invoke-WorkoutApi GET '/api/workouts/sessions/active' 200 $null $workoutsToken).data
            if ($null -ne $remaining) { Invoke-WorkoutApi DELETE "/api/workouts/sessions/$($remaining.id)" 204 $null $workoutsToken | Out-Null }
        } catch { Write-Warning 'Could not clean up the active QA workout; inspect the QA account if the service is unavailable.' }
        if ($workoutsProgramId) {
            try { Invoke-WorkoutApi DELETE "/api/workouts/programs/$workoutsProgramId" 204 $null $workoutsToken | Out-Null }
            catch { Write-Warning 'Could not remove the personal QA program; inspect the QA account if the service is unavailable.' }
        }
    }
    $workoutsClient.Dispose()
}
