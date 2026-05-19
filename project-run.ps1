[CmdletBinding()]
param(
    [string]$Profile = "local",

    [ValidateRange(0, 300)]
    [int]$StartupDelaySeconds = 20,

    [ValidateRange(5, 600)]
    [int]$ShutdownTimeoutSeconds = 60,

    [ValidateRange(0, 120)]
    [int]$ForcedTerminationGraceSeconds = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([Console]::IsInputRedirected) {
    throw "project-run.ps1 requires an interactive console."
}

$script:repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:stateDir = Join-Path ([System.IO.Path]::GetTempPath()) ("modern-ecommerce-project-run-" + [Guid]::NewGuid().ToString("N"))
$script:cleanupStarted = $false
$script:rabbitMqPreflightStarted = $false

$components = @(
    [pscustomobject]@{
        Name                         = "service-registry"
        ScriptPath                   = Join-Path $script:repoRoot "api\service-registry\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\service-registry"
        PortEnvironmentVariable      = "SERVICE_REGISTRY_PORT"
        DefaultPort                  = 8761
        RequiredEnvironmentVariables = @()
        ExternalPrerequisite         = "Eureka must be able to bind its local port."
    },
    [pscustomobject]@{
        Name                         = "auth-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\auth-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\auth-service"
        PortEnvironmentVariable      = "AUTH_SERVICE_PORT"
        DefaultPort                  = 8081
        RequiredEnvironmentVariables = @("AUTH_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through AUTH_MONGODB_URI."
    },
    [pscustomobject]@{
        Name                         = "user-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\user-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\user-service"
        PortEnvironmentVariable      = "USER_SERVICE_PORT"
        DefaultPort                  = 8082
        RequiredEnvironmentVariables = @("USER_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through USER_MONGODB_URI."
    },
    [pscustomobject]@{
        Name                         = "category-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\category-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\category-service"
        PortEnvironmentVariable      = "CATEGORY_SERVICE_PORT"
        DefaultPort                  = 8083
        RequiredEnvironmentVariables = @("CATEGORY_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through CATEGORY_MONGODB_URI."
    },
    [pscustomobject]@{
        Name                         = "product-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\product-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\product-service"
        PortEnvironmentVariable      = "PRODUCT_SERVICE_PORT"
        DefaultPort                  = 8084
        RequiredEnvironmentVariables = @("PRODUCT_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through PRODUCT_MONGODB_URI."
    },
    [pscustomobject]@{
        Name                         = "cart-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\cart-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\cart-service"
        PortEnvironmentVariable      = "CART_SERVICE_PORT"
        DefaultPort                  = 8085
        RequiredEnvironmentVariables = @("CART_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through CART_MONGODB_URI."
    },
    [pscustomobject]@{
        Name                         = "notification-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\notification-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\notification-service"
        PortEnvironmentVariable      = "NOTIFICATION_SERVICE_PORT"
        DefaultPort                  = 8087
        RequiredEnvironmentVariables = @()
        ExternalPrerequisite         = "RabbitMQ must be reachable through RABBITMQ_* settings. SMTP credentials are required only when NOTIFICATION_MAIL_ENABLED=true."
    },
    [pscustomobject]@{
        Name                         = "order-service"
        ScriptPath                   = Join-Path $script:repoRoot "api\order-service\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\order-service"
        PortEnvironmentVariable      = "ORDER_SERVICE_PORT"
        DefaultPort                  = 8086
        RequiredEnvironmentVariables = @("ORDER_MONGODB_URI")
        ExternalPrerequisite         = "MongoDB must be reachable through ORDER_MONGODB_URI and RabbitMQ must be reachable through RABBITMQ_* settings."
    },
    [pscustomobject]@{
        Name                         = "api-gateway"
        ScriptPath                   = Join-Path $script:repoRoot "api\api-gateway\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "api\api-gateway"
        PortEnvironmentVariable      = "API_GATEWAY_PORT"
        DefaultPort                  = 8080
        RequiredEnvironmentVariables = @()
        ExternalPrerequisite         = "Downstream services should have time to register in Eureka before the frontend starts."
    },
    [pscustomobject]@{
        Name                         = "web"
        ScriptPath                   = Join-Path $script:repoRoot "web\run-local.ps1"
        WorkingDirectory             = Join-Path $script:repoRoot "web"
        PortEnvironmentVariable      = "VITE_PORT"
        DefaultPort                  = 5173
        RequiredEnvironmentVariables = @()
        ExternalPrerequisite         = "Node.js and installed frontend dependencies are required."
    }
)

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
            if ([string]::IsNullOrWhiteSpace($name) -or (Test-Path -LiteralPath "Env:$name")) {
                continue
            }

            $value = $line.Substring($separatorIndex + 1)
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Get-ComposeBaseArguments {
    $composeArguments = New-Object System.Collections.Generic.List[string]
    $localEnvFile = Join-Path $script:repoRoot ".env.local"

    if (Test-Path -LiteralPath $localEnvFile) {
        $composeArguments.Add("--env-file") | Out-Null
        $composeArguments.Add($localEnvFile) | Out-Null
    }

    $composeArguments.Add("-f") | Out-Null
    $composeArguments.Add((Join-Path $script:repoRoot "compose.yaml")) | Out-Null

    return $composeArguments.ToArray()
}

function Get-ComposeRabbitMqArguments {
    $composeArguments = New-Object System.Collections.Generic.List[string]
    $composeArguments.AddRange([string[]](Get-ComposeBaseArguments))

    $debugComposeFile = Join-Path $script:repoRoot "compose.debug.yaml"
    if (Test-Path -LiteralPath $debugComposeFile) {
        $composeArguments.Add("-f") | Out-Null
        $composeArguments.Add($debugComposeFile) | Out-Null
    }

    return $composeArguments.ToArray()
}

function Invoke-DockerCompose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $dockerArguments = @("compose") + $Arguments
    & docker @dockerArguments

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed with exit code ${LASTEXITCODE}: docker $($dockerArguments -join ' ')"
    }
}

function Invoke-RabbitMqPreflight {
    $skipPreflight = (Get-EnvironmentValue -Name "PROJECT_RUN_SKIP_RABBITMQ_PREFLIGHT")
    if ($skipPreflight -in @("1", "true", "TRUE", "yes", "YES")) {
        Write-Host "Skipping RabbitMQ preflight because PROJECT_RUN_SKIP_RABBITMQ_PREFLIGHT is enabled."
        return
    }

    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Could not find 'docker' on PATH. It is required to start RabbitMQ for local sequential runs."
    }

    $localEnvFile = Join-Path $script:repoRoot ".env.local"
    if (-not (Test-Path -LiteralPath $localEnvFile)) {
        throw ".env.local is required for RabbitMQ preflight because the hardened Compose profile fails fast on required secrets."
    }

    $composeArguments = @(Get-ComposeRabbitMqArguments)
    $runningServices = & docker @(@("compose") + $composeArguments + @("ps", "--status", "running", "--services", "rabbitmq"))
    $rabbitMqAlreadyRunning = ($LASTEXITCODE -eq 0) -and (($runningServices | Where-Object { $_ -eq "rabbitmq" }) -ne $null)

    Write-Host "Ensuring RabbitMQ is available through Docker Compose..."
    Invoke-DockerCompose -Arguments ($composeArguments + @("up", "-d", "--build", "rabbitmq"))
    if (-not $rabbitMqAlreadyRunning) {
        $script:rabbitMqPreflightStarted = $true
    }

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        & docker @(@("compose") + $composeArguments + @("exec", "-T", "rabbitmq", "sh", "-c", "rabbitmq-diagnostics -q ping")) | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "RabbitMQ preflight passed."
            return
        }

        Start-Sleep -Seconds 3
    }

    throw "RabbitMQ did not become healthy within the preflight timeout."
}

function Stop-RabbitMqPreflight {
    if (-not $script:rabbitMqPreflightStarted) {
        return
    }

    $stopOnExit = Get-EnvironmentValue -Name "PROJECT_RUN_STOP_RABBITMQ_ON_EXIT"
    if ($stopOnExit -in @("0", "false", "FALSE", "no", "NO")) {
        Write-Host "Leaving RabbitMQ running because PROJECT_RUN_STOP_RABBITMQ_ON_EXIT is disabled."
        return
    }

    try {
        Write-Host "Stopping RabbitMQ preflight container..."
        Invoke-DockerCompose -Arguments (@(Get-ComposeRabbitMqArguments) + @("stop", "rabbitmq"))
    } catch {
        Write-Warning "RabbitMQ preflight cleanup failed: $($_.Exception.Message)"
    }
}

function Get-EnvironmentValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $item = Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
    if ($null -eq $item) {
        return $null
    }

    return $item.Value
}

