# 本地 APK 构建脚本
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $Root '.tools\jdk-17.0.20.1+1'
$env:ANDROID_SDK_ROOT = Join-Path $Root '.tools\android-sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$Gradle = Join-Path $Root '.tools\gradle-8.10.2\bin\gradle.bat'
& $Gradle assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$Dist = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force $Dist | Out-Null
$Apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
$GenericApk = Join-Path $Dist 'steelfire-assault-debug.apk'
Copy-Item $Apk $GenericApk -Force
$Hash = (Get-FileHash $GenericApk -Algorithm SHA256).Hash.ToLower()
"$Hash *steelfire-assault-debug.apk" | Set-Content (Join-Path $Dist 'SHA256SUMS.txt') -Encoding ascii

# Keep the versioned artifact alongside the stable convenience path so reports and
# install instructions cannot silently refer to a previous build.
$Version = '0.1.1'
$VersionDir = Join-Path $Dist "steelfire-assault-v$Version"
New-Item -ItemType Directory -Force $VersionDir | Out-Null
$VersionedApk = Join-Path $VersionDir "steelfire-assault-v$Version-debug.apk"
Copy-Item $Apk $VersionedApk -Force
"$Hash *steelfire-assault-v$Version-debug.apk" | Set-Content (Join-Path $VersionDir 'SHA256SUMS.txt') -Encoding ascii
Write-Host "APK: $Dist\steelfire-assault-debug.apk"
Write-Host "SHA256: $Hash"
