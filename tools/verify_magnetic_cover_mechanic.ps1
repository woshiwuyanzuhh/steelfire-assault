$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\")).Path
$javaHome = Join-Path $repoRoot ".tools\jdk-17.0.20.1+1"
$androidHome = Join-Path $repoRoot ".tools\android-sdk"
$gradle = Join-Path $repoRoot ".tools\gradle-8.10.2\bin\gradle.bat"
$javac = Join-Path $javaHome "bin\javac.exe"
$java = Join-Path $javaHome "bin\java.exe"
$kotlinClasses = Join-Path $repoRoot "app\build\tmp\kotlin-classes\debug"
$source = Join-Path $repoRoot "tools\MagneticCoverMechanicVerifier.java"
# Keep generated verifier bytecode under the ignored Android build tree.
$output = Join-Path $repoRoot "app\build\magnetic-cover-verifier\classes"

foreach ($path in @($javaHome, $androidHome, $gradle, $javac, $java, $source)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing verifier dependency: $path"
    }
}

# Compile the production Kotlin reducer first. The verifier intentionally stays outside the APK
# source set, so adding it does not add a test framework or runtime dependency to the app.
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
& $gradle ":app:compileDebugKotlin" "--offline" "--no-daemon" "-Pkotlin.incremental=false"
if ($LASTEXITCODE -ne 0) {
    throw "Gradle Kotlin compilation failed with exit code $LASTEXITCODE"
}
if (-not (Test-Path -LiteralPath $kotlinClasses)) {
    throw "Kotlin compiler produced no classes at $kotlinClasses"
}

$stdlibRoot = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib"
$stdlib = Get-ChildItem -LiteralPath $stdlibRoot -Filter "kotlin-stdlib-*.jar" -File -Recurse |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $stdlib) {
    throw "Kotlin standard library jar was not found under $stdlibRoot"
}

if (Test-Path -LiteralPath $output) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$compileClasspath = "$kotlinClasses;$($stdlib.FullName)"
& $javac "-encoding" "UTF-8" "-cp" $compileClasspath "-d" $output $source
if ($LASTEXITCODE -ne 0) {
    throw "JVM verifier compilation failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = "$output;$compileClasspath"
& $java "-ea" "-cp" $runtimeClasspath "MagneticCoverMechanicVerifier"
if ($LASTEXITCODE -ne 0) {
    throw "MagneticCoverMechanic JVM verifier failed with exit code $LASTEXITCODE"
}
