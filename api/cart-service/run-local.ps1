[CmdletBinding()]
param(
    [string]$Profile = "local",

    [string]$ShutdownSignalFile,

    [string]$PidFile
)

$runner = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\scripts\run-local-service.ps1"

if (-not (Test-Path -LiteralPath $runner)) {
    throw "Could not find launcher helper at '$runner'."
}

& $runner `
    -ServiceName "cart-service" `
    -ModuleDir (Split-Path -Parent $MyInvocation.MyCommand.Path) `
    -RequiredEnvironmentVariables @("CART_MONGODB_URI") `
    -Profile $Profile `
    -ShutdownSignalFile $ShutdownSignalFile `
    -PidFile $PidFile

exit $LASTEXITCODE
