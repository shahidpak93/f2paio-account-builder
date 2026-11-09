param([string]$Python='C:\Users\shahi\AppData\Local\Programs\Python\Python314\pythonw.exe')
$ErrorActionPreference='Stop'
$serverScript=Join-Path $PSScriptRoot 'server.py'
try {
    $health=Invoke-RestMethod http://127.0.0.1:8092/health -TimeoutSec 2
    if($health.ready){Write-Output 'OSRS agents already ready'; return}
} catch {}
$stateFolder=Join-Path $PSScriptRoot 'state'
New-Item -ItemType Directory -Force -Path $stateFolder | Out-Null
$process=Start-Process -FilePath $Python -ArgumentList ('"'+$serverScript+'"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stateFolder 'stdout.log') -RedirectStandardError (Join-Path $stateFolder 'stderr.log')
$process.Id | Set-Content -LiteralPath (Join-Path $stateFolder 'launcher.pid')
for($attempt=0;$attempt -lt 20;$attempt++) {
    Start-Sleep -Milliseconds 250
    try {
        $health=Invoke-RestMethod http://127.0.0.1:8092/health -TimeoutSec 1
        if($health.ready){$health | ConvertTo-Json -Compress; return}
    } catch {}
}
throw 'Agents did not become ready; inspect agent_lab/state/stderr.log'
