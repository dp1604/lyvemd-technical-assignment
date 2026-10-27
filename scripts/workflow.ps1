[CmdletBinding(DefaultParameterSetName='Single')]
param(
    [Parameter(Mandatory,ParameterSetName='Single')][ValidateSet('review','attest-critical','release','revoke-proxy')][string]$Action,
    [Parameter(Mandatory,ParameterSetName='Single')][ValidatePattern('^[A-Za-z0-9._-]{1,64}$')][string]$ActorId,
    [Parameter(Mandatory,ParameterSetName='Single')][ValidatePattern('^[A-Za-z0-9.-]{1,64}$')][string]$PatientId,
    [Parameter(Mandatory,ParameterSetName='Single')][ValidatePattern('^[A-Za-z0-9._-]{1,64}$')][string]$TargetId,
    [Parameter(ParameterSetName='Single')][ValidateSet('NONCRITICAL','CRITICAL')][string]$Classification,
    [Parameter(ParameterSetName='Single')][switch]$AttestComplete,
    [Parameter(Mandatory,ParameterSetName='Batch')][string]$BatchFile
)
. (Join-Path $PSScriptRoot 'lib\local-environment.ps1')
$atlasWorkflowJar = Join-Path $AtlasRoot 'target\atlas-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $atlasWorkflowJar)) {
    throw 'Build the service with the documented start command before using the local workflow command.'
}
if (($Action -eq 'review') -and (-not $Classification)) { throw 'Review requires an explicit classification.' }
if (($Action -ne 'review') -and $Classification) { throw 'Classification is valid only for review.' }
if (($Action -eq 'attest-critical') -and (-not $AttestComplete)) { throw 'Critical-path completion requires explicit attestation.' }
if (($Action -ne 'attest-critical') -and $AttestComplete) { throw 'Attestation is valid only for the critical-path action.' }
$atlasWorkflowArguments = if($PSCmdlet.ParameterSetName -eq 'Batch') {@('--workflow','batch',(Resolve-Path -LiteralPath $BatchFile).Path)} else {@('--workflow', $Action, $ActorId, $PatientId, $TargetId)}
if ($Action -eq 'review') { $atlasWorkflowArguments += $Classification }
if ($Action -eq 'attest-critical') { $atlasWorkflowArguments += '--attest-complete' }
Set-AtlasDatabaseEnvironment -Database atlas
try {
    & $AtlasJava '-Duser.timezone=UTC' -jar $atlasWorkflowJar @atlasWorkflowArguments
    if ($LASTEXITCODE -ne 0) { throw 'Local workflow action did not complete. See its categorical result.' }
} finally { Clear-AtlasDatabaseEnvironment }
