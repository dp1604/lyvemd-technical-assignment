$ErrorActionPreference = 'Stop'
$AtlasRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$AtlasLocal = Join-Path $AtlasRoot 'work\local'
New-Item -ItemType Directory -Force -Path $AtlasLocal | Out-Null
$atlasTools = Join-Path $AtlasRoot 'work\tools'
$atlasJdk = Join-Path $atlasTools 'jdk-21.0.12.1+1'
if (Test-Path -LiteralPath (Join-Path $atlasJdk 'bin\java.exe')) { $env:JAVA_HOME = $atlasJdk }
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }
$AtlasJava = (Get-Command java -ErrorAction Stop).Source
$atlasMavenCandidate = Join-Path $atlasTools 'apache-maven-3.9.16\bin\mvn.cmd'
$AtlasMaven = if (Test-Path -LiteralPath $atlasMavenCandidate) { $atlasMavenCandidate } else { (Get-Command mvn -ErrorAction Stop).Source }
$AtlasPgBin = if ($env:ATLAS_PG_BIN) { $env:ATLAS_PG_BIN } else { Join-Path $atlasTools 'postgresql-16.15\pgsql\bin' }
if (-not (Test-Path -LiteralPath (Join-Path $AtlasPgBin 'pg_ctl.exe'))) {
    $AtlasPgBin = Split-Path -Parent (Get-Command pg_ctl -ErrorAction Stop).Source
}
$AtlasMavenOptions = @('-B', '-ntp')
$atlasCache = Join-Path $atlasTools 'maven-repository'
if (Test-Path -LiteralPath $atlasCache) { $AtlasMavenOptions += "-Dmaven.repo.local=$atlasCache" }

function Invoke-AtlasMaven {
    param([string[]]$Goals)
    Push-Location $AtlasRoot
    try {
        & $AtlasMaven @AtlasMavenOptions @Goals
        if ($LASTEXITCODE -ne 0) { throw 'Maven verification/build failed; application was not started.' }
    } finally { Pop-Location }
}

function Set-AtlasDatabaseEnvironment {
    param([ValidateSet('atlas', 'atlas_test')][string]$Database)
    $atlasSecrets = Get-Content -LiteralPath (Join-Path $AtlasLocal 'secrets\database.json') -Raw | ConvertFrom-Json
    $env:ATLAS_DB_URL = "jdbc:postgresql://127.0.0.1:55433/${Database}?connectTimeout=5&socketTimeout=20&ApplicationName=atlas"
    $env:ATLAS_DB_USER = 'atlas_runtime'
    $env:ATLAS_DB_PASSWORD = $atlasSecrets.runtime
}

function Invoke-AtlasMigration {
    $atlasSecrets = Get-Content -LiteralPath (Join-Path $AtlasLocal 'secrets\database.json') -Raw | ConvertFrom-Json
    $env:ATLAS_MIGRATION_USER = 'atlas_migrate'
    $env:ATLAS_MIGRATION_PASSWORD = $atlasSecrets.migrate
    try {
        & $AtlasJava '-Duser.timezone=UTC' -jar (Join-Path $AtlasRoot 'target\atlas-0.1.0-SNAPSHOT.jar') --migrate
        if ($LASTEXITCODE -ne 0) { throw 'Database migration failed; application was not started.' }
    } finally {
        Remove-Item Env:\ATLAS_MIGRATION_PASSWORD,Env:\ATLAS_MIGRATION_USER -ErrorAction SilentlyContinue
    }
}

function Clear-AtlasDatabaseEnvironment {
    Remove-Item Env:\ATLAS_DB_PASSWORD,Env:\ATLAS_MIGRATION_PASSWORD,Env:\ATLAS_MIGRATION_USER -ErrorAction SilentlyContinue
}
