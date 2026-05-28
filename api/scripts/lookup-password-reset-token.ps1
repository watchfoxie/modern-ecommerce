[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Email,

    [string]$Profile = "local"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$moduleDir = Join-Path $repoRoot "api\auth-service"
$mavenWrapper = Join-Path $moduleDir "mvnw.cmd"

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Could not find Maven wrapper at '$mavenWrapper'."
}

Push-Location $moduleDir

try {
    $classPathFile = Join-Path $moduleDir "target\password-reset-lookup.classpath"

    & $mavenWrapper `
        "-q" `
        "-DskipTests" `
        "compile" `
        "dependency:build-classpath" `
        "-DincludeScope=runtime" `
        "-Dmdep.outputFile=target\password-reset-lookup.classpath"

    if ($LASTEXITCODE -ne 0) {
        throw "Preparing the runtime classpath for auth-service failed with exit code $LASTEXITCODE."
    }

    $dependencyClasspath = ""
    if (Test-Path -LiteralPath $classPathFile) {
        $dependencyClasspath = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
    }

    $runtimeClasspathEntries = New-Object System.Collections.Generic.List[string]
    $null = $runtimeClasspathEntries.Add((Join-Path $moduleDir "target\classes"))

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
        md.services.auth_service.connection.LookupPasswordResetToken `
        "--email=$Email"

    if ($LASTEXITCODE -ne 0) {
        throw "Password reset token lookup failed with exit code $LASTEXITCODE."
    }
}
finally {
    if ($null -ne $classPathFile) {
        Remove-Item -LiteralPath $classPathFile -ErrorAction SilentlyContinue
    }
    Pop-Location
}
