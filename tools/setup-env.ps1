# Sinoar - Android toolchain bootstrap (one-time)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$log = 'D:\Projects\sinoar\tools\setup.log'
New-Item -ItemType Directory -Force -Path 'D:\Projects\sinoar\tools' | Out-Null
Remove-Item 'D:\Projects\sinoar\.dsh-write-check' -Force -ErrorAction SilentlyContinue

function Log([string]$m) {
    $line = "{0}  {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Output $line
    Add-Content -Path $log -Value $line -Encoding utf8
}

$root = 'D:\tools'
$sdk  = 'D:\Android\Sdk'
New-Item -ItemType Directory -Force -Path $root, $sdk | Out-Null

# ---------- 1) JDK 21 ----------
$jdkHome = Join-Path $root 'jdk21'
if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
    Log 'JDK21: downloading (195 MB)'
    $zip = Join-Path $root 'jdk21.zip'
    Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip -UseBasicParsing
    Log 'JDK21: extracting'
    $tmp = Join-Path $root 'jdk21-tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $jdkHome
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}
$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"
Log ('JDK21: ' + ((& "$jdkHome\bin\java.exe" -version 2>&1) -join ' | '))

# ---------- 2) Android cmdline-tools ----------
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    Log 'cmdline-tools: downloading (146 MB)'
    $zip = Join-Path $root 'cmdline-tools.zip'
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $zip -UseBasicParsing
    Log 'cmdline-tools: extracting'
    $tmp = Join-Path $root 'cmdtools-tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}

# ---------- 3) SDK packages ----------
$yes = ("y`n" * 60)
Log 'sdkmanager: accepting licenses'
$yes | & $sdkManager --sdk_root=$sdk --licenses 2>&1 | ForEach-Object { Log $_ }

$packages = @('platform-tools', 'platforms;android-35', 'build-tools;35.0.0', 'cmake;3.22.1', 'ndk;27.0.12077973')
Log ('sdkmanager: installing ' + ($packages -join ', '))
$yes | & $sdkManager --sdk_root=$sdk @packages 2>&1 | ForEach-Object { Log $_ }

# ---------- 4) Gradle ----------
$gradleHome = Join-Path $root 'gradle-8.11.1'
if (-not (Test-Path (Join-Path $gradleHome 'bin\gradle.bat'))) {
    Log 'Gradle: downloading (130 MB)'
    $zip = Join-Path $root 'gradle.zip'
    Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $zip -UseBasicParsing
    Log 'Gradle: extracting'
    Expand-Archive -Path $zip -DestinationPath $root
    Remove-Item $zip -Force
}

# ---------- 5) Persist environment ----------
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkHome, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdk, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdk, 'User')

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$parts = @()
if ($userPath) { $parts = @($userPath -split ';' | Where-Object { $_ }) }
foreach ($p in @("$jdkHome\bin", "$sdk\platform-tools", "$sdk\cmdline-tools\latest\bin", "$gradleHome\bin")) {
    if ($parts -notcontains $p) { $parts += $p }
}
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')

# ---------- 6) Verify ----------
Log 'VERIFY -----------------------------'
Log ('platforms/android-35 : ' + (Test-Path (Join-Path $sdk 'platforms\android-35')))
Log ('build-tools/35.0.0   : ' + (Test-Path (Join-Path $sdk 'build-tools\35.0.0')))
Log ('ndk/27.0.12077973    : ' + (Test-Path (Join-Path $sdk 'ndk\27.0.12077973')))
Log ('cmake/3.22.1         : ' + (Test-Path (Join-Path $sdk 'cmake\3.22.1')))
Log ('adb                  : ' + (Test-Path (Join-Path $sdk 'platform-tools\adb.exe')))
Log ('gradle               : ' + (Test-Path (Join-Path $gradleHome 'bin\gradle.bat')))
Log 'DONE'