function Get-ResolvedPort {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Component
    )

    $configuredValue = Get-EnvironmentValue -Name $Component.PortEnvironmentVariable
    if ([string]::IsNullOrWhiteSpace($configuredValue)) {
        return [int]$Component.DefaultPort
    }

    $resolvedPort = 0
    if (-not [int]::TryParse($configuredValue, [ref]$resolvedPort)) {
        throw "Environment variable '$($Component.PortEnvironmentVariable)' for $($Component.Name) must be an integer. Current value: '$configuredValue'."
    }

    return $resolvedPort
}

function Test-PortInUse {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    return $null -ne ($listeners | Where-Object { $_.Port -eq $Port } | Select-Object -First 1)
}

function Test-ProcessAlive {
    param(
        [int]$ProcessId
    )

    if ($ProcessId -le 0) {
        return $false
    }

    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Get-PidFromFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $rawValue = (Get-Content -LiteralPath $Path -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return $null
    }

    $parsedValue = 0
    if (-not [int]::TryParse($rawValue, [ref]$parsedValue)) {
        return $null
    }

    return $parsedValue
}

function Convert-ToProcessArgument {
    param(
        [AllowNull()]
        [string]$Value
    )

    if ($null -eq $Value) {
        return '""'
    }

    return '"' + ($Value -replace '"', '\"') + '"'
}

