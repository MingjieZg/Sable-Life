# Optional local weapon compatibility mods; not Sable Life runtime dependencies.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $projectRoot '.dev\test-mods'
$clientMods = Join-Path $projectRoot 'runs\client\mods'
New-Item -ItemType Directory -Force -Path $cache,$clientMods | Out-Null
$lock = Get-Content (Join-Path $projectRoot 'test-mod-dependencies.lock.json') -Raw | ConvertFrom-Json
foreach ($mod in $lock) {
    if ([IO.Path]::GetFileName($mod.filename) -ne $mod.filename) { throw 'Invalid artifact filename.' }
    $artifact = Join-Path $cache $mod.filename
    if (-not (Test-Path -LiteralPath $artifact)) {
        Invoke-WebRequest -Uri $mod.url -OutFile $artifact -Headers @{'User-Agent'='SableLife-local-compat-test'}
    }
    if ((Get-FileHash -LiteralPath $artifact -Algorithm $mod.algorithm).Hash.ToLowerInvariant() -ne $mod.hash) {
        throw "Checksum mismatch: $($mod.filename)"
    }
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $clientMods $mod.filename) -Force
    Write-Output "Installed optional test mod $($mod.filename)"
}
