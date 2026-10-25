<#
E96 P5-A READ-ONLY device preflight. Never installs, uninstalls, clears data, launches the app, touches the screen or the
network. It only asks adb for facts and prints them. The installed APK is copied to a scratch folder to read its signing
certificate FINGERPRINT (never keys), then that copy is deleted.

  .\p5-device-preflight.ps1 -Serial QV725WEQ54 -ExpectedModel XQ-AS52 `
      -Adb C:\Android\Sdk\platform-tools\adb.exe -Apksigner C:\Android\Sdk\build-tools\36.0.0\apksigner.bat `
      -CandidateVersionCode 109 -CandidateCertSha256 <64 hex> -CandidatePackage app.melovault.player

Output: one KEY=VALUE per line, then one GATE line per check (PASS/FAIL/SKIP). It mirrors QaInstallPreflight.kt.
#>
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$ExpectedModel,
    [Parameter(Mandatory = $true)][string]$Adb,
    [Parameter(Mandatory = $true)][string]$Apksigner,
    [string]$Package = 'app.melovault.player',
    [string]$CandidatePackage = '',
    [long]$CandidateVersionCode = 0,
    [string]$CandidateCertSha256 = '',
    [string]$CandidateAbis = '',
    [string]$Scratch = (Join-Path $env:TEMP 'p5a-preflight')
)
$ErrorActionPreference = 'Stop'
function Adbs([string[]]$a) { (& $Adb -s $Serial @a 2>&1 | Out-String).Trim() }
function Gate([string]$name, [string]$state, [string]$detail) { Write-Output ("GATE {0}={1} {2}" -f $name, $state, $detail) }

# 1. exact serial, exact state (never "the only device")
$list = (& $Adb devices -l 2>&1 | Out-String)
$row = ($list -split "`r?`n" | Where-Object { $_ -match ('^' + [regex]::Escape($Serial) + '\s') })
Write-Output ("DEVICES_LISTED=" + (($list -split "`r?`n" | Where-Object { $_ -match '\sdevice\s|\sunauthorized|\soffline' }).Count))
if (-not $row) { Gate 'SERIAL_LISTED' 'FAIL' $Serial; exit 2 }
$state = Adbs @('get-state')
Write-Output "STATE=$state"
if ($state -ne 'device') { Gate 'DEVICE_READY' 'FAIL' $state; exit 2 }

# 2. facts (read-only getprop)
$model = Adbs @('shell', 'getprop', 'ro.product.model')
Write-Output "MODEL=$model"
Write-Output ("MANUFACTURER=" + (Adbs @('shell', 'getprop', 'ro.product.manufacturer')))
Write-Output ("ANDROID=" + (Adbs @('shell', 'getprop', 'ro.build.version.release')))
Write-Output ("SDK=" + (Adbs @('shell', 'getprop', 'ro.build.version.sdk')))
$abis = Adbs @('shell', 'getprop', 'ro.product.cpu.abilist')
Write-Output "ABIS=$abis"
Gate 'MODEL' $(if ($model -eq $ExpectedModel) { 'PASS' } else { 'FAIL' }) $model

# 3. installed package (read-only)
$path = Adbs @('shell', 'pm', 'path', $Package)
$base = ($path -split "`r?`n" | Where-Object { $_ -like 'package:*base.apk' } | Select-Object -First 1)
if (-not $base) { Gate 'INSTALLED' 'FAIL' 'package not installed'; exit 2 }
$base = $base.Substring(8)
$dump = Adbs @('shell', 'dumpsys', 'package', $Package)
$vc = [regex]::Match($dump, 'versionCode=(\d+)').Groups[1].Value
$vn = [regex]::Match($dump, 'versionName=([^\s]+)').Groups[1].Value
$upd = [regex]::Match($dump, 'lastUpdateTime=([^\r\n]+)').Groups[1].Value
Write-Output "INSTALLED_PACKAGE=$Package"
Write-Output "INSTALLED_VERSIONCODE=$vc"
Write-Output "INSTALLED_VERSIONNAME=$vn"
Write-Output "INSTALLED_LASTUPDATE=$upd"
Write-Output ("INSTALLED_DEBUGGABLE=" + ($dump -match 'pkgFlags=\[[^\]]*DEBUGGABLE'))

# 4. installed certificate FINGERPRINT from a scratch copy of the base APK (deleted afterwards)
New-Item -ItemType Directory -Force -Path $Scratch | Out-Null
$copy = Join-Path $Scratch ("installed-" + $Serial + ".apk")
$ErrorActionPreference = 'Continue'; $null = & $Adb -s $Serial pull $base $copy 2>&1; $ErrorActionPreference = 'Stop'
try {
    $certs = (& $Apksigner verify --print-certs $copy 2>&1 | Out-String)
    $fp = [regex]::Match($certs, 'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})').Groups[1].Value
    Write-Output "INSTALLED_CERT_SHA256=$fp"
    $dn = [regex]::Match($certs, 'Signer #1 certificate DN: ([^\r\n]+)').Groups[1].Value
    Write-Output "INSTALLED_CERT_DN=$dn"
} finally {
    Remove-Item -Force -ErrorAction SilentlyContinue $copy
}

# 5. candidate comparison (only when the caller supplies the candidate's facts)
if ($CandidateCertSha256 -ne '') {
    Gate 'PACKAGE' $(if ($CandidatePackage -eq $Package) { 'PASS' } else { 'FAIL' }) $CandidatePackage
    Gate 'NO_DOWNGRADE' $(if ($CandidateVersionCode -ge [long]$vc) { 'PASS' } else { 'FAIL' }) "candidate=$CandidateVersionCode installed=$vc"
    Gate 'SIGNATURE' $(if ($CandidateCertSha256 -eq $fp) { 'PASS' } else { 'FAIL' }) "candidate=$CandidateCertSha256 installed=$fp"
    if ($CandidateAbis -ne '') {
        $ok = $false
        foreach ($a in $CandidateAbis.Split(',')) { if ($abis.Split(',') -contains $a) { $ok = $true } }
        Gate 'ABI' $(if ($ok) { 'PASS' } else { 'FAIL' }) $CandidateAbis
    }
} else {
    Gate 'CANDIDATE' 'SKIP' 'no candidate facts supplied'
}
Write-Output 'DONE read-only: nothing was installed, cleared, launched or changed.'
