param([string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& .\Build.ps1 -Jdk $Jdk
$info = Get-Content -LiteralPath dist/build-info.json -Raw | ConvertFrom-Json
$tests = Join-Path $PSScriptRoot ('build\tests-' + [guid]::NewGuid().ToString('N'))
$testHome = Join-Path $tests 'home'
New-Item -ItemType Directory -Force -Path $tests,$testHome | Out-Null
$cp = $info.classes + ';' + $info.clientJar
& "$Jdk\bin\javac.exe" --release 11 --add-modules jdk.httpserver -encoding UTF-8 -proc:none -cp $cp -d $tests src/test/java/com/example/f2paio/OfflineTests.java src/test/java/com/example/f2paio/LootRegressionTests.java src/test/java/com/example/f2paio/LearningTests.java src/test/java/com/example/f2paio/ChainTests.java src/test/java/com/example/f2paio/AccountBuilderTests.java src/test/java/com/example/f2paio/RunEnergyTests.java src/test/java/com/example/f2paio/BankCurriculumTests.java src/test/java/com/example/f2paio/RuntimeFixTests.java src/test/java/com/example/f2paio/AccountKnowledgeTests.java src/test/java/com/example/f2paio/AiBridgeTests.java src/test/java/com/example/f2paio/LoaderCheck.java src/test/java/com/example/f2paio/CatalogExporter.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& "$Jdk\bin\java.exe" --add-modules jdk.httpserver "-Duser.home=$testHome" -ea -cp ($tests + ';' + $cp) com.example.f2paio.OfflineTests
if ($LASTEXITCODE -ne 0) { throw 'Offline tests failed.' }
& "$Jdk\bin\java.exe" "-Duser.home=$testHome" -ea -cp ($tests + ';' + $cp) com.example.f2paio.LoaderCheck (Join-Path $PSScriptRoot 'dist\F2pAioPlugin.jar')
if ($LASTEXITCODE -ne 0) { throw 'Native plugin loader check failed.' }
& "$Jdk\bin\java.exe" "-Duser.home=$testHome" -cp ($tests + ';' + $cp) com.example.f2paio.CatalogExporter (Join-Path $PSScriptRoot 'METHODS.json')
if ($LASTEXITCODE -ne 0) { throw 'Catalogue export failed.' }

