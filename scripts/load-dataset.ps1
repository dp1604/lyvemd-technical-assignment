[CmdletBinding()]
param(
    # Internal child phases let the parent bound existing build/bootstrap commands without another script.
    [ValidateSet('Load','Prepare','Build','Migrate')][string]$InternalPhase = 'Load',
    # Investigated rejection only: preserve its receipt and permit one explicit new attempt key.
    [switch]$RevalidateRejected
)
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')

if ($InternalPhase -ne 'Load') {
    try {
        switch ($InternalPhase) {
            'Prepare' { & (Join-Path $PSScriptRoot 'prepare-local-database.ps1') }
            'Build' { Invoke-AtlasMaven -Goals @('package') }
            'Migrate' { Set-AtlasDatabaseEnvironment -Database atlas; Invoke-AtlasMigration }
        }
        exit 0
    } catch {
        Write-Output 'ATLAS_DATASET_STAGE INTERNAL_ACTION_FAILED'
        exit 2
    } finally {
        Clear-AtlasDatabaseEnvironment
        Remove-Item Env:\PGPASSWORD,Env:\PGPASSFILE,Env:\PGSERVICEFILE -ErrorAction SilentlyContinue
    }
}

$atlasDatasetDirectory = Join-Path $AtlasRoot 'work\dataset\v1'
New-Item -ItemType Directory -Force -Path $atlasDatasetDirectory | Out-Null
$atlasDatasetLogDirectory = Join-Path $atlasDatasetDirectory 'logs'
New-Item -ItemType Directory -Force -Path $atlasDatasetLogDirectory | Out-Null
$atlasDatasetRun = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$atlasDatasetJar = Join-Path $AtlasRoot 'target\atlas-0.1.0-SNAPSHOT.jar'
$atlasDatasetBase = 'http://127.0.0.1:18091'
$atlasDatasetPwsh = (Get-Command pwsh -ErrorAction Stop).Source
$atlasDatasetOwned = [System.Collections.Generic.List[object]]::new()
$atlasDatasetLock = $null
$atlasDatasetMutex = $null
$atlasDatasetMutexHeld = $false
$atlasDatasetSecrets = $null
$script:atlasDatasetCleanupFailed = $false

function Write-AtlasDatasetStage {
    param([string]$Name)
    Write-Output ('ATLAS_DATASET_STAGE ' + $Name)
}

