param([switch]$Sources)
$ErrorActionPreference='Stop'
$projectDirectory=Split-Path -Parent $PSScriptRoot
$artifactDirectory=Join-Path $projectDirectory '.dev\epicfight'
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$artifacts=@(
    @{Name='epic-fight-21.17.3.1-mc1.21.1-neoforge.jar';Hash='d39c1d4a2198e7f2080cdc98de890d26d658a02b44c8399a301c7c0005d9991ff97cc94881823a9daa8952f3b89eb3d9197c2f83cf0fbba9c7992c77921b2122'}
)
if($Sources){$artifacts+=@{Name='epic-fight-21.17.3.1-mc1.21.1-neoforge-sources.jar';Hash='53a3afc7c434688c3dbb1d666d34d5d9c7a44d2ada478bf228e825fa0d190417b8de7abac1edeffe605a9282d69f77c812d721ebfc79ccda3b55eefcb8be011a'}}
foreach($artifact in $artifacts) {
    $target=Join-Path $artifactDirectory $artifact.Name
    if(Test-Path -LiteralPath $target) {
        if((Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash.ToLowerInvariant() -eq $artifact.Hash){Write-Output "Verified $($artifact.Name)";continue}
    }
    $temporary="$target.download"
    Invoke-WebRequest -Uri "https://cdn.modrinth.com/data/vu3NZ5Ma/versions/8HHhJt6i/$($artifact.Name)" -OutFile $temporary
    if((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant() -ne $artifact.Hash){throw "Checksum mismatch: $($artifact.Name)"}
    Move-Item -LiteralPath $temporary -Destination $target -Force
    Write-Output "Verified $($artifact.Name)"
}
