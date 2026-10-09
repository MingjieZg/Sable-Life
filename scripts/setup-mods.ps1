# Restore the exact official release artifacts recorded in mod-dependencies.lock.json.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$modDir = Join-Path $projectRoot '.dev\mods'
$compileDir = Join-Path $projectRoot '.dev\compile-libs'
New-Item -ItemType Directory -Force -Path $modDir,$compileDir | Out-Null
$lock = Get-Content (Join-Path $projectRoot 'mod-dependencies.lock.json') -Raw | ConvertFrom-Json
foreach ($mod in $lock) {
    if ([IO.Path]::GetFileName($mod.filename) -ne $mod.filename) { throw 'Invalid artifact filename in dependency lock.' }
    $artifactDir = if ($mod.scope -eq 'compileOnly') { $compileDir } else { $modDir }
    $destination = Join-Path $artifactDir $mod.filename
    if (-not (Test-Path $destination)) { Invoke-WebRequest -Uri $mod.url -OutFile $destination }
    if ((Get-FileHash $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $mod.sha512) {
        throw "Checksum mismatch: $($mod.filename). Restore this artifact from its official release."
    }
    Write-Output "Verified $($mod.filename)"
    if ($mod.scope -eq 'runtimeAndCompile') {
        Copy-Item -LiteralPath $destination -Destination (Join-Path $compileDir $mod.filename) -Force
    }
    # Nested libraries are needed by javac; the mod loader loads them from the original JAR at runtime.
    $zip = [IO.Compression.ZipFile]::OpenRead($destination)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like 'META-INF/jarjar/*.jar') {
                $nestedPath = Join-Path $compileDir $entry.Name
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $nestedPath, $true)
            }
        }
    } finally { $zip.Dispose() }
}
