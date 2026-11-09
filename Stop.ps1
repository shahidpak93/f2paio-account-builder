$directory = Join-Path $env:USERPROFILE '.runelite\f2paio'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
New-Item -ItemType File -Force -Path (Join-Path $directory 'STOP') | Out-Null
Write-Host 'STOP is set. The plugin must be restarted manually after you remove this file.'