function Test-ComponentRunning {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Instance
    )

    $launcherPid = Get-PidFromFile -Path $Instance.PidFile
    if ($null -ne $launcherPid) {
        return Test-ProcessAlive -ProcessId $launcherPid
    }

    return Test-ProcessAlive -ProcessId $Instance.TerminalPid
}

function Get-ComponentProcessIds {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Instance
    )

    $processIds = New-Object System.Collections.Generic.List[int]

    foreach ($candidate in @($Instance.LauncherPid, $Instance.TerminalPid)) {
        if (($candidate -gt 0) -and ($processIds -notcontains $candidate)) {
            $processIds.Add($candidate) | Out-Null
        }
    }

    return $processIds
}

function Wait-ForComponentStop {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Instance,

        [ValidateRange(0, 600)]
        [int]$TimeoutSeconds
    )

    if (-not (Test-ComponentRunning -Instance $Instance)) {
        return $true
    }

    if ($TimeoutSeconds -le 0) {
        return $false
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-ComponentRunning -Instance $Instance)) {
            return $true
        }

        Start-Sleep -Milliseconds 500
    }

    return -not (Test-ComponentRunning -Instance $Instance)
}

function Request-ProcessWindowClose {
    param(
        [int]$ProcessId
    )

    if ($ProcessId -le 0) {
        return $false
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return $false
    }

    try {
        return $process.CloseMainWindow()
    }
    catch {
        return $false
    }
}

function Wait-ForLauncherRegistration {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$TerminalProcess,

        [Parameter(Mandatory = $true)]
        [string]$PidFile,

        [Parameter(Mandatory = $true)]
        [string]$ComponentName
    )

    $deadline = (Get-Date).AddSeconds(20)

    while ((Get-Date) -lt $deadline) {
        $launcherPid = Get-PidFromFile -Path $PidFile
        if ($null -ne $launcherPid) {
            return $launcherPid
        }

        $TerminalProcess.Refresh()
        if ($TerminalProcess.HasExited) {
            break
        }

        Start-Sleep -Milliseconds 200
    }

    $TerminalProcess.Refresh()
    if ($TerminalProcess.HasExited) {
        throw "$ComponentName exited before registering its PID file. Exit code: $($TerminalProcess.ExitCode)."
    }

    throw "Timed out while waiting for $ComponentName to register its PID file."
}

