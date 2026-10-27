[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$HttpPort=18101,[switch]$SkipHttpChecks)
# Install at scripts/verify-restore.ps1. Root/operator must quiesce source writes first.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
$atlasRestoreStarted=[DateTime]::UtcNow
$atlasRestoreClock=[System.Diagnostics.Stopwatch]::StartNew()
$atlasRestoreName='atlas_restore_'+$atlasRestoreStarted.ToString('yyyyMMddTHHmmss').ToLowerInvariant()+'_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
if($atlasRestoreName -notmatch '^atlas_restore_[0-9]{8}t[0-9]{6}_[0-9a-f]{8}$' -or $atlasRestoreName -in @('atlas','atlas_test','postgres','template0','template1')) {throw 'ATLAS_RESTORE_UNSAFE_TARGET'}
$atlasRestoreDirectory=Join-Path $AtlasRoot ('work\restore\'+$atlasRestoreName)
New-Item -ItemType Directory -Path $atlasRestoreDirectory -ErrorAction Stop | Out-Null
$atlasRestoreBackup=Join-Path $atlasRestoreDirectory 'atlas.dump'
$atlasRestoreReportPath=Join-Path $atlasRestoreDirectory 'report.json'
$atlasRestoreUtf8=[System.Text.UTF8Encoding]::new($false)
$atlasRestoreSecrets=$null
$atlasRestoreServer=$null
$atlasRestoreHttp=$null
$atlasRestoreTargetCreated=$false
$atlasRestoreFailure=$null
$atlasRestoreReport=[ordered]@{schemaVersion=1;status='RUNNING';sourceDatabase='atlas';restoredDatabase=$atlasRestoreName;startedAtUtc=$atlasRestoreStarted.ToString('o');sourceQuiescence='Operator must stop source writers; before/after canonical inventories must agree.';httpChecks=@();limits=@('Small synthetic cohort; no approved RPO/RTO or production-scale recovery claim.','Row MD5 is accidental-mutation equality evidence; dump integrity uses SHA-256.','Restored HTTP reads intentionally append audit rows after exact pre-read equality checks.','No source database reset/drop/stop; restored DB and ignored backup remain for inspection.')}

function New-AtlasRestoreProcess {
    param([string]$Executable,[string[]]$Arguments,[hashtable]$ChildEnvironment=@{},[switch]$InputRequired)
    $info=[System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$Executable;$info.WorkingDirectory=$AtlasRoot;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.RedirectStandardInput=[bool]$InputRequired
    foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
    foreach($name in @('PGPASSWORD','PGPASSFILE','PGSERVICE','PGSERVICEFILE','PGHOST','PGPORT','PGUSER','PGDATABASE','PGOPTIONS','PGSSLMODE','ATLAS_MIGRATION_USER','ATLAS_MIGRATION_PASSWORD','ATLAS_DB_URL','ATLAS_DB_USER','ATLAS_DB_PASSWORD','SPRING_APPLICATION_JSON','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD')){$info.Environment.Remove($name)|Out-Null}
    foreach($name in $ChildEnvironment.Keys){$info.Environment[$name]=[string]$ChildEnvironment[$name]}
    $process=[System.Diagnostics.Process]::new();$process.StartInfo=$info
    if(-not $process.Start()){throw 'ATLAS_RESTORE_CHILD_START_FAILED'}
    return [pscustomobject]@{Process=$process;Stdout=$process.StandardOutput.ReadToEndAsync();Stderr=$process.StandardError.ReadToEndAsync()}
}

function Invoke-AtlasRestoreTool {
    param([string]$Label,[string]$Executable,[string[]]$Arguments,[hashtable]$ChildEnvironment=@{},[string]$InputText,[int]$BudgetSeconds=120)
    $record=$null
    try{
        $record=New-AtlasRestoreProcess -Executable $Executable -Arguments $Arguments -ChildEnvironment $ChildEnvironment -InputRequired:($null -ne $InputText)
        if($null -ne $InputText){$record.Process.StandardInput.Write($InputText);$record.Process.StandardInput.Close()}
        if(-not $record.Process.WaitForExit($BudgetSeconds*1000)){$record.Process.Kill($true);$record.Process.WaitForExit(5000)|Out-Null;throw ('ATLAS_RESTORE_TIMEOUT_'+$Label)}
        $stdout=$record.Stdout.GetAwaiter().GetResult();$stderr=$record.Stderr.GetAwaiter().GetResult()
        if($record.Process.ExitCode -ne 0){
            foreach($purpose in @('admin','migrate','runtime')){$secret=[string]$atlasRestoreSecrets.$purpose;if($secret){$stderr=$stderr.Replace($secret,'[REDACTED]')}}
            [IO.File]::WriteAllText((Join-Path $atlasRestoreDirectory ('failed-'+$Label+'-'+[Guid]::NewGuid().ToString('N')+'.stderr.log')),$stderr,$atlasRestoreUtf8)
            throw ('ATLAS_RESTORE_TOOL_FAILED_'+$Label)
        }
        return $stdout
    }finally{
        if($null -ne $record){
            if(-not $record.Process.HasExited){$record.Process.Kill($true);$record.Process.WaitForExit(5000)|Out-Null}
            $record.Process.Dispose()
        }
    }
}

function Get-AtlasRestorePgEnvironment {
    param([ValidateSet('admin','migrate')][string]$Purpose,[string]$Database)
    $user=if($Purpose -eq 'admin'){'atlas_admin'}else{'atlas_migrate'}
    return @{PGHOST='127.0.0.1';PGPORT='55433';PGUSER=$user;PGDATABASE=$Database;PGPASSWORD=[string]$atlasRestoreSecrets.$Purpose;PGCONNECT_TIMEOUT='5';PGTZ='UTC';PGOPTIONS='-c statement_timeout=60000 -c lock_timeout=3000 -c idle_in_transaction_session_timeout=60000'}
}

function Invoke-AtlasRestoreSql {
    param([string]$Database,[string]$Sql,[ValidateSet('admin','migrate')][string]$Purpose='migrate')
    return Invoke-AtlasRestoreTool -Label 'SQL' -Executable (Join-Path $AtlasPgBin 'psql.exe') -Arguments @('-X','-q','-A','-t','-v','ON_ERROR_STOP=1') -ChildEnvironment (Get-AtlasRestorePgEnvironment $Purpose $Database) -InputText ($Sql+"`n")
}

function Get-AtlasRestoreTextSha256 {
    param([string]$Text)
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($Text))).ToLowerInvariant()
}

