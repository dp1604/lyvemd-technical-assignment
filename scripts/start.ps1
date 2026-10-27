[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
& (Join-Path $PSScriptRoot 'prepare-local-database.ps1')
Invoke-AtlasMaven -Goals @('package')
Set-AtlasDatabaseEnvironment -Database atlas
try {
    Invoke-AtlasMigration
    Write-Output 'Atlas starts on http://127.0.0.1:18081 by default. Stop with Ctrl+C. Test headers are not real authentication.'
    & $AtlasJava '-Duser.timezone=UTC' -jar (Join-Path $AtlasRoot 'target\atlas-0.1.0-SNAPSHOT.jar')
    if ($LASTEXITCODE -ne 0) { throw 'Atlas exited unsuccessfully.' }
} finally { Clear-AtlasDatabaseEnvironment }