function Invoke-PreflightChecks {
    $environmentFiles = @(Get-EnvironmentFiles)
    if ($environmentFiles.Count -gt 0) {
        Import-DotEnvFiles -Paths $environmentFiles
    }

    Invoke-RabbitMqPreflight

    if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
        throw "Could not find 'node' on PATH. It is required to launch the Vite frontend."
    }

    $missingEnvironmentReports = New-Object System.Collections.Generic.List[string]
    $busyPortReports = New-Object System.Collections.Generic.List[string]

    foreach ($component in $components) {
        if (-not (Test-Path -LiteralPath $component.ScriptPath)) {
            throw "Could not find launcher script for $($component.Name) at '$($component.ScriptPath)'."
        }

        $missingVariables = @()
        foreach ($name in $component.RequiredEnvironmentVariables) {
            $value = Get-EnvironmentValue -Name $name
            if ([string]::IsNullOrWhiteSpace($value)) {
                $missingVariables += $name
            }
        }

        if ($missingVariables.Count -gt 0) {
            $missingEnvironmentReports.Add("$($component.Name): $($missingVariables -join ', ')") | Out-Null
        }

        $resolvedPort = Get-ResolvedPort -Component $component
        if (Test-PortInUse -Port $resolvedPort) {
            $busyPortReports.Add("$($component.Name): port $resolvedPort is already in use") | Out-Null
        }

        $component | Add-Member -NotePropertyName ResolvedPort -NotePropertyValue $resolvedPort -Force
    }

    if ($missingEnvironmentReports.Count -gt 0) {
        throw "Missing required environment variables:`n - $($missingEnvironmentReports -join "`n - ")"
    }

    if ($busyPortReports.Count -gt 0) {
        throw "Port preflight failed:`n - $($busyPortReports -join "`n - ")"
    }
}

function Start-ComponentWindow {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Component
    )

    $stopFile = Join-Path $script:stateDir "$($Component.Name).stop"
    $pidFile = Join-Path $script:stateDir "$($Component.Name).pid"

    Remove-Item -LiteralPath $stopFile, $pidFile -ErrorAction SilentlyContinue

    $argumentList = @(
        "-NoLogo",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        (Convert-ToProcessArgument -Value $Component.ScriptPath),
        "-Profile",
        (Convert-ToProcessArgument -Value $Profile),
        "-ShutdownSignalFile",
        (Convert-ToProcessArgument -Value $stopFile),
        "-PidFile",
        (Convert-ToProcessArgument -Value $pidFile)
    )

    $terminalProcess = Start-Process -FilePath "powershell.exe" -ArgumentList $argumentList -WorkingDirectory $Component.WorkingDirectory -PassThru
    $launcherPid = Wait-ForLauncherRegistration -TerminalProcess $terminalProcess -PidFile $pidFile -ComponentName $Component.Name

    return [pscustomobject]@{
        Name                 = $Component.Name
        TerminalPid          = $terminalProcess.Id
        LauncherPid          = $launcherPid
        StopFile             = $stopFile
        PidFile              = $pidFile
        Port                 = $Component.ResolvedPort
        ExternalPrerequisite = $Component.ExternalPrerequisite
    }
}

