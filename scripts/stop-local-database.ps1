[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
$atlasData = Join-Path $AtlasLocal 'postgres-data'
if (-not (Test-Path -LiteralPath (Join-Path $atlasData 'PG_VERSION'))) { throw 'No assessment cluster exists; nothing was stopped.' }
& (Join-Path $AtlasPgBin 'pg_ctl.exe') -D $atlasData -m fast -t 20 -w stop
if ($LASTEXITCODE -ne 0) { throw 'Assessment PostgreSQL stop failed. Inspect its status before retrying.' }
