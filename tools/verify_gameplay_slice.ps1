param([switch]$Build)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$mapper = Get-Content (Join-Path $root "app/src/main/java/com/steelfire/assault/TouchInputMapper.kt") -Raw
$snapshot = Get-Content (Join-Path $root "app/src/main/java/com/steelfire/assault/InputSnapshot.kt") -Raw
$game = Get-Content (Join-Path $root "app/src/main/java/com/steelfire/assault/GameView.kt") -Raw

function Assert-Contains([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { throw "FAIL: $message" }
    Write-Host "PASS: $message"
}

Assert-Contains $snapshot "ownedPointerIds" "InputSnapshot exposes owned pointer IDs"
Assert-Contains $mapper "owners = LinkedHashMap<Int, Control>()" "mapper stores pointerId ownership"
Assert-Contains $mapper "fun onUp(pointerId: Int)" "mapper releases only the lifted pointer"
Assert-Contains $mapper "crouchHeld" "mapper exposes crouch hold state"
Assert-Contains $mapper "aimHeld" "mapper exposes aim hold state"
Assert-Contains $game "Mode.CHECKPOINT" "death flow has a checkpoint mode"
Assert-Contains $game "retryCheckpoint()" "checkpoint mode has a retry path"
Assert-Contains $game "TouchInputMapper(W, H)" "GameView consumes the pointer ownership mapper"
Assert-Contains $game "if (crouchHeld) 145f else 230f" "crouch changes movement speed"
Assert-Contains $game "if (aimHeld) -105f else 0f" "aim changes projectile trajectory"
Assert-Contains $game "cameraShake = max(cameraShake" "camera response is advanced in fixed-step update"
Assert-Contains $game "hitFlash = 0.12f" "damage flash is advanced from the hit event"
Assert-Contains $game "muzzleFlash = 0.08f" "muzzle flash is advanced from the shot event"

if ($Build) {
    & (Join-Path $root "build-apk.ps1")
    if ($LASTEXITCODE -ne 0) { throw "FAIL: APK build" }
    if (-not (Test-Path (Join-Path $root "dist/steelfire-assault-debug.apk"))) { throw "FAIL: APK missing" }
    Write-Host "PASS: debug APK built"
}

Write-Host "Gameplay slice static verification complete. Device checklist remains open until iQOO 13 replay."
