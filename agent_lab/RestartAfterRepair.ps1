param([Parameter(Mandatory=$true)][string]$JobId)
$ErrorActionPreference='Stop'
if ($JobId -notmatch '^[a-f0-9]{16}$') { throw 'Invalid repair job ID' }
$projectRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$jobRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot "state/jobs/$JobId"))
$expectedJobs=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'state/jobs'))+[IO.Path]::DirectorySeparatorChar
if (-not $jobRoot.StartsWith($expectedJobs,[StringComparison]::OrdinalIgnoreCase)) { throw 'Job outside this project' }
Start-Sleep -Seconds 2
try {
    & (Join-Path $PSScriptRoot 'StopAgents.ps1')
    & (Join-Path $PSScriptRoot 'StartAgents.ps1')
    @{status='RESTARTED';at=(Get-Date -Format o)} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $jobRoot 'restart-status.json')
} catch {
    & (Join-Path $PSScriptRoot 'StopAgents.ps1')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::OpenRead((Join-Path $jobRoot 'source-backup.zip'))
    try {
        foreach($entry in $archive.Entries) {
            if (-not $entry.FullName.StartsWith('agent_lab/') -or -not $entry.FullName.EndsWith('.py')) { continue }
            $destination=[IO.Path]::GetFullPath((Join-Path $projectRoot $entry.FullName))
            $allowed=[IO.Path]::GetFullPath($PSScriptRoot)+[IO.Path]::DirectorySeparatorChar
            if (-not $destination.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)) { throw 'Backup path outside agent source' }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$destination,$true)
        }
    } finally { $archive.Dispose() }
    & (Join-Path $PSScriptRoot 'StartAgents.ps1')
    @{status='ROLLED_BACK_GATEWAY_SOURCE';at=(Get-Date -Format o)} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $jobRoot 'restart-status.json')
}
