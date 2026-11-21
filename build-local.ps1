$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$cache = Join-Path $root '.build-cache'
$sdk = (Get-ChildItem (Join-Path $cache 'platform') -Recurse -Filter 'android.jar' | Select-Object -First 1).FullName
$tools = (Get-ChildItem (Join-Path $cache 'tools') -Recurse -Filter 'aapt2.exe' | Select-Object -First 1).Directory.FullName
$java = 'D:\Environments\Java\jdk-26\bin'
$out = Join-Path $root 'dist'
$work = Join-Path $cache 'work'
New-Item -ItemType Directory -Force -Path $out,$work,(Join-Path $work 'classes'),(Join-Path $work 'dex') | Out-Null
function Invoke-Checked($program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed: $program ($LASTEXITCODE)" }
}
$src = Join-Path $root 'app\src\main'
$api = Join-Path $cache 'xposed.jar'
$sources = @(Get-ChildItem "$src\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked (Join-Path $java 'javac.exe') (@('--release','8','-encoding','UTF-8','-classpath',"$sdk;$api",'-d',"$work\classes") + $sources)
Invoke-Checked (Join-Path $java 'jar.exe') @('cf',"$work\code.jar",'-C',"$work\classes",'.')
Invoke-Checked (Join-Path $java 'java.exe') @('-cp',"$tools\lib\d8.jar",'com.android.tools.r8.D8','--min-api','24','--lib',$sdk,'--classpath',$api,'--output',"$work\dex", "$work\code.jar")
Invoke-Checked (Join-Path $tools 'aapt2.exe') @('compile','--dir',"$src\res",'-o',"$work\resources.zip")
Invoke-Checked (Join-Path $tools 'aapt2.exe') @('link','-o',"$work\unsigned.apk",'-I',$sdk,'--manifest',"$src\AndroidManifest.xml",'-A',"$src\assets",'--min-sdk-version','24','--target-sdk-version','35','--version-code','7','--version-name','0.4.2-alpha',"$work\resources.zip")
Invoke-Checked (Join-Path $java 'jar.exe') @('uf',"$work\unsigned.apk",'-C',"$work\dex",'classes.dex')
Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-f','4',"$work\unsigned.apk", "$work\aligned.apk")
$key = Join-Path $cache 'test-signing.jks'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-Checked (Join-Path $java 'keytool.exe') @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','marketlite-test','-dname','CN=MarketLite Test','-keyalg','RSA','-keysize','2048','-validity','3650','-storetype','JKS')
}
$apk = Join-Path $out 'MarketLite-0.4.2-alpha.apk'
Invoke-Checked (Join-Path $java 'java.exe') @('-jar',"$tools\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','marketlite-test','--out',$apk,"$work\aligned.apk")
Invoke-Checked (Join-Path $java 'java.exe') @('-jar',"$tools\lib\apksigner.jar",'verify','--verbose',$apk)
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
