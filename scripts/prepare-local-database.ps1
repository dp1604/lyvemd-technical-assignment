[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
if (-not $IsWindows) { throw 'This local bootstrap supports Windows with PowerShell 7. Use the documented external database setup elsewhere.' }
$atlasSecretsDirectory = Join-Path $AtlasLocal 'secrets'
New-Item -ItemType Directory -Force -Path $atlasSecretsDirectory | Out-Null
$atlasAllowedSids = @([System.Security.Principal.WindowsIdentity]::GetCurrent().User,
        [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
        [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))
$atlasExistingAcl = Get-Acl -LiteralPath $atlasSecretsDirectory
$atlasExistingRules = @($atlasExistingAcl.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))
$atlasAclCorrect = $atlasExistingAcl.AreAccessRulesProtected -and $atlasExistingRules.Count -eq 3
foreach ($atlasExistingRule in $atlasExistingRules) {
    $atlasAclCorrect = $atlasAclCorrect -and ($atlasExistingRule.IdentityReference.Value -in $atlasAllowedSids.Value) `
        -and $atlasExistingRule.AccessControlType -eq 'Allow' -and $atlasExistingRule.FileSystemRights -eq 'FullControl'
}
if (-not $atlasAclCorrect) {
    $atlasAcl = [System.Security.AccessControl.DirectorySecurity]::new()
    $atlasAcl.SetAccessRuleProtection($true, $false)
    foreach ($atlasSid in $atlasAllowedSids) {
        $atlasRule = [System.Security.AccessControl.FileSystemAccessRule]::new($atlasSid, 'FullControl',
            'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $atlasAcl.AddAccessRule($atlasRule)
    }
    [System.IO.FileSystemAclExtensions]::SetAccessControl([System.IO.DirectoryInfo]::new($atlasSecretsDirectory), $atlasAcl)
}
$atlasSecretsFile = Join-Path $atlasSecretsDirectory 'database.json'
if (-not (Test-Path -LiteralPath $atlasSecretsFile)) {
    $atlasNewSecrets = @{}
    foreach ($atlasPurpose in @('admin', 'migrate', 'runtime')) {
        $atlasNewSecrets[$atlasPurpose] = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    }
    [System.IO.File]::WriteAllText($atlasSecretsFile, ($atlasNewSecrets | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
}
$atlasSecrets = Get-Content -LiteralPath $atlasSecretsFile -Raw | ConvertFrom-Json
foreach ($atlasPurpose in @('admin', 'migrate', 'runtime')) {
    if ($atlasSecrets.$atlasPurpose -notmatch '^[0-9A-F]{64}$') { throw 'Invalid local credential file; preserve and inspect it rather than recreating the database.' }
}
$atlasData = Join-Path $AtlasLocal 'postgres-data'
if (-not (Test-Path -LiteralPath (Join-Path $atlasData 'PG_VERSION'))) {
    $atlasInitPassword = Join-Path $atlasSecretsDirectory 'init-password.txt'
    try {
        [System.IO.File]::WriteAllText($atlasInitPassword, $atlasSecrets.admin)
        & (Join-Path $AtlasPgBin 'initdb.exe') -D $atlasData -U atlas_admin --pwfile=$atlasInitPassword --auth-host=scram-sha-256 --auth-local=scram-sha-256 --encoding=UTF8 --locale=C *> (Join-Path $AtlasLocal 'initdb.log')
        if ($LASTEXITCODE -ne 0) { throw 'Local PostgreSQL initialization failed; inspect work/local/initdb.log.' }
    } finally {
        if (Test-Path -LiteralPath $atlasInitPassword) { Remove-Item -LiteralPath $atlasInitPassword }
    }
}
if ((Get-Content -LiteralPath (Join-Path $atlasData 'PG_VERSION') -Raw).Trim() -ne '16') { throw 'The assessment requires PostgreSQL 16.' }
& (Join-Path $AtlasPgBin 'pg_ctl.exe') -D $atlasData status *> $null
if ($LASTEXITCODE -ne 0) {
    $atlasArguments = @('-D', ('"' + $atlasData + '"'), '-l', ('"' + (Join-Path $AtlasLocal 'postgres.log') + '"'),
        '-o', '"-h 127.0.0.1 -p 55433"', '-t', '20', '-w', 'start')
    $atlasStart = Start-Process -FilePath (Join-Path $AtlasPgBin 'pg_ctl.exe') -ArgumentList $atlasArguments -WindowStyle Hidden -PassThru
    if (-not $atlasStart.WaitForExit(30000) -or $atlasStart.ExitCode -ne 0) { throw 'Local PostgreSQL start failed; inspect work/local/postgres.log. No existing cluster was replaced.' }
}
$env:PGPASSWORD = $atlasSecrets.admin
$env:PGCONNECT_TIMEOUT = '5'
try {
    $atlasPsql = Join-Path $AtlasPgBin 'psql.exe'
    $atlasPgOptions = @('-X', '-q', '-h', '127.0.0.1', '-p', '55433', '-U', 'atlas_admin', '-v', 'ON_ERROR_STOP=1')
    $atlasRoles = @"
DO `$roles`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='atlas_migrate') THEN
    CREATE ROLE atlas_migrate LOGIN PASSWORD '$($atlasSecrets.migrate)' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='atlas_runtime') THEN
    CREATE ROLE atlas_runtime LOGIN PASSWORD '$($atlasSecrets.runtime)' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END
`$roles`$;
"@
    $atlasRoles | & $atlasPsql @atlasPgOptions -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'Local database role preparation failed.' }
    foreach ($atlasDatabase in @('atlas', 'atlas_test')) {
        $atlasExists = & $atlasPsql @atlasPgOptions -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$atlasDatabase'"
        if ($LASTEXITCODE -ne 0) { throw 'Local database inventory failed.' }
        if ($atlasExists -ne '1') {
            & (Join-Path $AtlasPgBin 'createdb.exe') -h 127.0.0.1 -p 55433 -U atlas_admin -O atlas_migrate $atlasDatabase
            if ($LASTEXITCODE -ne 0) { throw 'Local assessment database creation failed.' }
        }
        $atlasSetup = @"
REVOKE ALL ON DATABASE $atlasDatabase FROM PUBLIC;
GRANT CONNECT ON DATABASE $atlasDatabase TO atlas_migrate, atlas_runtime;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS atlas AUTHORIZATION atlas_migrate;
REVOKE ALL ON SCHEMA atlas FROM PUBLIC;
GRANT USAGE ON SCHEMA atlas TO atlas_runtime;
ALTER ROLE atlas_runtime IN DATABASE $atlasDatabase SET search_path=atlas,pg_catalog;
ALTER ROLE atlas_runtime IN DATABASE $atlasDatabase SET statement_timeout='10s';
ALTER ROLE atlas_runtime IN DATABASE $atlasDatabase SET lock_timeout='3s';
ALTER ROLE atlas_runtime IN DATABASE $atlasDatabase SET idle_in_transaction_session_timeout='15s';
"@
        $atlasSetup | & $atlasPsql @atlasPgOptions -d $atlasDatabase
        if ($LASTEXITCODE -ne 0) { throw 'Local schema/privilege preparation failed.' }
    }
    Write-Output 'Local assessment and test databases ready; existing data preserved. PostgreSQL listens on 127.0.0.1:55433.'
} finally { Remove-Item Env:\PGPASSWORD,Env:\PGCONNECT_TIMEOUT -ErrorAction SilentlyContinue }
