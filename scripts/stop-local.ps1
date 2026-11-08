. "$PSScriptRoot/common.ps1"
$manifestPath = Join-Path $script:RuntimeRoot 'processes.json'
if (-not (Test-Path -LiteralPath $manifestPath)) { Write-Host 'No managed SportCLUB processes.'; return }
$parsedEntries = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($parsedEntries | Where-Object { $null -ne $_ })
[array]::Reverse($entries)
foreach ($entry in $entries) {
    $expectedJar = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot "$($entry.name)/target/$($entry.name)-0.1.0-SNAPSHOT.jar"))
    if ($expectedJar -ne $entry.jarPath) { throw "Unexpected jar path for $($entry.name). Refusing to stop it." }
    if (Test-SportClubProcess -ProcessId $entry.processId -JarPath $expectedJar) {
        Stop-Process -Id $entry.processId -ErrorAction SilentlyContinue
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while (Test-SportClubProcess -ProcessId $entry.processId -JarPath $expectedJar) {
            if ([DateTime]::UtcNow -ge $deadline) { throw "Could not stop $($entry.name)." }
            Start-Sleep -Milliseconds 200
        }
        Write-Host "Stopped $($entry.name)."
    }
}
'[]' | Set-Content -LiteralPath $manifestPath -Encoding UTF8

