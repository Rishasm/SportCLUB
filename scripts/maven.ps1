param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)
. "$PSScriptRoot/common.ps1"

$java = Get-SportClubJava
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8 -Xmx512m'
$maven = $null
$command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($command) { $maven = $command.Source }
if (-not $maven) {
    $jetBrains = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $jetBrains) {
        foreach ($installation in (Get-ChildItem -LiteralPath $jetBrains -Directory)) {
            $candidate = Join-Path $installation.FullName 'plugins/maven/lib/maven3/bin/mvn.cmd'
            if (Test-Path -LiteralPath $candidate) { $maven = $candidate; break }
        }
    }
}
if (-not $maven) {
    $version = '3.9.11'
    $tools = Join-Path $script:RuntimeRoot 'tools'
    $maven = Join-Path $tools "apache-maven-$version/bin/mvn.cmd"
    if (-not (Test-Path -LiteralPath $maven)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $archive = Join-Path $tools "apache-maven-$version-bin.zip"
        $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
        Write-Host "Downloading Apache Maven $version..."
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $archive
        $checksumText = (Invoke-WebRequest -UseBasicParsing -Uri "$url.sha512").Content
        if ($checksumText -is [byte[]]) { $checksumText = [Text.Encoding]::ASCII.GetString($checksumText) }
        $expected = ($checksumText.Trim() -split '\s+')[0]
        $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
        if ($actual -ne $expected) { throw 'Maven archive checksum mismatch.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
    }
}
Push-Location $script:ProjectRoot
try {
    & $maven "-Dmaven.repo.local=$script:RuntimeRoot/maven-repository" -B -ntp @MavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

