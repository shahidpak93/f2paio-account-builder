$ErrorActionPreference='Stop'
$expectedScript=Join-Path $PSScriptRoot 'server.py'
foreach($name in @('server.pid','launcher.pid')) {
    $pidFile=Join-Path $PSScriptRoot ('state/'+$name)
    if(-not(Test-Path -LiteralPath $pidFile)) { continue }
    try { $servicePid=[int](Get-Content -LiteralPath $pidFile -Raw) } catch { continue }
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$servicePid"
    if($process -and $process.CommandLine -and $process.CommandLine.Contains($expectedScript)) {
        Stop-Process -Id $servicePid
        Write-Output 'Stopped this project agent service'
        return
    }
}
Write-Output 'No matching project agent process is running'