function Get-AtlasRestoreInventory {
    param([string]$Database)
    $namesText=Invoke-AtlasRestoreSql $Database "SELECT tablename FROM pg_tables WHERE schemaname='atlas' ORDER BY tablename COLLATE `"C`";"
    $tables=@($namesText.Trim() -split "`r?`n" | Where-Object {$_})
    if($tables.Count -lt 5){throw 'ATLAS_RESTORE_TABLE_INVENTORY_INCOMPLETE'}
    $sql=[System.Collections.Generic.List[string]]::new()
    $sql.Add("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY; SET LOCAL TIME ZONE 'UTC'; SET LOCAL search_path=pg_catalog;")
    foreach($table in $tables){
        if($table -notmatch '^[A-Za-z_][A-Za-z0-9_]*$'){throw 'ATLAS_RESTORE_UNSUPPORTED_TABLE_NAME'}
        $sql.Add("SELECT jsonb_build_object('kind','table','table','$table','rows',count(*),'rowMd5',md5(COALESCE(string_agg(to_jsonb(t)::text,E'\n' ORDER BY to_jsonb(t)::text COLLATE `"C`"),'')))::text FROM atlas.`"$table`" t;")
    }
    $sql.Add(@'
WITH pieces AS (
 SELECT 'C:'||table_name||':'||column_name||':'||ordinal_position||':'||data_type||':'||udt_name||':'||is_nullable||':'||COALESCE(column_default,'')||':'||COALESCE(is_identity,'') AS piece
 FROM information_schema.columns WHERE table_schema='atlas'
 UNION ALL SELECT 'K:'||c.conrelid::regclass::text||':'||c.conname||':'||pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='atlas'
 UNION ALL SELECT 'I:'||indexname||':'||indexdef FROM pg_indexes WHERE schemaname='atlas'
 UNION ALL SELECT 'T:'||table_name||':'||grantor||':'||grantee||':'||privilege_type||':'||is_grantable FROM information_schema.table_privileges WHERE table_schema='atlas'
 UNION ALL SELECT 'P:'||table_name||':'||column_name||':'||grantor||':'||grantee||':'||privilege_type||':'||is_grantable FROM information_schema.column_privileges WHERE table_schema='atlas'
)
SELECT jsonb_build_object('kind','schema','metadataMd5',md5(COALESCE(string_agg(piece,E'\n' ORDER BY piece COLLATE "C"),'')))::text FROM pieces;
SELECT jsonb_build_object('kind','sequences','data',COALESCE(jsonb_agg(to_jsonb(s) ORDER BY sequencename),'[]'::jsonb))::text FROM pg_sequences s WHERE schemaname='atlas';
COMMIT;
'@)
    $raw=Invoke-AtlasRestoreSql $Database ($sql -join "`n")
    $lines=@($raw.Trim() -split "`r?`n" | Where-Object {$_})
    $records=@($lines | ForEach-Object {$_ | ConvertFrom-Json})
    return [pscustomobject]@{summarySha256=(Get-AtlasRestoreTextSha256 ($lines -join "`n"));records=$records;tableCount=$tables.Count}
}