function New-AtlasDatasetProcess {
    param(
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments,
        [hashtable]$AdditionalEnvironment = @{},
        [switch]$OwnerFixture
    )
    $atlasInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $atlasInfo.FileName = $Executable
    $atlasInfo.WorkingDirectory = $AtlasRoot
    $atlasInfo.UseShellExecute = $false
    $atlasInfo.CreateNoWindow = $true
    $atlasInfo.RedirectStandardOutput = $true
    $atlasInfo.RedirectStandardError = $true
    foreach ($atlasArgument in $Arguments) { $atlasInfo.ArgumentList.Add($atlasArgument) }
    # Never inherit owner/bootstrap credentials into a serving or runtime tool process.
    foreach ($atlasName in @('ATLAS_MIGRATION_USER','ATLAS_MIGRATION_PASSWORD','PGPASSWORD','PGPASSFILE','PGSERVICEFILE')) {
        $atlasInfo.Environment.Remove($atlasName) | Out-Null
    }
    foreach ($atlasName in $AdditionalEnvironment.Keys) { $atlasInfo.Environment[$atlasName] = [string]$AdditionalEnvironment[$atlasName] }
    if ($OwnerFixture) {
        if ($null -eq $atlasDatasetSecrets) { throw 'ATLAS_DATASET_CREDENTIAL_SETUP_FAILED' }
        $atlasInfo.Environment['ATLAS_MIGRATION_USER'] = 'atlas_migrate'
        $atlasInfo.Environment['ATLAS_MIGRATION_PASSWORD'] = $atlasDatasetSecrets.migrate
    }
    $atlasStdoutPath = Join-Path $atlasDatasetLogDirectory ($atlasDatasetRun + '-' + $Label + '.stdout.log')
    $atlasStderrPath = Join-Path $atlasDatasetLogDirectory ($atlasDatasetRun + '-' + $Label + '.stderr.log')
    $atlasStdout = [System.IO.FileStream]::new($atlasStdoutPath,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::ReadWrite,1,$true)
    $atlasStderr = [System.IO.FileStream]::new($atlasStderrPath,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::ReadWrite,1,$true)
    $atlasProcess = [System.Diagnostics.Process]::new()
    $atlasProcess.StartInfo = $atlasInfo
    $atlasCaptureCancellation = [System.Threading.CancellationTokenSource]::new()
    $atlasStarted = $false
    # Preparation may have deliberately started the persistent assessment PostgreSQL server.
    # Never traverse that phase's descendant tree during timeout cleanup.
    $atlasKillTree = $Label -ne 'prepare'
    try {
        if (-not $atlasProcess.Start()) { throw 'ATLAS_DATASET_PROCESS_START_FAILED' }
        $atlasStarted = $true
        $atlasRecord = [pscustomobject]@{
            Label = $Label; Process = $atlasProcess; Stdout = $atlasStdout; Stderr = $atlasStderr
            StdoutPath = $atlasStdoutPath; StderrPath = $atlasStderrPath
            CaptureCancellation = $atlasCaptureCancellation
            KillTree = $atlasKillTree
            StdoutTask = $atlasProcess.StandardOutput.BaseStream.CopyToAsync($atlasStdout,81920,$atlasCaptureCancellation.Token)
            StderrTask = $atlasProcess.StandardError.BaseStream.CopyToAsync($atlasStderr,81920,$atlasCaptureCancellation.Token)
            Closed = $false
        }
        $atlasDatasetOwned.Add($atlasRecord)
        return $atlasRecord
    } catch {
        if ($atlasStarted -and -not $atlasProcess.HasExited) { try { $atlasProcess.Kill($atlasKillTree) } catch {} }
        $atlasStdout.Dispose(); $atlasStderr.Dispose(); $atlasCaptureCancellation.Dispose(); $atlasProcess.Dispose()
        throw 'ATLAS_DATASET_PROCESS_START_FAILED'
    }
}

function Close-AtlasDatasetProcess {
    param([Parameter(Mandatory)]$Record,[switch]$Stop)
    if ($Record.Closed) { return }
    try {
        if ($Stop -and -not $Record.Process.HasExited) {
            # Original Process handle only. Never stop by a port, executable name or discovered PID.
            $Record.Process.Kill([bool]$Record.KillTree)
            if (-not $Record.Process.WaitForExit(10000)) { throw 'ATLAS_DATASET_OWNED_PROCESS_STOP_FAILED' }
        }
        $atlasCaptures = [System.Threading.Tasks.Task]::WhenAll([System.Threading.Tasks.Task[]]@($Record.StdoutTask,$Record.StderrTask))
        if (-not $atlasCaptures.Wait(10000)) { $Record.CaptureCancellation.Cancel() }
    } catch {
        if ($Stop) {
            try { if (-not $Record.Process.HasExited) { $script:atlasDatasetCleanupFailed = $true } } catch { $script:atlasDatasetCleanupFailed = $true }
        }
        $Record.CaptureCancellation.Cancel()
    } finally {
        $Record.Stdout.Dispose(); $Record.Stderr.Dispose()
        $Record.CaptureCancellation.Dispose(); $Record.Process.Dispose(); $Record.Closed = $true
        # Defense in depth for existing preparation-tool error output: never retain a known local password.
        if ($null -ne $atlasDatasetSecrets) {
            foreach ($atlasLog in @($Record.StdoutPath,$Record.StderrPath)) {
                try {
                    $atlasLogText = [System.IO.File]::ReadAllText($atlasLog)
                    foreach ($atlasPurpose in @('admin','migrate','runtime')) {
                        $atlasSecretValue = [string]$atlasDatasetSecrets.$atlasPurpose
                        if ($atlasSecretValue) { $atlasLogText = $atlasLogText.Replace($atlasSecretValue,'[REDACTED]') }
                    }
                    [System.IO.File]::WriteAllText($atlasLog,$atlasLogText,[System.Text.UTF8Encoding]::new($false))
                } catch {}
            }
        }
    }
}

