param()

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$vaultRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $projectRoot '..\..\..')
)
$buildRoot = Join-Path $vaultRoot '04 - Builds und Daten\CatPlugins\CatClans'
$uploadRoot = Join-Path $buildRoot 'Upload'

[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml')
$version = [string]$pom.project.version
$artifactName = "CatClans-v$version"
$jar = Join-Path $projectRoot "target\$artifactName.jar"
$release = Join-Path $buildRoot $version
$latest = Join-Path $uploadRoot 'Latest-Beta'
$staging = Join-Path $uploadRoot "Latest-Beta-$version-staging"
$temporaryZip = Join-Path ([System.IO.Path]::GetTempPath()) "$artifactName.zip"

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Build artifact is missing: $jar"
}
if (Test-Path -LiteralPath $release) {
    throw "Release already exists and will not be overwritten: $release"
}
if (Test-Path -LiteralPath $staging) {
    throw "Staging directory already exists: $staging"
}

New-Item -ItemType Directory -Path $release | Out-Null
Copy-Item -LiteralPath $jar -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'README.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'START_HERE.md') -Destination $release
Copy-Item -LiteralPath (Join-Path $projectRoot 'BRANDING.md') -Destination $release
Copy-Item -Path (Join-Path $projectRoot 'docs\*.md') -Destination $release
Copy-Item -Path (Join-Path $projectRoot 'docs\*.html') -Destination $release

$assets = Join-Path $projectRoot 'docs\assets'
if (Test-Path -LiteralPath $assets -PathType Container) {
    Copy-Item -LiteralPath $assets -Destination (Join-Path $release 'assets') -Recurse
}

Compress-Archive -Path (Join-Path $release '*') `
    -DestinationPath $temporaryZip `
    -CompressionLevel Optimal `
    -Force
Copy-Item -LiteralPath $temporaryZip -Destination (Join-Path $release "$artifactName.zip")

New-Item -ItemType Directory -Path $staging | Out-Null
Copy-Item -Path (Join-Path $release '*') -Destination $staging -Recurse

if (Test-Path -LiteralPath $latest -PathType Container) {
    $latestJar = Get-ChildItem -LiteralPath $latest -Filter 'CatClans-v*.jar' |
        Select-Object -First 1
    if ($null -eq $latestJar -or $latestJar.BaseName -notmatch '^CatClans-v(.+)$') {
        throw "The existing Latest-Beta version could not be identified."
    }
    $previous = Join-Path $uploadRoot "Previous-$($Matches[1])"
    if (Test-Path -LiteralPath $previous) {
        throw "Previous archive already exists: $previous"
    }
    Move-Item -LiteralPath $latest -Destination $previous
}
Move-Item -LiteralPath $staging -Destination $latest

$normalizedUploadRoot = [System.IO.Path]::GetFullPath($uploadRoot)
$obsolete = Get-ChildItem -LiteralPath $uploadRoot -Directory -Filter 'Previous-*' |
    Sort-Object LastWriteTime, Name -Descending |
    Select-Object -Skip 2
foreach ($directory in $obsolete) {
    $resolved = [System.IO.Path]::GetFullPath($directory.FullName)
    if (-not $resolved.StartsWith(
            $normalizedUploadRoot + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Unsafe previous-release path was rejected: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

Write-Output "Release created: $release"
Write-Output "Latest-Beta updated; previous archives retained: maximum 2"
