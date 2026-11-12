[CmdletBinding()]
param(
    [ValidateSet('build','assemble','test','runClient','runServer','runGameTestServer','tasks','createMinecraftArtifacts','prepareClientRun')]
    [string]$Task = 'build'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkCandidates = @(
    $env:JAVA_HOME,
    (Join-Path $env:ProgramFiles 'Java\jdk-21')
) + @(Get-ChildItem (Join-Path $env:ProgramFiles 'Eclipse Adoptium') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | ForEach-Object FullName)
$jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\javac.exe')) } | Where-Object {
    (& (Join-Path $_ 'bin\javac.exe') -version 2>&1 | Out-String) -match 'javac 21\.'
} | Select-Object -First 1
if (-not $jdk) { throw 'A Java 21 JDK is required. Install one or set JAVA_HOME to it.' }
$env:JAVA_HOME = $jdk
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
Push-Location $projectRoot
try {
    & .\gradlew.bat $Task --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle $Task failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