function Assert-AtlasRestoreEqual {
    param($Left,$Right,[string]$Code)
    if($Left.summarySha256 -ne $Right.summarySha256){throw ('ATLAS_RESTORE_MISMATCH_'+$Code)}
}

function Invoke-AtlasRestoredGet {
    param([string]$Patient,[string]$Actor,[string]$Role,[int]$ExpectedStatus,[Nullable[int]]$ExpectedTotal)
    $request=[System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get,"http://127.0.0.1:$HttpPort/patients/$Patient/results?offset=0&limit=200")
    $response=$null
    try{
        $request.Headers.Add('X-Test-Actor-Id',$Actor);$request.Headers.Add('X-Test-Actor-Role',$Role)
        $response=$atlasRestoreHttp.SendAsync($request).GetAwaiter().GetResult()
        $body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if([int]$response.StatusCode -ne $ExpectedStatus -or -not $response.Headers.CacheControl.NoStore){throw 'ATLAS_RESTORE_HTTP_STATUS_OR_CACHE_FAILED'}
        $parsed=$body | ConvertFrom-Json
        if($null -ne $ExpectedTotal -and [int]$parsed.total -ne $ExpectedTotal){throw 'ATLAS_RESTORE_HTTP_VISIBLE_TOTAL_FAILED'}
        if($ExpectedStatus -ne 200 -and ($parsed.resourceType -ne 'OperationOutcome' -or $body.Contains($Patient))){throw 'ATLAS_RESTORE_HTTP_DENIAL_FAILED'}
        return $parsed
    }finally{if($null -ne $response){$response.Dispose()};$request.Dispose()}
}

