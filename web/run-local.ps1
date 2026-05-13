[CmdletBinding()]
param(
    [string]$Profile = "local",

    [string]$ShutdownSignalFile,

    [string]$PidFile
)

$ErrorActionPreference = "Stop"

if ([Console]::IsInputRedirected) {
    throw "run-local.ps1 requires an interactive console."
}

$script:moduleDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:runner = Join-Path $script:moduleDir "run-local.mjs"
$script:vitePackage = Join-Path $script:moduleDir "node_modules\vite\package.json"

if (-not (Test-Path -LiteralPath $script:runner)) {
    throw "Could not find web launcher at '$script:runner'."
}

if (-not (Test-Path -LiteralPath $script:vitePackage)) {
    throw "Could not find Vite dependencies at '$script:vitePackage'. Run 'npm install' in '$script:moduleDir' first."
}

$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($null -eq $nodeCommand) {
    throw "Could not find 'node' on PATH."
}

$arguments = @(
    $script:runner,
    "--mode=$Profile"
)

if (-not [string]::IsNullOrWhiteSpace($ShutdownSignalFile)) {
    $arguments += @("--shutdown-signal-file", $ShutdownSignalFile)
}

if (-not [string]::IsNullOrWhiteSpace($PidFile)) {
    $arguments += @("--pid-file", $PidFile)
}

Push-Location $script:moduleDir

try {
    & $nodeCommand.Source @arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Web launcher exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
