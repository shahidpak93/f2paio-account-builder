param(
    [string]$ClientJar = '',
    [string]$Jdk = ''
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $ClientJar) {
    $ClientJar = (Get-ChildItem -LiteralPath "$env:USERPROFILE\.microbot" -Filter 'microbot-*.jar' |
        Sort-Object { [version]($_.BaseName -replace '^microbot-', '') } -Descending |
        Select-Object -First 1).FullName
}
if (-not $ClientJar -or -not (Test-Path -LiteralPath $ClientJar)) { throw 'Pass -ClientJar with your Microbot shaded JAR path.' }
if (-not $Jdk) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) { $Jdk = $env:JAVA_HOME }
    else {
        $Jdk = (Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1).FullName
    }
}
if (-not $Jdk -or -not (Test-Path -LiteralPath "$Jdk\bin\javac.exe")) { throw 'JDK 17+ required. Pass -Jdk.' }
New-Item -ItemType Directory -Force -Path build,dist | Out-Null
$classes = Join-Path $PSScriptRoot ('build\classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null
$sources = Get-ChildItem -LiteralPath src/main/java -Filter '*.java' -Recurse
$sourceList = Join-Path $PSScriptRoot 'build\sources.txt'
$sourceLines = $sources | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
[IO.File]::WriteAllLines($sourceList, [string[]]$sourceLines, [Text.UTF8Encoding]::new($false))
& "$Jdk\bin\javac.exe" --release 11 -encoding UTF-8 -proc:none -Xlint:unchecked -cp $ClientJar -d $classes "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Copy-Item -LiteralPath LICENSE-MICROBOT-HUB.txt -Destination $classes
Copy-Item -LiteralPath LICENSE-MICROBOT.txt -Destination $classes
Copy-Item -LiteralPath knowledge/rules.json -Destination (Join-Path $classes 'osrs-knowledge.json')
Copy-Item -LiteralPath knowledge/f2p-production.json,knowledge/human-workflow-prior.json -Destination $classes
$versionMatch = Select-String -LiteralPath src/main/java/com/example/f2paio/Tuning.java -Pattern 'VERSION = "([0-9.]+)"'
$pluginVersion = $versionMatch.Matches[0].Groups[1].Value
$manifest = "Manifest-Version: 1.0`r`nMicrobot-Plugin-Class: com.example.f2paio.F2pAioPlugin`r`nMicrobot-Plugin-Id: com.example.f2paio`r`nImplementation-Version: $pluginVersion`r`n`r`n"
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build\MANIFEST.MF'), $manifest, [Text.UTF8Encoding]::new($false))
& "$Jdk\bin\jar.exe" --create --file dist/F2pAioPlugin.jar --manifest build/MANIFEST.MF -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
@{ clientJar=$ClientJar; clientSha256=(Get-FileHash -LiteralPath $ClientJar -Algorithm SHA256).Hash;
   java=(cmd.exe /c "`"$Jdk\bin\java.exe`" -version 2>&1" | Out-String).Trim();
   builtAt=[DateTimeOffset]::Now.ToString('o'); classes=$classes;
   jarSha256=(Get-FileHash -LiteralPath dist/F2pAioPlugin.jar -Algorithm SHA256).Hash } |
    ConvertTo-Json | Set-Content -LiteralPath dist/build-info.json -Encoding utf8
Write-Host 'Built dist\F2pAioPlugin.jar'