function Read-AtlasDatasetLiveLog {
    param([Parameter(Mandatory)][string]$Path)
    # ReadAllText's default sharing can conflict with an active redirected writer on Windows.
    $atlasReadStream = [System.IO.FileStream]::new($Path,[System.IO.FileMode]::Open,[System.IO.FileAccess]::Read,[System.IO.FileShare]::ReadWrite)
    $atlasReader = [System.IO.StreamReader]::new($atlasReadStream,[System.Text.Encoding]::UTF8)
    try { return $atlasReader.ReadToEnd() } finally { $atlasReader.Dispose() }
}

function Wait-AtlasDatasetProcess {
    param([Parameter(Mandatory)]$Record,[Parameter(Mandatory)][int]$TimeoutSeconds)
    $atlasElapsed = [System.Diagnostics.Stopwatch]::StartNew()
    $atlasNextHeartbeat = 30
    while (-not $Record.Process.WaitForExit(1000)) {
        if ($atlasElapsed.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw 'ATLAS_DATASET_STAGE_TIMED_OUT' }
        if ($atlasElapsed.Elapsed.TotalSeconds -ge $atlasNextHeartbeat) {
            Write-Output ('ATLAS_DATASET_STAGE ' + $Record.Label.ToUpperInvariant() + '_WAIT_SECONDS=' + [int]$atlasElapsed.Elapsed.TotalSeconds)
            $atlasNextHeartbeat += 30
        }
    }
    $atlasExitCode = $Record.Process.ExitCode
    Close-AtlasDatasetProcess -Record $Record
    if ($atlasExitCode -ne 0) { throw 'ATLAS_DATASET_STAGE_FAILED' }
}

function Invoke-AtlasDatasetJava {
    param([Parameter(Mandatory)][string]$Label,[Parameter(Mandatory)][string[]]$ToolArguments,[int]$TimeoutSeconds=180,[switch]$OwnerFixture)
    $atlasArguments = @('-Duser.timezone=UTC','-jar',$atlasDatasetJar) + $ToolArguments
    $atlasRecord = New-AtlasDatasetProcess -Label $Label -Executable $AtlasJava -Arguments $atlasArguments -OwnerFixture:$OwnerFixture
    Wait-AtlasDatasetProcess -Record $atlasRecord -TimeoutSeconds $TimeoutSeconds
}

