#requires -Version 7.2
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

function Invoke-HistoryGit {
    param([Parameter(Mandatory = $true)][string[]]$GitArguments)
    $lyveOutput = @(& git @GitArguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Git history operation failed with exit code $LASTEXITCODE; inspect the local repository before retrying."
    }
    $lyveOutput | ForEach-Object { [string]$_ }
}

function Assert-HistoryDirectory {
    param([Parameter(Mandatory = $true)][string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory)) {
        New-Item -ItemType Directory -Path $Directory | Out-Null
    }
    $lyveItem = Get-Item -LiteralPath $Directory -Force
    if (-not $lyveItem.PSIsContainer -or ($lyveItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw 'History output must be a normal directory, without a symbolic link or junction.'
    }
}

function Assert-HistoryClean {
    param([Parameter(Mandatory = $true)][string]$Repository)
    $lyveChanges = @(Invoke-HistoryGit -GitArguments @('-C', $Repository, 'status', '--porcelain=v1', '--untracked-files=all'))
    if ($lyveChanges.Count -ne 0) {
        throw 'History checkpoint refused: review and commit tracked/nonignored work first. No changes were staged or committed by this command.'
    }
}

function Assert-HistoryEqual {
    param([string[]]$Expected, [string[]]$Actual, [string]$Description)
    if (@(Compare-Object -ReferenceObject $Expected -DifferenceObject $Actual -CaseSensitive).Count -ne 0) {
        throw "History verification failed: $Description differs."
    }
}

$lyveRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$lyveGitRoot = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-parse', '--show-toplevel'))[0]
if ([System.IO.Path]::GetFullPath($lyveGitRoot) -ne $lyveRoot) { throw 'Run the script from its own repository.' }
Assert-HistoryClean -Repository $lyveRoot
$lyveShallow = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-parse', '--is-shallow-repository'))[0]
if ($lyveShallow -ne 'false') { throw 'A shallow repository cannot supply the required full development history.' }
$lyveHead = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-parse', '--verify', 'HEAD'))[0]
$lyveBranch = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'symbolic-ref', '--quiet', '--short', 'HEAD'))[0]
$lyveTree = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-parse', 'HEAD^{tree}'))[0]
$lyveRefs = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'for-each-ref', '--format=%(objectname) %(refname)') | Sort-Object)
foreach ($lyveRef in $lyveRefs) {
    if ($lyveRef -notmatch '^[0-9a-f]+ refs/(heads/|tags/|remotes/origin/)') {
        throw 'Unexpected ref scope (for example stash or replacement refs). Review it before exporting history.'
    }
}
$lyveCommits = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-list', '--all') | Sort-Object)
Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'fsck', '--full', '--no-dangling') | Out-Null

$lyveHistoryRoot = Join-Path $lyveRoot 'work/history'
Assert-HistoryDirectory -Directory $lyveRoot
Assert-HistoryDirectory -Directory (Join-Path $lyveRoot 'work')
Assert-HistoryDirectory -Directory $lyveHistoryRoot
Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'check-ignore', '--quiet', '--', 'work/history/checkpoint-placeholder') | Out-Null
$lyveRunId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + $lyveHead.Substring(0, 12) + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$lyveCheckpointDirectory = Join-Path $lyveHistoryRoot $lyveRunId
if (-not $lyveCheckpointDirectory.StartsWith($lyveHistoryRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Checkpoint path escaped its fixed local directory.'
}
New-Item -ItemType Directory -Path $lyveCheckpointDirectory | Out-Null
$lyveBundle = Join-Path $lyveCheckpointDirectory 'history.bundle'
$lyveMirror = Join-Path $lyveCheckpointDirectory 'recovered.git'

# No exclusions: every object reachable through the reviewed refs is included.
Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'bundle', 'create', $lyveBundle, '--all') | Out-Null
Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'bundle', 'verify', $lyveBundle) | Out-Null
Invoke-HistoryGit -GitArguments @('clone', '--quiet', '--mirror', '--', $lyveBundle, $lyveMirror) | Out-Null
Invoke-HistoryGit -GitArguments @('--git-dir', $lyveMirror, 'fsck', '--full', '--no-dangling') | Out-Null
$lyveRecoveredRefs = @(Invoke-HistoryGit -GitArguments @('--git-dir', $lyveMirror, 'for-each-ref', '--format=%(objectname) %(refname)') | Sort-Object)
$lyveRecoveredCommits = @(Invoke-HistoryGit -GitArguments @('--git-dir', $lyveMirror, 'rev-list', '--all') | Sort-Object)
$lyveRecoveredHead = @(Invoke-HistoryGit -GitArguments @('--git-dir', $lyveMirror, 'rev-parse', 'HEAD'))[0]
$lyveRecoveredTree = @(Invoke-HistoryGit -GitArguments @('--git-dir', $lyveMirror, 'rev-parse', 'HEAD^{tree}'))[0]
Assert-HistoryEqual -Expected $lyveRefs -Actual $lyveRecoveredRefs -Description 'restored ref/object map'
Assert-HistoryEqual -Expected $lyveCommits -Actual $lyveRecoveredCommits -Description 'restored reachable commit set'
if ($lyveRecoveredHead -ne $lyveHead -or $lyveRecoveredTree -ne $lyveTree) { throw 'Restored HEAD or complete file tree does not match.' }

# Detect concurrent commits, branch switches or working-tree changes during export.
$lyveCurrentRefs = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'for-each-ref', '--format=%(objectname) %(refname)') | Sort-Object)
$lyveCurrentHead = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'rev-parse', 'HEAD'))[0]
$lyveCurrentBranch = @(Invoke-HistoryGit -GitArguments @('-C', $lyveRoot, 'symbolic-ref', '--quiet', '--short', 'HEAD'))[0]
Assert-HistoryEqual -Expected $lyveRefs -Actual $lyveCurrentRefs -Description 'source refs before/after checkpoint'
if ($lyveCurrentHead -ne $lyveHead -or $lyveCurrentBranch -ne $lyveBranch) { throw 'Source HEAD changed during checkpoint.' }
Assert-HistoryClean -Repository $lyveRoot

$lyveDigest = (Get-FileHash -LiteralPath $lyveBundle -Algorithm SHA256).Hash.ToLowerInvariant()
$lyveUtf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $lyveCheckpointDirectory 'history.bundle.sha256'),$lyveDigest + '  history.bundle' + "`n",$lyveUtf8)
$lyveManifest = [ordered]@{
    status = 'PASS'
    verifiedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    checkpointDirectory = 'work/history/' + $lyveRunId
    bundle = 'history.bundle'
    sha256 = $lyveDigest
    branch = $lyveBranch
    head = $lyveHead
    tree = $lyveTree
    reachableCommits = $lyveCommits.Count
    refs = $lyveRefs
    checks = @('clean source', 'full history', 'bundle verify', 'independent mirror restore', 'restored object integrity', 'all refs equal', 'reachable commit sets equal', 'HEAD and complete file tree equal', 'unchanged source')
    scope = 'Committed reachable Git history only; local configuration, uncommitted/ignored files and database data are not backed up.'
}
$lyveJson = $lyveManifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $lyveCheckpointDirectory 'verification.json'),$lyveJson + "`n",$lyveUtf8)
$lyveJson
