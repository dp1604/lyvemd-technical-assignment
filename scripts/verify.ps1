[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
& (Join-Path $PSScriptRoot 'prepare-local-database.ps1')
Set-AtlasDatabaseEnvironment -Database atlas_test
$atlasSecrets = Get-Content -LiteralPath (Join-Path $AtlasLocal 'secrets\database.json') -Raw | ConvertFrom-Json
$env:ATLAS_MIGRATION_USER = 'atlas_migrate'
$env:ATLAS_MIGRATION_PASSWORD = $atlasSecrets.migrate
try { Invoke-AtlasMaven -Goals @('-Pintegration', 'verify') }
finally { Clear-AtlasDatabaseEnvironment }