function Assert-AtlasDatasetPortFree {
    if (@([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 18091).Count -gt 0) {
        throw 'ATLAS_DATASET_HELPER_PORT_OCCUPIED'
    }
}

try {
    if (-not $IsWindows) { throw 'ATLAS_DATASET_WINDOWS_POWERSHELL_REQUIRED' }
    $atlasDatasetMutex = [System.Threading.Mutex]::new($false,'Local\AtlasDataset-v1-127.0.0.1-55433-atlas')
    try { $atlasDatasetMutexHeld = $atlasDatasetMutex.WaitOne(0) }
    catch [System.Threading.AbandonedMutexException] { $atlasDatasetMutexHeld = $true }
    if (-not $atlasDatasetMutexHeld) { throw 'ATLAS_DATASET_LOAD_ALREADY_RUNNING' }
    $atlasDatasetLock = [System.IO.FileStream]::new((Join-Path $atlasDatasetDirectory 'load.lock'),[System.IO.FileMode]::OpenOrCreate,[System.IO.FileAccess]::ReadWrite,[System.IO.FileShare]::None)
    $atlasDatasetLock.SetLength(0)
    $atlasLockBytes = [System.Text.Encoding]::UTF8.GetBytes('ATLAS_DATASET_LOCK ' + $atlasDatasetRun)
    $atlasDatasetLock.Write($atlasLockBytes,0,$atlasLockBytes.Length); $atlasDatasetLock.Flush()
    Assert-AtlasDatasetPortFree

    Write-AtlasDatasetStage '1_OF_8_PREPARE'
    # Bound preparation's libpq operations too, while preserving the database server on a parent timeout.
    # A failed/timed-out preparation is inspected before retrying; no server/cluster is reset or killed.
    $atlasStep = New-AtlasDatasetProcess -Label 'prepare' -Executable $atlasDatasetPwsh -Arguments @('-NoProfile','-File',$PSCommandPath,'-InternalPhase','Prepare') -AdditionalEnvironment @{
        PGCONNECT_TIMEOUT='5'; PGOPTIONS='-c statement_timeout=10000 -c lock_timeout=3000 -c idle_in_transaction_session_timeout=15000'
    }
    Wait-AtlasDatasetProcess -Record $atlasStep -TimeoutSeconds 180
    $atlasDatasetSecrets = Get-Content -LiteralPath (Join-Path $AtlasLocal 'secrets\database.json') -Raw | ConvertFrom-Json
    # Re-sanitize preparation output now that a first-run credential file exists.
    foreach ($atlasLog in @($atlasStep.StdoutPath,$atlasStep.StderrPath)) {
        $atlasText = [System.IO.File]::ReadAllText($atlasLog)
        foreach ($atlasPurpose in @('admin','migrate','runtime')) { $atlasText = $atlasText.Replace([string]$atlasDatasetSecrets.$atlasPurpose,'[REDACTED]') }
        [System.IO.File]::WriteAllText($atlasLog,$atlasText,[System.Text.UTF8Encoding]::new($false))
    }

    Write-AtlasDatasetStage '2_OF_8_BUILD'
    $atlasStep = New-AtlasDatasetProcess -Label 'build' -Executable $atlasDatasetPwsh -Arguments @('-NoProfile','-File',$PSCommandPath,'-InternalPhase','Build')
    Wait-AtlasDatasetProcess -Record $atlasStep -TimeoutSeconds 900
    if (-not (Test-Path -LiteralPath $atlasDatasetJar)) { throw 'ATLAS_DATASET_BUILD_ARTIFACT_MISSING' }

    Write-AtlasDatasetStage '3_OF_8_MIGRATE'
    $atlasStep = New-AtlasDatasetProcess -Label 'migrate' -Executable $atlasDatasetPwsh -Arguments @('-NoProfile','-File',$PSCommandPath,'-InternalPhase','Migrate')
    Wait-AtlasDatasetProcess -Record $atlasStep -TimeoutSeconds 180
    Set-AtlasDatabaseEnvironment -Database atlas
    Remove-Item Env:\ATLAS_MIGRATION_USER,Env:\ATLAS_MIGRATION_PASSWORD,Env:\PGPASSWORD -ErrorAction SilentlyContinue

    Write-AtlasDatasetStage '4_OF_8_GENERATE_AND_REGISTER'
    Invoke-AtlasDatasetJava -Label 'generate' -ToolArguments @('--dataset','generate',$atlasDatasetDirectory,$atlasDatasetBase,'100','10000') -TimeoutSeconds 180
    # The roster mode must also call checkConfiguration(recipe-config.json) before any initial POST.
    $atlasRosterMode = if ($RevalidateRejected) { 'roster-revalidate' } else { 'roster' }
    Invoke-AtlasDatasetJava -Label 'roster-and-configuration' -ToolArguments @('--dataset',$atlasRosterMode,$atlasDatasetDirectory) -OwnerFixture -TimeoutSeconds 180

    Write-AtlasDatasetStage '5_OF_8_START_HTTP_HELPER'
    Assert-AtlasDatasetPortFree
    $atlasHttp = New-AtlasDatasetProcess -Label 'http-helper' -Executable $AtlasJava -Arguments @(
        '-Duser.timezone=UTC','-jar',$atlasDatasetJar,'--server.address=127.0.0.1','--server.port=18091',
        '--spring.main.log-startup-info=true','--logging.level.com.aldercrest.atlas.AtlasApplication=INFO')
    $atlasReady = $false; $atlasStartupWatch = [System.Diagnostics.Stopwatch]::StartNew(); $atlasStartupHeartbeat = 30
    while ($atlasStartupWatch.Elapsed.TotalSeconds -lt 180) {
        if ($atlasHttp.Process.HasExited) { throw 'ATLAS_DATASET_HTTP_HELPER_EXITED' }
        try { $atlasStartupText = Read-AtlasDatasetLiveLog -Path $atlasHttp.StdoutPath } catch { $atlasStartupText = '' }
        if ($atlasStartupText -match '(?m)\bStarted AtlasApplication\b') {
            $atlasReady = $true
            break
        }
        if ($atlasStartupWatch.Elapsed.TotalSeconds -ge $atlasStartupHeartbeat) {
            Write-Output ('ATLAS_DATASET_STAGE HTTP_START_WAIT_SECONDS=' + [int]$atlasStartupWatch.Elapsed.TotalSeconds)
            $atlasStartupHeartbeat += 30
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $atlasReady) { throw 'ATLAS_DATASET_HTTP_START_TIMED_OUT' }

    Write-AtlasDatasetStage '6_OF_8_LOAD_ACTUAL_HTTP_AND_WORKFLOW'
    Invoke-AtlasDatasetJava -Label 'load' -ToolArguments @('--dataset','load',$atlasDatasetDirectory,$atlasDatasetBase,'100','10000') -TimeoutSeconds 1800
    if ($atlasHttp.Process.HasExited) { throw 'ATLAS_DATASET_HTTP_HELPER_EXITED' }

    Write-AtlasDatasetStage '7_OF_8_REGISTER_INDIVIDUAL_RELATIONSHIPS'
    Invoke-AtlasDatasetJava -Label 'relationships' -ToolArguments @('--dataset','relationships',$atlasDatasetDirectory) -OwnerFixture -TimeoutSeconds 180

    Write-AtlasDatasetStage '8_OF_8_VERIFY_SCENARIOS'
    Invoke-AtlasDatasetJava -Label 'verify' -ToolArguments @('--dataset','verify',$atlasDatasetDirectory,$atlasDatasetBase,'100','10000') -TimeoutSeconds 600
    Write-Output 'ATLAS_DATASET 200 LOAD_AND_VERIFICATION_COMPLETED'
} catch {
    # Category only; raw exception/SQL/arguments/identities are never printed to the terminal.
    Write-Output 'ATLAS_DATASET 503 LOAD_OR_VERIFICATION_FAILED'
    throw 'ATLAS_DATASET_LOAD_FAILED_INSPECT_IGNORED_STAGE_LOGS'
} finally {
    if ($null -eq $atlasDatasetSecrets) {
        try { $atlasDatasetSecrets = Get-Content -LiteralPath (Join-Path $AtlasLocal 'secrets\database.json') -Raw | ConvertFrom-Json } catch {}
    }
    foreach ($atlasOwned in $atlasDatasetOwned) { Close-AtlasDatasetProcess -Record $atlasOwned -Stop }
    if ($null -ne $atlasDatasetSecrets) {
        foreach ($atlasOwned in $atlasDatasetOwned) {
            foreach ($atlasLog in @($atlasOwned.StdoutPath,$atlasOwned.StderrPath)) {
                try {
                    $atlasText = [System.IO.File]::ReadAllText($atlasLog)
                    foreach ($atlasPurpose in @('admin','migrate','runtime')) {
                        $atlasSecretValue = [string]$atlasDatasetSecrets.$atlasPurpose
                        if ($atlasSecretValue) { $atlasText = $atlasText.Replace($atlasSecretValue,'[REDACTED]') }
                    }
                    [System.IO.File]::WriteAllText($atlasLog,$atlasText,[System.Text.UTF8Encoding]::new($false))
                } catch {}
            }
        }
    }
    Clear-AtlasDatabaseEnvironment
    Remove-Item Env:\PGPASSWORD,Env:\PGPASSFILE,Env:\PGSERVICEFILE -ErrorAction SilentlyContinue
    $atlasDatasetSecrets = $null
    if ($null -ne $atlasDatasetLock) { $atlasDatasetLock.Dispose() }
    if ($atlasDatasetMutexHeld) { $atlasDatasetMutex.ReleaseMutex() }
    if ($null -ne $atlasDatasetMutex) { $atlasDatasetMutex.Dispose() }
    if ($script:atlasDatasetCleanupFailed) { throw 'ATLAS_DATASET_OWNED_HELPER_CLEANUP_FAILED' }
}
