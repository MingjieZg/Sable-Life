$ErrorActionPreference='Stop'
$projectDirectory=Split-Path -Parent $PSScriptRoot
$artifacts=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'iron-spells-dependencies.json') -Raw | ConvertFrom-Json
foreach($artifact in $artifacts){
    $directory=Join-Path $projectDirectory $artifact.directory
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $target=Join-Path $directory $artifact.file
    if((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash.ToLowerInvariant() -eq $artifact.sha512){Write-Output "Verified $($artifact.file)";continue}
    $temporary="$target.download"
    Invoke-WebRequest -Uri $artifact.url -OutFile $temporary
    if((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant() -ne $artifact.sha512){throw "Checksum mismatch: $($artifact.file)"}
    Move-Item -LiteralPath $temporary -Destination $target -Force
    Write-Output "Verified $($artifact.file)"
}
