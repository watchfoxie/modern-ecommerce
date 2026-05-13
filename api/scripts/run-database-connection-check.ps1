[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "auth-service",
        "user-service",
        "category-service",
        "product-service",
        "cart-service",
        "order-service"
    )]
    [string]$ServiceName,

    [string]$Profile = "local"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$serviceMap = @{
    "auth-service" = @{
        ModuleDir = Join-Path $repoRoot "api\auth-service"
        MainClass = "md.services.auth_service.connection.CheckDatabaseConnection"
    }
    "user-service" = @{
        ModuleDir = Join-Path $repoRoot "api\user-service"
        MainClass = "md.services.user_service.connection.CheckDatabaseConnection"
    }
    "category-service" = @{
        ModuleDir = Join-Path $repoRoot "api\category-service"
        MainClass = "md.services.category_service.connection.CheckDatabaseConnection"
    }
    "product-service" = @{
        ModuleDir = Join-Path $repoRoot "api\product-service"
        MainClass = "md.services.product_service.connection.CheckDatabaseConnection"
    }
    "cart-service" = @{
        ModuleDir = Join-Path $repoRoot "api\cart-service"
        MainClass = "md.services.cart_service.connection.CheckDatabaseConnection"
    }
    "order-service" = @{
        ModuleDir = Join-Path $repoRoot "api\order-service"
        MainClass = "md.services.order_service.connection.CheckDatabaseConnection"
    }
}

$serviceDefinition = $serviceMap[$ServiceName]
if ($null -eq $serviceDefinition) {
    throw "Unsupported service name '$ServiceName'."
}

$mavenWrapper = Join-Path $serviceDefinition.ModuleDir "mvnw.cmd"
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Could not find Maven wrapper at '$mavenWrapper'."
}

Push-Location $serviceDefinition.ModuleDir

try {
    $classPathFile = Join-Path $serviceDefinition.ModuleDir "target\database-connection-check.classpath"

    & $mavenWrapper `
        "-q" `
        "-DskipTests" `
        "compile" `
        "dependency:build-classpath" `
        "-DincludeScope=runtime" `
        "-Dmdep.outputFile=target\database-connection-check.classpath"

    if ($LASTEXITCODE -ne 0) {
        throw "Preparing the runtime classpath for '$ServiceName' failed with exit code $LASTEXITCODE."
    }

    $dependencyClasspath = ""
    if (Test-Path -LiteralPath $classPathFile) {
        $dependencyClasspath = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
    }

    $runtimeClasspathEntries = New-Object System.Collections.Generic.List[string]
    $null = $runtimeClasspathEntries.Add((Join-Path $serviceDefinition.ModuleDir "target\classes"))

    if (-not [string]::IsNullOrWhiteSpace($dependencyClasspath)) {
        foreach ($entry in ($dependencyClasspath -split ';')) {
            $trimmedEntry = $entry.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmedEntry)) {
                $null = $runtimeClasspathEntries.Add($trimmedEntry)
            }
        }
    }

    $runtimeClasspath = [string]::Join(';', $runtimeClasspathEntries)

    & java `
        "-Dspring.profiles.active=$Profile" `
        "-cp" `
        $runtimeClasspath `
        $serviceDefinition.MainClass

    if ($LASTEXITCODE -ne 0) {
        throw "Database connection check for '$ServiceName' failed with exit code $LASTEXITCODE."
    }
}
finally {
    if ($null -ne $classPathFile) {
        Remove-Item -LiteralPath $classPathFile -ErrorAction SilentlyContinue
    }
    Pop-Location
}
