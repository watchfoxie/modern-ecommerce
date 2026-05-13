[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ServiceName,

    [Parameter(Mandatory = $true)]
    [string]$ModuleDir,

    [string[]]$RequiredEnvironmentVariables = @(),

    [string]$Profile = "local",

    [string]$ShutdownSignalFile,

    [string]$PidFile
)

$ErrorActionPreference = "Stop"

if ([Console]::IsInputRedirected) {
    throw "run-local.ps1 requires an interactive console."
}

$script:moduleDir = (Resolve-Path -LiteralPath $ModuleDir).Path
$script:repoRoot = Split-Path -Parent (Split-Path -Parent $script:moduleDir)
$script:mavenWrapper = Join-Path $script:moduleDir "mvnw.cmd"
$script:sourceRoot = Join-Path $script:moduleDir "src\main\java"
$script:started = $false
$script:stopped = $false

if (-not (Test-Path -LiteralPath $script:mavenWrapper)) {
    throw "Could not find Maven wrapper at '$script:mavenWrapper'."
}

function Get-EnvironmentFiles {
    $profileEnvFile = Join-Path $script:repoRoot ".env.$Profile"
    $localEnvFile = Join-Path $script:repoRoot ".env.local"
    $envFiles = New-Object System.Collections.Generic.List[string]

    if (Test-Path -LiteralPath $profileEnvFile) {
        $null = $envFiles.Add($profileEnvFile)
    }

    if (($localEnvFile -ne $profileEnvFile) -and (Test-Path -LiteralPath $localEnvFile)) {
        $null = $envFiles.Add($localEnvFile)
    }

    return $envFiles
}

function Import-DotEnvFiles {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Paths
    )

    foreach ($path in $Paths) {
        foreach ($line in Get-Content -LiteralPath $path) {
            $trimmed = $line.Trim()

            if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
                continue
            }

            $separatorIndex = $line.IndexOf("=")
            if ($separatorIndex -lt 1) {
                continue
            }

            $name = $line.Substring(0, $separatorIndex).Trim()
            if ([string]::IsNullOrWhiteSpace($name)) {
                continue
            }

            if (Test-Path -LiteralPath "Env:$name") {
                continue
            }

            $value = $line.Substring($separatorIndex + 1)
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Get-MissingEnvironmentVariables {
    param(
        [string[]]$Names
    )

    $missing = New-Object System.Collections.Generic.List[string]

    foreach ($name in $Names) {
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }

        $value = (Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue).Value
        if ([string]::IsNullOrWhiteSpace($value)) {
            $null = $missing.Add($name)
        }
    }

    return $missing
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

function Get-MainClass {
    if (-not (Test-Path -LiteralPath $script:sourceRoot)) {
        throw "Could not find source directory at '$script:sourceRoot'."
    }

    $candidates = Get-ChildItem -Path $script:sourceRoot -Filter "*Application.java" -Recurse
    if (-not $candidates) {
        throw "Could not infer a Spring Boot application class under '$script:sourceRoot'."
    }

    $selectedFile = $candidates | Where-Object {
        (Get-Content -LiteralPath $_.FullName -Raw) -match "@SpringBootApplication"
    } | Select-Object -First 1

    if ($null -eq $selectedFile) {
        $selectedFile = $candidates | Select-Object -First 1
    }

    $packageName = $null
    $className = $selectedFile.BaseName

    foreach ($line in Get-Content -LiteralPath $selectedFile.FullName) {
        if (($null -eq $packageName) -and ($line -match "^\s*package\s+([A-Za-z0-9_.]+)\s*;")) {
            $packageName = $Matches[1]
        }

        if ($line -match "^\s*public\s+class\s+([A-Za-z0-9_]+)\b") {
            $className = $Matches[1]
            break
        }
    }

    if ([string]::IsNullOrWhiteSpace($packageName)) {
        return $className
    }

    return "$packageName.$className"
}

function Stop-ServiceProcess {
    if (-not $script:started -or $script:stopped) {
        return
    }

    Invoke-Maven -Arguments @("spring-boot:stop")
    $script:stopped = $true
}

$environmentFiles = @(Get-EnvironmentFiles)
if ($environmentFiles.Count -gt 0) {
    Import-DotEnvFiles -Paths $environmentFiles
}

$missingEnvironmentVariables = @(Get-MissingEnvironmentVariables -Names $RequiredEnvironmentVariables)
if ($missingEnvironmentVariables.Count -gt 0) {
    $expectedLocations = if ($environmentFiles.Count -gt 0) {
        ($environmentFiles | ForEach-Object { "'$_'" }) -join ", "
    }
    else {
        "the current environment"
    }

    throw "Missing required environment variables for ${ServiceName}: $($missingEnvironmentVariables -join ', '). Define them in $expectedLocations or export them before launching the service."
}

$mainClass = Get-MainClass
Set-LauncherPidFile

Push-Location $script:moduleDir
$previousTreatControlCAsInput = [Console]::TreatControlCAsInput

try {
    Invoke-Maven -Arguments @("compile")
    Invoke-Maven -Arguments @(
        "spring-boot:start",
        "-Dspring-boot.run.profiles=$Profile",
        "-Dspring-boot.run.main-class=$mainClass"
    )
    $script:started = $true

    [Console]::TreatControlCAsInput = $true
    Write-Host "$ServiceName is running. Press Ctrl+C to stop gracefully."
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
        Stop-ServiceProcess
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
