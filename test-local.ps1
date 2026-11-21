$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$out = Join-Path $root '.build-cache\test-classes'
$java = 'D:\Environments\Java\jdk-26\bin'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& (Join-Path $java 'javac.exe') --release 8 -encoding UTF-8 -d $out (Join-Path $root 'app\src\main\java\io\github\marketlite\SettingsPolicy.java') (Join-Path $root 'tests\SettingsPolicyTest.java') (Join-Path $root 'app\src\main\java\io\github\marketlite\ReleaseVersion.java') (Join-Path $root 'tests\ReleaseVersionTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Settings tests compilation failed' }
& (Join-Path $java 'java.exe') -cp $out io.github.marketlite.SettingsPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Settings tests failed' }
& (Join-Path $java 'java.exe') -cp $out io.github.marketlite.ReleaseVersionTest
if ($LASTEXITCODE -ne 0) { throw 'Release version tests failed' }
