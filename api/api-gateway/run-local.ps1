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
$script:mavenWrapper = Join-Path $script:moduleDir "mvnw.cmd"
$script:started = $false
$script:stopped = $false

if (-not (Test-Path $script:mavenWrapper)) {
    throw "Could not find Maven wrapper at '$script:mavenWrapper'."
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $script:mavenWrapper @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed with exit code ${LASTEXITCODE}: mvnw.cmd $($Arguments -join ' ')"
    }
}

function Set-LauncherPidFile {
    if ([string]::IsNullOrWhiteSpace($PidFile)) {
        return
    }

    $parentDirectory = Split-Path -Parent $PidFile
    if (-not [string]::IsNullOrWhiteSpace($parentDirectory)) {
        $null = New-Item -ItemType Directory -Path $parentDirectory -Force
    }

    Set-Content -LiteralPath $PidFile -Value "$PID" -NoNewline
}

function Remove-LauncherPidFile {
    if ([string]::IsNullOrWhiteSpace($PidFile)) {
        return
    }

    Remove-Item -LiteralPath $PidFile -ErrorAction SilentlyContinue
}

function Stop-ApiGateway {
    if (-not $script:started -or $script:stopped) {
        return
    }

    Invoke-Maven -Arguments @("spring-boot:stop")
    $script:stopped = $true
}

Set-LauncherPidFile

Push-Location $script:moduleDir
$previousTreatControlCAsInput = [Console]::TreatControlCAsInput

try {
    Invoke-Maven -Arguments @("compile")
    Invoke-Maven -Arguments @("spring-boot:start", "-Dspring-boot.run.profiles=$Profile")
    $script:started = $true

    [Console]::TreatControlCAsInput = $true
    Write-Host "api-gateway is running. Press Ctrl+C to stop gracefully."
    Write-Host "Press Q or Enter if your terminal does not forward Ctrl+C as input."

    while ($true) {
        if ((-not [string]::IsNullOrWhiteSpace($ShutdownSignalFile)) -and (Test-Path -LiteralPath $ShutdownSignalFile)) {
            break
        }

        if (-not [Console]::KeyAvailable) {
            Start-Sleep -Milliseconds 200
            continue
        }

        $key = [Console]::ReadKey($true)
        $isCtrlC = ($key.Key -eq [ConsoleKey]::C) -and (($key.Modifiers -band [ConsoleModifiers]::Control) -ne 0)
        $isFallbackStopKey = ($key.Key -eq [ConsoleKey]::Q) -or ($key.Key -eq [ConsoleKey]::Enter)

        if ($isCtrlC -or $isFallbackStopKey) {
            break
        }
    }
}
finally {
    [Console]::TreatControlCAsInput = $previousTreatControlCAsInput

    try {
        Stop-ApiGateway
    }
    finally {
        try {
            Pop-Location
        }
        finally {
            Remove-LauncherPidFile
        }
    }
}

exit 0
