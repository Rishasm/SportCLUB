param(
    [ValidateRange(1, 65535)][int]$Port = 5432,
    [string]$AdminUser = 'postgres',
    [Security.SecureString]$AdminPassword,
    [switch]$RecoverIncompleteSetup
)
. "$PSScriptRoot/common.ps1"

$client = Get-Command psql.exe -ErrorAction SilentlyContinue
$psql = if ($client) { $client.Source } else {
    $installations = Join-Path $env:ProgramFiles 'PostgreSQL'
    if (Test-Path -LiteralPath $installations) {
        foreach ($installation in (Get-ChildItem -LiteralPath $installations -Directory | Sort-Object Name -Descending)) {
            $candidate = Join-Path $installation.FullName 'bin/psql.exe'
            if (Test-Path -LiteralPath $candidate) { $candidate; break }
        }
    }
}
if (-not $psql) { throw 'psql.exe was not found. Install PostgreSQL or add its bin folder to PATH.' }
if ($AdminUser -eq 'sportclub_app') { throw 'Use a PostgreSQL administrator to create the databases.' }
if (-not $AdminPassword) { $AdminPassword = Read-Host "PostgreSQL password for $AdminUser on 127.0.0.1:$Port" -AsSecureString }

$role = 'sportclub_app'
$databases = @('sportclub_members','sportclub_club','sportclub_workouts','sportclub_wellness','sportclub_test')
$settingsFile = Join-Path $script:RuntimeRoot 'postgres.clixml'
$saved = if (Test-Path -LiteralPath $settingsFile) { Import-Clixml -LiteralPath $settingsFile }
$sameServer = $null -ne $saved -and [int]$saved.Port -eq $Port -and $saved.User -eq $role
$arguments = @('-X','-w','-h','127.0.0.1','-p',[string]$Port,'-U',$AdminUser,'-d','postgres','-A','-t','-q','-v','ON_ERROR_STOP=1')
$previousPassword = $env:PGPASSWORD
$previousTimeout = $env:PGCONNECT_TIMEOUT
try {
    $env:PGCONNECT_TIMEOUT = '5'
    $env:PGPASSWORD = [Net.NetworkCredential]::new('', $AdminPassword).Password
    $roleExists = & $psql @arguments -c "select 1 from pg_roles where rolname = '$role'"
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL administrator login failed.' }
    if ($roleExists -eq '1' -and -not $sameServer -and -not $RecoverIncompleteSetup) {
        throw 'The sportclub_app role already exists and no matching local credentials are saved. Existing roles were left intact.'
    }
    $databaseList = ($databases | ForEach-Object { "'$_'" }) -join ','
    $foreignDatabases = & $psql @arguments -c "select datname from pg_database where datname in ($databaseList) and pg_get_userbyid(datdba) <> '$role'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the database owners.' }
    if ($foreignDatabases) { throw "Database names already owned by another role: $($foreignDatabases -join ', ')." }

    if ($roleExists -eq '1' -and $sameServer) {
        $appPassword = $saved.Password
    } else {
        $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        $appPassword = [Net.NetworkCredential]::new('', $password).SecurePassword
        if ($roleExists -eq '1') {
            $unused = & $psql @arguments -c "select 1 from pg_roles r where r.rolname = '$role' and r.rolcanlogin and not (r.rolsuper or r.rolcreatedb or r.rolcreaterole or r.rolreplication or r.rolbypassrls) and not exists (select 1 from pg_database where datdba = r.oid) and not exists (select 1 from pg_auth_members where member = r.oid or roleid = r.oid) and not exists (select 1 from pg_shdepend where refclassid = 'pg_authid'::regclass and refobjid = r.oid)"
            if ($LASTEXITCODE -ne 0 -or $unused -ne '1') { throw 'The existing role is in use. Its password was left intact.' }
            "ALTER ROLE $role PASSWORD '$password';" | & $psql @arguments
        } else {
            "CREATE ROLE $role LOGIN PASSWORD '$password';" | & $psql @arguments
        }
        if ($LASTEXITCODE -ne 0) { throw 'Could not configure the SportCLUB database role.' }
    }
    New-Item -ItemType Directory -Force -Path $script:RuntimeRoot | Out-Null
    [pscustomobject]@{ Port = $Port; User = $role; Password = $appPassword } |
        Export-Clixml -LiteralPath $settingsFile

    foreach ($database in $databases) {
        $exists = & $psql @arguments -c "select 1 from pg_database where datname = '$database'"
        if ($LASTEXITCODE -ne 0) { throw "Could not check $database." }
        if ($exists -ne '1') {
            & $psql @arguments -c "CREATE DATABASE $database OWNER $role ENCODING 'UTF8' TEMPLATE template0"
            if ($LASTEXITCODE -ne 0) { throw "Could not create $database." }
        }
    }
    $env:PGPASSWORD = [Net.NetworkCredential]::new('', $appPassword).Password
    foreach ($database in $databases) {
        & $psql -X -w -h 127.0.0.1 -p $Port -U $role -d $database -A -t -q -c 'select 1' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "SportCLUB could not connect to $database." }
        Write-Host "Ready: $database"
    }
    Write-Host "SportCLUB PostgreSQL setup complete on 127.0.0.1:$Port."
} finally {
    $env:PGPASSWORD = $previousPassword
    $env:PGCONNECT_TIMEOUT = $previousTimeout
}
