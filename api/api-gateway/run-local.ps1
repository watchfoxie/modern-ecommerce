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
    -ServiceName "api-gateway" `
    -ModuleDir (Split-Path -Parent $MyInvocation.MyCommand.Path) `
    -RequiredEnvironmentVariables @() `
    -Profile $Profile `
    -ShutdownSignalFile $ShutdownSignalFile `
    -PidFile $PidFile

exit $LASTEXITCODE