try{
    if(-not $IsWindows){throw 'ATLAS_RESTORE_WINDOWS_TOOLS_REQUIRED'}
    $secretPath=Join-Path $AtlasLocal 'secrets\database.json'
    if(-not (Test-Path -LiteralPath $secretPath)){throw 'ATLAS_RESTORE_EXISTING_LOCAL_CONFIG_REQUIRED'}
    $atlasRestoreSecrets=Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json
    foreach($purpose in @('admin','migrate','runtime')){if([string]$atlasRestoreSecrets.$purpose -notmatch '^[0-9a-f]{64}$'){throw 'ATLAS_RESTORE_INVALID_LOCAL_CONFIG'}}
    $sourceInfo=(Invoke-AtlasRestoreSql 'atlas' "SELECT json_build_object('version',version(),'versionNum',current_setting('server_version_num')::int,'database',current_database(),'user',current_user,'superuser',(SELECT rolsuper FROM pg_roles WHERE rolname=current_user),'collate',(SELECT datcollate FROM pg_database WHERE datname=current_database()),'ctype',(SELECT datctype FROM pg_database WHERE datname=current_database()),'encoding',current_setting('server_encoding'));") | ConvertFrom-Json
    if([math]::Floor([int]$sourceInfo.versionNum/10000) -ne 16 -or $sourceInfo.database -ne 'atlas' -or $sourceInfo.user -ne 'atlas_migrate' -or $sourceInfo.superuser -or $sourceInfo.encoding -ne 'UTF8'){throw 'ATLAS_RESTORE_SOURCE_GUARD_FAILED'}
    $atlasRestoreReport.server=$sourceInfo
    $dumpVersion=(Invoke-AtlasRestoreTool -Label 'DUMP_VERSION' -Executable (Join-Path $AtlasPgBin 'pg_dump.exe') -Arguments @('--version')).Trim()
    $restoreVersion=(Invoke-AtlasRestoreTool -Label 'RESTORE_VERSION' -Executable (Join-Path $AtlasPgBin 'pg_restore.exe') -Arguments @('--version')).Trim()
    if($dumpVersion -notmatch 'PostgreSQL\) 16\.' -or $restoreVersion -notmatch 'PostgreSQL\) 16\.'){throw 'ATLAS_RESTORE_TOOL_VERSION_GUARD_FAILED'}
    $atlasRestoreReport.tools=@{pgDump=$dumpVersion;pgRestore=$restoreVersion}
    Write-Output 'ATLAS_RESTORE_STAGE SOURCE_INVENTORY'
    $sourceBefore=Get-AtlasRestoreInventory 'atlas';$atlasRestoreReport.sourceBefore=$sourceBefore
    $dumpStarted=[DateTime]::UtcNow
    $null=Invoke-AtlasRestoreTool -Label 'DUMP' -Executable (Join-Path $AtlasPgBin 'pg_dump.exe') -Arguments @('--format=custom','--file',$atlasRestoreBackup,'--dbname','atlas','--no-password') -ChildEnvironment (Get-AtlasRestorePgEnvironment 'migrate' 'atlas') -BudgetSeconds 180
    $atlasRestoreReport.backup=[ordered]@{file='atlas.dump';format='PostgreSQL custom';sha256=(Get-FileHash -LiteralPath $atlasRestoreBackup -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $atlasRestoreBackup).Length;startedAtUtc=$dumpStarted.ToString('o');finishedAtUtc=[DateTime]::UtcNow.ToString('o')}
    $sourceAfterDump=Get-AtlasRestoreInventory 'atlas';$atlasRestoreReport.sourceAfterDump=$sourceAfterDump;Assert-AtlasRestoreEqual $sourceBefore $sourceAfterDump 'SOURCE_CHANGED_DURING_DUMP'
    $exists=Invoke-AtlasRestoreSql 'postgres' "SELECT count(*) FROM pg_database WHERE datname='$atlasRestoreName';" 'admin'
    if($exists.Trim() -ne '0'){throw 'ATLAS_RESTORE_TARGET_ALREADY_EXISTS'}
    Write-Output 'ATLAS_RESTORE_STAGE CREATE_FRESH_TARGET'
    $null=Invoke-AtlasRestoreTool -Label 'CREATEDB' -Executable (Join-Path $AtlasPgBin 'createdb.exe') -Arguments @('--owner','atlas_migrate','--template','template0','--encoding','UTF8','--lc-collate',[string]$sourceInfo.collate,'--lc-ctype',[string]$sourceInfo.ctype,'--no-password',$atlasRestoreName) -ChildEnvironment (Get-AtlasRestorePgEnvironment 'admin' 'postgres')
    $atlasRestoreTargetCreated=$true
    # Database ACL and per-database role settings are not carried by an ordinary no-create dump.
    $null=Invoke-AtlasRestoreSql $atlasRestoreName "REVOKE ALL ON DATABASE $atlasRestoreName FROM PUBLIC; GRANT CONNECT ON DATABASE $atlasRestoreName TO atlas_migrate,atlas_runtime; REVOKE ALL ON SCHEMA public FROM PUBLIC; ALTER ROLE atlas_runtime IN DATABASE $atlasRestoreName SET search_path=atlas,pg_catalog; ALTER ROLE atlas_runtime IN DATABASE $atlasRestoreName SET statement_timeout='10s'; ALTER ROLE atlas_runtime IN DATABASE $atlasRestoreName SET lock_timeout='3s'; ALTER ROLE atlas_runtime IN DATABASE $atlasRestoreName SET idle_in_transaction_session_timeout='15s';" 'admin'
    Write-Output 'ATLAS_RESTORE_STAGE RESTORE'
    $restoreStarted=[DateTime]::UtcNow
    $null=Invoke-AtlasRestoreTool -Label 'PGRESTORE' -Executable (Join-Path $AtlasPgBin 'pg_restore.exe') -Arguments @('--exit-on-error','--single-transaction','--no-owner','--no-password','--dbname',$atlasRestoreName,$atlasRestoreBackup) -ChildEnvironment (Get-AtlasRestorePgEnvironment 'migrate' $atlasRestoreName) -BudgetSeconds 180
    $atlasRestoreReport.restore=[ordered]@{startedAtUtc=$restoreStarted.ToString('o');finishedAtUtc=[DateTime]::UtcNow.ToString('o');seconds=([DateTime]::UtcNow-$restoreStarted).TotalSeconds;targetCreatedFresh=$true;owner='atlas_migrate'}
    $restoredBeforeReads=Get-AtlasRestoreInventory $atlasRestoreName;Assert-AtlasRestoreEqual $sourceBefore $restoredBeforeReads 'RESTORED_CONTENT_OR_SCHEMA'
    $atlasRestoreReport.restoredBeforeHttp=$restoredBeforeReads
    $sourceAfterRestore=Get-AtlasRestoreInventory 'atlas';Assert-AtlasRestoreEqual $sourceBefore $sourceAfterRestore 'SOURCE_CHANGED_DURING_RESTORE'
    $atlasRestoreReport.sourceAfterRestore=$sourceAfterRestore
    if(-not $SkipHttpChecks){
        Write-Output 'ATLAS_RESTORE_STAGE RESTORED_HTTP'
        $jar=Join-Path $AtlasRoot 'target\atlas-0.1.0-SNAPSHOT.jar'
        if(-not (Test-Path -LiteralPath $jar)){throw 'ATLAS_RESTORE_EXISTING_VERIFIED_JAR_REQUIRED'}
        $manifest=Get-Content -LiteralPath (Join-Path $AtlasRoot 'work\dataset\v1\load-manifest.json') -Raw | ConvertFrom-Json
        if($manifest.version -ne 'atlas-g6-v1'){throw 'ATLAS_RESTORE_DATASET_VERSION_UNSUPPORTED'}
        $p000=[string]$manifest.patients.p000.id;$clinician=[string]$manifest.patients.p000.actorId;$p008=[string]$manifest.patients.p008.id
        foreach($id in @($p000,$p008)){if($id -notmatch '^[0-9a-f-]{36}$'){throw 'ATLAS_RESTORE_INVALID_FIXTURE_ID'}}
        if($clinician -notmatch '^[A-Za-z0-9._-]{1,64}$'){throw 'ATLAS_RESTORE_INVALID_FIXTURE_ACTOR'}
        $guard=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$HttpPort)
        try{$guard.Start()}finally{$guard.Stop()}
        $url="jdbc:postgresql://127.0.0.1:55433/${atlasRestoreName}?connectTimeout=5&socketTimeout=20&ApplicationName=atlas-restore"
        $runtimeEnvironment=@{ATLAS_DB_URL=$url;ATLAS_DB_USER='atlas_runtime';ATLAS_DB_PASSWORD=[string]$atlasRestoreSecrets.runtime;ATLAS_HTTP_PORT=[string]$HttpPort;SPRING_DATASOURCE_URL=$url;SPRING_DATASOURCE_USERNAME='atlas_runtime';SPRING_DATASOURCE_PASSWORD=[string]$atlasRestoreSecrets.runtime}
        $atlasRestoreServer=New-AtlasRestoreProcess -Executable $AtlasJava -Arguments @('-Duser.timezone=UTC','-jar',$jar) -ChildEnvironment $runtimeEnvironment
        $atlasRestoreReport.applicationJarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        $atlasRestoreHttp=[System.Net.Http.HttpClient]::new();$atlasRestoreHttp.Timeout=[TimeSpan]::FromSeconds(30)
        $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(120)
        while([DateTime]::UtcNow -lt $deadline){
            if($atlasRestoreServer.Process.HasExited){throw 'ATLAS_RESTORE_HTTP_PROCESS_EXITED'}
            try{$probe=$atlasRestoreHttp.GetAsync("http://127.0.0.1:$HttpPort/").GetAwaiter().GetResult();$ready=$probe.IsSuccessStatusCode;$probe.Dispose()}catch{}
            if($ready){break};Start-Sleep -Milliseconds 500
        }
        if(-not $ready){throw 'ATLAS_RESTORE_HTTP_START_TIMEOUT'}
        $beforeAudit=(Invoke-AtlasRestoreSql $atlasRestoreName "SELECT json_build_object('count',count(*),'maxId',COALESCE(max(id),0)) FROM atlas.access_log;") | ConvertFrom-Json
        $restoredPage=Invoke-AtlasRestoredGet $p000 $clinician 'CLINICIAN' 200 5
        $creatinines=@($restoredPage.items | Where-Object {$_.code -eq '2160-0'})
        $egfrs=@($restoredPage.items | Where-Object {$_.code -eq '98979-8'})
        if($creatinines.Count -ne 2 -or @($creatinines | Where-Object {$_.unitCode -ne 'mg/dL' -or [decimal]$_.value -ne 1}).Count -ne 0 -or $egfrs.Count -ne 2 -or [decimal]$egfrs[0].value -le 0 -or [decimal]$egfrs[0].value -ne [decimal]$egfrs[1].value){throw 'ATLAS_RESTORE_CANONICAL_OR_DERIVED_CHECK_FAILED'}
        $null=Invoke-AtlasRestoredGet $p000 'atlas-g6-v1-staff' 'STAFF' 404 $null
        $null=Invoke-AtlasRestoredGet $p008 'atlas-g6-v1-proxy-retained' 'PROXY' 200 1
        $null=Invoke-AtlasRestoredGet $p008 'atlas-g6-v1-proxy-revoked' 'PROXY' 404 $null
        $newAudit=(Invoke-AtlasRestoreSql $atlasRestoreName "SELECT COALESCE(json_agg(json_build_object('actor',actor_id,'role',actor_role,'patient',patient_id,'action',action,'outcome',outcome,'source',source,'verified',actor_roster_verified) ORDER BY id),'[]') FROM atlas.access_log WHERE id>$($beforeAudit.maxId);") | ConvertFrom-Json
        $expected=@(@($clinician,'CLINICIAN',$p000,'ALLOWED'),@('atlas-g6-v1-staff','STAFF',$p000,'DENIED'),@('atlas-g6-v1-proxy-retained','PROXY',$p008,'ALLOWED'),@('atlas-g6-v1-proxy-revoked','PROXY',$p008,'DENIED'))
        if(@($newAudit).Count -ne 4){throw 'ATLAS_RESTORE_HTTP_AUDIT_COUNT_FAILED'}
        for($index=0;$index -lt 4;$index++){
            $row=$newAudit[$index];$wanted=$expected[$index]
            if($row.actor -ne $wanted[0] -or $row.role -ne $wanted[1] -or $row.patient -ne $wanted[2] -or $row.outcome -ne $wanted[3] -or $row.action -ne 'RESULT_LIST' -or $row.source -ne 'HTTP' -or -not $row.verified){throw 'ATLAS_RESTORE_HTTP_AUDIT_ATTRIBUTION_FAILED'}
        }
        $restoredAfterReads=Get-AtlasRestoreInventory $atlasRestoreName
        foreach($table in @($restoredBeforeReads.records | Where-Object {$_.kind -eq 'table' -and $_.table -ne 'access_log'})){
            $after=@($restoredAfterReads.records | Where-Object {$_.kind -eq 'table' -and $_.table -eq $table.table})
            if($after.Count -ne 1 -or $after[0].rows -ne $table.rows -or $after[0].rowMd5 -ne $table.rowMd5){throw 'ATLAS_RESTORE_HTTP_MUTATED_CLINICAL_STATE'}
        }
        $atlasRestoreReport.httpChecks=@('Clinician p000 list:5rows, two canonical1mg/dL creatinine and two equal positive eGFR views.','STAFF clinical list denied404.','Retained individual proxy list:1visible row.','Revoked proxy list denied404.','All four requests persisted exact attributable allowed/denied audits.','All non-audit table counts/content unchanged after restored HTTP reads.')
        $atlasRestoreReport.httpAuditRowsAdded=4
        $atlasRestoreReport.restoredAfterHttp=$restoredAfterReads
    }else{$atlasRestoreReport.httpChecks=@('SKIPPED explicitly; no restored-application allowed/denied-read proof is claimed.')}
    $sourceFinal=Get-AtlasRestoreInventory 'atlas';Assert-AtlasRestoreEqual $sourceBefore $sourceFinal 'SOURCE_CHANGED_DURING_HTTP'
    $atlasRestoreReport.sourceFinal=$sourceFinal
    $atlasRestoreReport.status='PASS'
}catch{
    $atlasRestoreReport.status='FAIL'
    $atlasRestoreFailure=if($_.Exception.Message -match '^ATLAS_RESTORE_[A-Z_]+$'){$_.Exception.Message}else{'ATLAS_RESTORE_ACTION_FAILED'}
    $atlasRestoreReport.failure=$atlasRestoreFailure
}finally{
    if($null -ne $atlasRestoreHttp){$atlasRestoreHttp.Dispose()}
    if($null -ne $atlasRestoreServer){
        try{
            if(-not $atlasRestoreServer.Process.HasExited){$atlasRestoreServer.Process.Kill($false);if(-not $atlasRestoreServer.Process.WaitForExit(10000)){throw 'ATLAS_RESTORE_OWNED_HTTP_STOP_FAILED'}}
            $out=$atlasRestoreServer.Stdout.GetAwaiter().GetResult();$err=$atlasRestoreServer.Stderr.GetAwaiter().GetResult()
            foreach($purpose in @('admin','migrate','runtime')){$secret=[string]$atlasRestoreSecrets.$purpose;if($secret){$out=$out.Replace($secret,'[REDACTED]');$err=$err.Replace($secret,'[REDACTED]')}}
            [System.IO.File]::WriteAllText((Join-Path $atlasRestoreDirectory 'application.stdout.log'),$out,$atlasRestoreUtf8)
            [System.IO.File]::WriteAllText((Join-Path $atlasRestoreDirectory 'application.stderr.log'),$err,$atlasRestoreUtf8)
        }catch{$atlasRestoreReport.status='FAIL';$atlasRestoreFailure='ATLAS_RESTORE_OWNED_HTTP_CLEANUP_FAILED';$atlasRestoreReport.failure=$atlasRestoreFailure}
        finally{$atlasRestoreServer.Process.Dispose()}
    }
    Remove-Item Env:\PGPASSWORD,Env:\PGPASSFILE,Env:\PGSERVICEFILE,Env:\ATLAS_DB_PASSWORD,Env:\ATLAS_MIGRATION_PASSWORD,Env:\ATLAS_MIGRATION_USER -ErrorAction SilentlyContinue
    $atlasRestoreSecrets=$null
    $atlasRestoreClock.Stop();$atlasRestoreReport.finishedAtUtc=[DateTime]::UtcNow.ToString('o');$atlasRestoreReport.elapsedSeconds=$atlasRestoreClock.Elapsed.TotalSeconds;$atlasRestoreReport.targetCreated=$atlasRestoreTargetCreated
    [System.IO.File]::WriteAllText($atlasRestoreReportPath,(($atlasRestoreReport|ConvertTo-Json -Depth 15).Replace("`r`n","`n")+"`n"),$atlasRestoreUtf8)
}
if($atlasRestoreReport.status -ne 'PASS'){throw $atlasRestoreFailure}
Write-Output ('ATLAS_RESTORE_PASS '+$atlasRestoreReportPath)