function Stop-ComponentInstance {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Instance
    )

    Write-Host "Stopping $($Instance.Name)..."

    if (-not (Test-ComponentRunning -Instance $Instance)) {
        Write-Host "$($Instance.Name) is already stopped."
        return
    }

    $null = New-Item -ItemType File -Path $Instance.StopFile -Force
    $deadline = (Get-Date).AddSeconds($ShutdownTimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        if (-not (Test-ComponentRunning -Instance $Instance)) {
            Write-Host "$($Instance.Name) stopped gracefully."
            return
        }

        Start-Sleep -Seconds 1
    }

    Write-Warning "$($Instance.Name) did not stop within $ShutdownTimeoutSeconds seconds. Attempting controlled termination before forcing it."

    $processIds = @(Get-ComponentProcessIds -Instance $Instance)

    if (Request-ProcessWindowClose -ProcessId $Instance.TerminalPid) {
        if (Wait-ForComponentStop -Instance $Instance -TimeoutSeconds $ForcedTerminationGraceSeconds) {
            Write-Host "$($Instance.Name) stopped after the console-close fallback."
            return
        }
    }

    if (($Instance.LauncherPid -gt 0) -and (Test-ProcessAlive -ProcessId $Instance.LauncherPid)) {
        Stop-Process -Id $Instance.LauncherPid -ErrorAction SilentlyContinue
        if (Wait-ForComponentStop -Instance $Instance -TimeoutSeconds $ForcedTerminationGraceSeconds) {
            Write-Host "$($Instance.Name) stopped after the launcher-termination fallback."
            return
        }
    }

    if (($Instance.TerminalPid -gt 0) -and (Test-ProcessAlive -ProcessId $Instance.TerminalPid)) {
        Stop-Process -Id $Instance.TerminalPid -ErrorAction SilentlyContinue
        if (Wait-ForComponentStop -Instance $Instance -TimeoutSeconds $ForcedTerminationGraceSeconds) {
            Write-Host "$($Instance.Name) stopped after the terminal-termination fallback."
            return
        }
    }

    foreach ($processId in $processIds) {
        if (Test-ProcessAlive -ProcessId $processId) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-Cleanup {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[object]]$StartedComponents
    )

    if ($script:cleanupStarted) {
        return
    }

    $script:cleanupStarted = $true

    for ($index = $StartedComponents.Count - 1; $index -ge 0; $index--) {
        Stop-ComponentInstance -Instance $StartedComponents[$index]
    }

    Stop-RabbitMqPreflight
    Remove-Item -LiteralPath $script:stateDir -Recurse -Force -ErrorAction SilentlyContinue
}

$startedComponents = New-Object 'System.Collections.Generic.List[object]'
$previousTreatControlCAsInput = [Console]::TreatControlCAsInput

New-Item -ItemType Directory -Path $script:stateDir -Force | Out-Null

try {
    Invoke-PreflightChecks

    Write-Host "Launching modern-ecommerce in profile '$Profile'."
    Write-Host "Each component will open in a dedicated console window."
    Write-Host "External infrastructure is not started automatically:"
    foreach ($component in $components) {
        Write-Host " - $($component.Name) -> port $($component.ResolvedPort): $($component.ExternalPrerequisite)"
    }

    for ($index = 0; $index -lt $components.Count; $index++) {
        $component = $components[$index]

        Write-Host "Starting $($component.Name) on port $($component.ResolvedPort)..."
        $instance = Start-ComponentWindow -Component $component
        $startedComponents.Add($instance) | Out-Null

        $failedDuringStartup = $startedComponents | Where-Object { -not (Test-ComponentRunning -Instance $_) } | Select-Object -First 1
        if ($null -ne $failedDuringStartup) {
            throw "$($failedDuringStartup.Name) exited during the startup sequence."
        }

        if ($index -lt ($components.Count - 1) -and $StartupDelaySeconds -gt 0) {
            Write-Host "Waiting $StartupDelaySeconds seconds before launching the next component..."
            Start-Sleep -Seconds $StartupDelaySeconds

            $failedDuringStartup = $startedComponents | Where-Object { -not (Test-ComponentRunning -Instance $_) } | Select-Object -First 1
            if ($null -ne $failedDuringStartup) {
                throw "$($failedDuringStartup.Name) exited during the startup sequence."
            }
        }
    }

    [Console]::TreatControlCAsInput = $true
    Write-Host "All components are running. Press Ctrl+C, Q, or Enter to stop them in reverse order."

    while ($true) {
        $unexpectedExit = $startedComponents | Where-Object { -not (Test-ComponentRunning -Instance $_) } | Select-Object -First 1
        if ($null -ne $unexpectedExit) {
            Write-Warning "$($unexpectedExit.Name) exited unexpectedly. Initiating graceful shutdown."
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
    Invoke-Cleanup -StartedComponents $startedComponents
}

exit 0
