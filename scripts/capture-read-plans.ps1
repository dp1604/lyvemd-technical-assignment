[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepositoryRoot,
    [Parameter(Mandatory)][string]$ManifestPath,
    [Parameter(Mandatory)][string]$PsqlPath,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$atlasPlanOutput = [IO.Path]::GetFullPath($OutputDirectory)
$atlasPlanProcess = $null
$atlasPlanError = $false

function Assert-AtlasPlan {
    param([bool]$Condition,[string]$Code)
    if (-not $Condition) { throw $Code }
}
function Get-AtlasSqlBlock {
    param([string]$Source,[string]$Marker)
    $atlasPattern = [regex]::Escape($Marker) + '\s*"""(?<sql>[\s\S]*?)"""'
    $atlasMatches = [regex]::Matches($Source,$atlasPattern)
    Assert-AtlasPlan ($atlasMatches.Count -eq 1) 'ATLAS_PLAN_SOURCE_SHAPE_CHANGED'
    return $atlasMatches[0].Groups['sql'].Value.Trim()
}
function Convert-AtlasParameters {
    param([string]$Sql,[int]$Expected)
    $atlasParts = $Sql.Split('?')
    Assert-AtlasPlan (($atlasParts.Length - 1) -eq $Expected) 'ATLAS_PLAN_PARAMETER_SHAPE_CHANGED'
    $atlasNumbered = $atlasParts[0]
    for ($atlasIndex = 1; $atlasIndex -lt $atlasParts.Length; $atlasIndex++) {
        $atlasNumbered += '$' + $atlasIndex + $atlasParts[$atlasIndex]
    }
    return $atlasNumbered
}
function Invoke-AtlasPlanPsql {
    param([string]$Sql)
    $atlasInfo = [Diagnostics.ProcessStartInfo]::new()
    $atlasInfo.FileName = [IO.Path]::GetFullPath($PsqlPath)
    $atlasInfo.UseShellExecute = $false
    $atlasInfo.CreateNoWindow = $true
    $atlasInfo.RedirectStandardInput = $true
    $atlasInfo.RedirectStandardOutput = $true
    $atlasInfo.RedirectStandardError = $true
    foreach ($atlasArgument in @('-X','--no-password','--quiet','--no-align','--tuples-only','--set=ON_ERROR_STOP=1')) {
        $atlasInfo.ArgumentList.Add($atlasArgument)
    }
    # Credentials/connection settings are inherited from the operator's PG environment only.
    # No password or clinical identifier is put into process arguments or ordinary output.
    $script:atlasPlanProcess = [Diagnostics.Process]::new()
    $script:atlasPlanProcess.StartInfo = $atlasInfo
    try {
        Assert-AtlasPlan ($script:atlasPlanProcess.Start()) 'ATLAS_PLAN_PROCESS_START_FAILED'
        $atlasStdout = $script:atlasPlanProcess.StandardOutput.ReadToEndAsync()
        $atlasStderr = $script:atlasPlanProcess.StandardError.ReadToEndAsync()
        $script:atlasPlanProcess.StandardInput.Write($Sql)
        $script:atlasPlanProcess.StandardInput.Close()
        if (-not $script:atlasPlanProcess.WaitForExit(15000)) {
            $script:atlasPlanProcess.Kill($true)
            $script:atlasPlanProcess.WaitForExit(3000) | Out-Null
            throw 'ATLAS_PLAN_PROCESS_TIMEOUT'
        }
        Assert-AtlasPlan ($script:atlasPlanProcess.ExitCode -eq 0) 'ATLAS_PLAN_DATABASE_ACTION_FAILED'
        $atlasText = $atlasStdout.GetAwaiter().GetResult()
        $null = $atlasStderr.GetAwaiter().GetResult() # Never emit raw database diagnostics.
        return $atlasText.Trim()
    } finally {
        if ($null -ne $script:atlasPlanProcess) {
            try { if (-not $script:atlasPlanProcess.HasExited) { $script:atlasPlanProcess.Kill($true); $script:atlasPlanProcess.WaitForExit(3000) | Out-Null } } catch { }
            $script:atlasPlanProcess.Dispose()
            $script:atlasPlanProcess = $null
        }
    }
}
function Add-AtlasPlanNodes {
    param($Node,[Collections.Generic.List[object]]$Items)
    $atlasSummary = [ordered]@{
        nodeType = $Node.'Node Type'; relation = $Node.'Relation Name'; index = $Node.'Index Name'
        estimatedRows = $Node.'Plan Rows'; estimatedWidth = $Node.'Plan Width'
        startupCost = $Node.'Startup Cost'; totalCost = $Node.'Total Cost'
        scanDirection = $Node.'Scan Direction'; joinType = $Node.'Join Type'
        sortKeyCount = @($Node.'Sort Key' | Where-Object { $_ -is [string] }).Count
    }
    $Items.Add([pscustomobject]$atlasSummary)
    foreach ($atlasChild in @($Node.Plans)) { if ($null -ne $atlasChild) { Add-AtlasPlanNodes -Node $atlasChild -Items $Items } }
}

try {
    Assert-AtlasPlan ($env:PGDATABASE -eq 'atlas') 'ATLAS_PLAN_TARGET_DATABASE_REQUIRED'
    Assert-AtlasPlan ($env:PGHOST -in @('127.0.0.1','localhost')) 'ATLAS_PLAN_LOOPBACK_REQUIRED'
    Assert-AtlasPlan ($env:PGPORT -match '^[0-9]{1,5}$' -and [int]$env:PGPORT -ge 1 -and [int]$env:PGPORT -le 65535) 'ATLAS_PLAN_PORT_REQUIRED'
    Assert-AtlasPlan (-not [string]::IsNullOrWhiteSpace($env:PGUSER)) 'ATLAS_PLAN_PGUSER_REQUIRED'
    Assert-AtlasPlan (Test-Path -LiteralPath $PsqlPath -PathType Leaf) 'ATLAS_PLAN_PSQL_REQUIRED'
    Assert-AtlasPlan (-not (Test-Path -LiteralPath $atlasPlanOutput)) 'ATLAS_PLAN_OUTPUT_MUST_BE_NEW'
    $atlasManifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    Assert-AtlasPlan ($atlasManifest.version -eq 'atlas-g6-v1' -and $atlasManifest.configuredPatients -eq 100 -and $atlasManifest.configuredObservations -eq 10000) 'ATLAS_PLAN_MANIFEST_REQUIRED'
    foreach ($atlasKey in @('p000','p006','p010')) {
        $atlasPatient = $atlasManifest.patients.$atlasKey
        Assert-AtlasPlan ($atlasPatient.id -match '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' -and $atlasPatient.actorId -eq 'atlas-g6-v1-clinician-a') 'ATLAS_PLAN_MAPPING_REQUIRED'
    }
    $atlasClinicalPath = Join-Path $RepositoryRoot 'src\main\java\com\aldercrest\atlas\results\ClinicalViewRepository.java'
    $atlasLogPath = Join-Path $RepositoryRoot 'src\main\java\com\aldercrest\atlas\results\AccessLogRepository.java'
    $atlasClinicalSource = Get-Content -LiteralPath $atlasClinicalPath -Raw
    $atlasLogSource = Get-Content -LiteralPath $atlasLogPath -Raw
    $atlasAccess = Convert-AtlasParameters (Get-AtlasSqlBlock $atlasClinicalSource 'List<LocalDate> births=jdbc.query(') 7
    $atlasView = Convert-AtlasParameters (Get-AtlasSqlBlock $atlasClinicalSource 'List<Candidate> candidates=jdbc.query(') 4
    $atlasPage = Convert-AtlasParameters (Get-AtlasSqlBlock $atlasLogSource 'List<Map<String,Object>> rows=jdbc.query(') 3
    $atlasCountLiteral = 'SELECT count(*) FROM access_log WHERE patient_id=?'
    Assert-AtlasPlan ($atlasLogSource.Contains('"' + $atlasCountLiteral + '"')) 'ATLAS_PLAN_COUNT_SOURCE_CHANGED'
    $atlasCount = Convert-AtlasParameters $atlasCountLiteral 1
    $atlasCases = [Collections.Generic.List[object]]::new()
    foreach ($atlasKey in @('p010','p006')) {
        $atlasId = $atlasManifest.patients.$atlasKey.id
        $atlasActor = $atlasManifest.patients.$atlasKey.actorId
        $atlasCases.Add([pscustomobject]@{ Label = ($atlasKey + '_clinical_access'); Sql = $atlasAccess; Types = 'text,text,text,text,text,text,text'; Bind = "'$atlasId','CLINICIAN','$atlasActor','CLINICIAN','$atlasActor','CLINICIAN','$atlasActor'"; Shape = 'Patient entitlement lookup for assigned clinician; SQL rights check, with age decision subsequently performed in Java.' })
        $atlasCases.Add([pscustomobject]@{ Label = ($atlasKey + '_clinical_view'); Sql = $atlasView; Types = 'text,boolean,integer,integer'; Bind = "'$atlasId',true,NULL,NULL"; Shape = 'Authorized full source history with review/encounter joins and exact-time order; list/detail/trend rendering and derivation subsequently performed in Java.' })
    }
    $atlasLogPatient = $atlasManifest.patients.p000.id
    $atlasCases.Add([pscustomobject]@{ Label = 'p000_log_count'; Sql = $atlasCount; Types = 'text'; Bind = "'$atlasLogPatient'"; Shape = 'Patient-scoped access-log count; planner only, not an executed count.' })
    $atlasCases.Add([pscustomobject]@{ Label = 'p000_log_page'; Sql = $atlasPage; Types = 'text,integer,integer'; Bind = "'$atlasLogPatient',50,0"; Shape = 'Patient-scoped first50 access-log entries ordered by time and id descending.' })
    New-Item -ItemType Directory -Path $atlasPlanOutput | Out-Null
    $atlasSummary = [ordered]@{
        schema = 'atlas-query-plan-evidence-v1'; status = 'RUNNING'; capturedAtUtc = [DateTime]::UtcNow.ToString('o')
        mode = 'EXPLAIN FORMAT JSON without ANALYZE; fresh prepared statements with session plan_cache_mode.'
        performsClinicalRows = $false; publishesIdentifiers = $false; queries = @()
        sources = @(@{ file = 'ClinicalViewRepository.java'; sha256 = (Get-FileHash -LiteralPath $atlasClinicalPath -Algorithm SHA256).Hash.ToLowerInvariant() }, @{ file = 'AccessLogRepository.java'; sha256 = (Get-FileHash -LiteralPath $atlasLogPath -Algorithm SHA256).Hash.ToLowerInvariant() })
        limits = 'One psql child at a time; read-only transactions; statement5s, lock1s, process15s.'
        interpretation = 'Planner nodes, estimates and cost units are not observed SQL latency. Current fresh prepared plans do not prove the warmed app uses the identical custom/generic plan. SQL extraction is source matched; bound values and full plans stay in ignored private evidence.'
    }
    foreach ($atlasCase in $atlasCases) {
        $atlasSql = "BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;`nSET LOCAL statement_timeout='5s';`nSET LOCAL lock_timeout='1s';`nSET LOCAL application_name='atlas-g11-query-plan';`nPREPARE atlas_g11_query(" + $atlasCase.Types + ") AS`n" + $atlasCase.Sql + ";`nEXPLAIN (FORMAT JSON) EXECUTE atlas_g11_query(" + $atlasCase.Bind + ");`nROLLBACK;`n"
        [IO.File]::WriteAllText((Join-Path $atlasPlanOutput ($atlasCase.Label + '.private.sql')),$atlasSql)
        $atlasPlanText = Invoke-AtlasPlanPsql $atlasSql
        $atlasPlan = $atlasPlanText | ConvertFrom-Json
        Assert-AtlasPlan ($null -ne $atlasPlan[0].Plan) 'ATLAS_PLAN_RESULT_INVALID'
        [IO.File]::WriteAllText((Join-Path $atlasPlanOutput ($atlasCase.Label + '.private.json')),$atlasPlanText)
        $atlasNodes = [Collections.Generic.List[object]]::new()
        Add-AtlasPlanNodes -Node $atlasPlan[0].Plan -Items $atlasNodes
        $atlasSummary.queries += [pscustomobject]@{ case = $atlasCase.Label; workShape = $atlasCase.Shape; nodes = $atlasNodes.ToArray() }
        Write-Output ('ATLAS_QUERY_PLAN_CAPTURED ' + $atlasCase.Label)
    }
    $atlasCatalogSql = @'
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='1s';
SELECT json_build_object('serverVersion',current_setting('server_version'),'planCacheMode',current_setting('plan_cache_mode'),'connectionRole',current_user,'database',current_database(),'capturedAtUtc',CURRENT_TIMESTAMP,'indexes',COALESCE((SELECT json_agg(json_build_object('schema',schemaname,'table',tablename,'name',indexname,'definition',indexdef) ORDER BY tablename,indexname) FROM pg_indexes WHERE schemaname='atlas' AND tablename IN ('patient','encounter','proxy_link','observation','observation_review','access_log')),'[]'::json));
ROLLBACK;
'@
    $atlasCatalogText = Invoke-AtlasPlanPsql $atlasCatalogSql
    $atlasCatalog = $atlasCatalogText | ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $atlasPlanOutput 'catalog.private.json'),$atlasCatalogText)
    $atlasSummary.serverVersion = $atlasCatalog.serverVersion
    $atlasSummary.planCacheMode = $atlasCatalog.planCacheMode
    $atlasSummary.indexes = @($atlasCatalog.indexes | ForEach-Object { [pscustomobject]@{ table = $_.table; name = $_.name; definition = $_.definition } })
    $atlasSummary.status = 'COMPLETED'
    $atlasSummary.completedAtUtc = [DateTime]::UtcNow.ToString('o')
    $atlasSummary | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $atlasPlanOutput 'summary.sanitized.json') -Encoding utf8
    Write-Output 'ATLAS_QUERY_PLAN_FINISHED COMPLETED'
    exit 0
} catch {
    # Do not expose raw psql/source/connection diagnostics or actual bound identifiers.
    Write-Output 'ATLAS_QUERY_PLAN_FINISHED FAILED'
    exit 2
} finally {
    if ($null -ne $atlasPlanProcess) { try { if (-not $atlasPlanProcess.HasExited) { $atlasPlanProcess.Kill($true) } } catch { }; $atlasPlanProcess.Dispose() }
}
