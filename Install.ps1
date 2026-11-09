param([string]$PluginDirectory = "$env:USERPROFILE\.runelite\microbot-plugins")
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'dist\F2pAioPlugin.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run Build.ps1 first.' }
New-Item -ItemType Directory -Force -Path $PluginDirectory | Out-Null
$destination = Join-Path $PluginDirectory 'F2pAioPlugin.jar'
if (Test-Path -LiteralPath $destination) {
    Copy-Item -LiteralPath $destination -Destination ($destination + '.' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bak')
}
Copy-Item -LiteralPath $jar -Destination $destination
Write-Host "Installed $destination. Rescan local plugins or restart Microbot, then enable F2P AIO."

# This package is configured as an account builder with a local model gateway.
$settingsPath = Join-Path $env:USERPROFILE '.runelite\f2paio\panel.json'
$settingsDirectory = Split-Path -Parent $settingsPath
New-Item -ItemType Directory -Force -Path $settingsDirectory | Out-Null
$settings = if (Test-Path -LiteralPath $settingsPath) { Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json } else { [pscustomobject]@{} }
$values = @{ autonomous=$true; accountBuilder=$true; taskMode='AIO'; allowPvp=$false; enableFlipping=$false; llmEnabled=$true; llmBackend='OLLAMA'; llmEndpoint='http://127.0.0.1:8092/api/generate'; llmModel='osrs-builder:2b'; localFallbackEnabled=$true; localFallbackModel='osrs-builder:2b'; gearReserveCoins=1000 }
foreach ($entry in $values.GetEnumerator()) { $settings | Add-Member -NotePropertyName $entry.Key -NotePropertyValue $entry.Value -Force }
$settings | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $settingsPath -Encoding utf8
Write-Host 'Account-builder settings saved. Local agents use agent_lab/StartAgents.ps1; direct local-model fallback remains enabled.'
