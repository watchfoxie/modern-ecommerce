[CmdletBinding()]
param(
    [string]$EnvFile = ".env.local",
    [switch]$KeepStack,
    [switch]$SkipOrderFlow,
    [ValidateRange(60, 900)]
    [int]$HealthTimeoutSeconds = 360
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:ComposeFile = Join-Path $script:RepoRoot "compose.yaml"
$script:EnvFilePath = Join-Path $script:RepoRoot $EnvFile
$script:ComposeArgs = @("--env-file", $script:EnvFilePath, "-f", $script:ComposeFile)

if (-not (Test-Path -LiteralPath $script:EnvFilePath)) {
    throw "Environment file '$script:EnvFilePath' was not found."
}

function Import-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1)
        if (-not [string]::IsNullOrWhiteSpace($name) -and -not (Test-Path -LiteralPath "Env:$name")) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Get-EnvOrDefault {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$DefaultValue
    )

    $item = Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
    if ($null -eq $item -or [string]::IsNullOrWhiteSpace($item.Value)) {
        return $DefaultValue
    }

    return $item.Value
}

function Invoke-Compose {
    $dockerArgs = @("compose") + $script:ComposeArgs + $args
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed with exit code ${LASTEXITCODE}: docker $($dockerArgs -join ' ')"
    }
}

function Invoke-ComposeOutput {
    $dockerArgs = @("compose") + $script:ComposeArgs + $args
    $output = & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed with exit code ${LASTEXITCODE}: docker $($dockerArgs -join ' ')"
    }

    return ($output | Out-String).Trim()
}

function Wait-ContainerHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [Parameter(Mandatory = $true)][datetime]$Deadline
    )

    while ((Get-Date) -lt $Deadline) {
        $status = (& docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerName 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $status -eq "healthy") {
            return
        }

        Start-Sleep -Seconds 5
    }

    throw "Container '$ContainerName' did not become healthy within the timeout."
}

function Invoke-HttpJson {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Method = "GET",
        [object]$Body = $null
    )

    $parameters = @{
        Uri         = $Uri
        Method      = $Method
        Headers     = $Headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $parameters.Body = ($Body | ConvertTo-Json -Depth 12)
    }

    return Invoke-RestMethod @parameters
}

function Get-HttpStatusFromError {
    param([Parameter(Mandatory = $true)]$ErrorRecord)

    $response = $ErrorRecord.Exception.Response
    if ($null -ne $response -and $null -ne $response.StatusCode) {
        return [int]$response.StatusCode
    }

    return $null
}

function Test-TransientHttpStatus {
    param([int]$StatusCode)

    return $StatusCode -in @(500, 502, 503, 504)
}

function Invoke-TransientHttpJson {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Method = "GET",
        [object]$Body = $null,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastStatus = $null

    while ((Get-Date) -lt $deadline) {
        try {
            return Invoke-HttpJson -Uri $Uri -Headers $Headers -Method $Method -Body $Body
        }
        catch {
            $lastStatus = Get-HttpStatusFromError -ErrorRecord $_
            if (-not (Test-TransientHttpStatus -StatusCode $lastStatus)) {
                throw
            }
        }

        Start-Sleep -Seconds 5
    }

    throw "HTTP request to $Uri did not recover from transient status $lastStatus within $TimeoutSeconds seconds."
}

function Assert-HttpStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][int]$ExpectedStatus,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastStatus = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing
            $lastStatus = [int]$response.StatusCode
            if ($lastStatus -eq $ExpectedStatus) {
                return
            }
        }
        catch [System.Net.WebException] {
            if ($null -ne $_.Exception.Response) {
                $errorResponse = [System.Net.HttpWebResponse]$_.Exception.Response
                try {
                    $lastStatus = [int]$errorResponse.StatusCode
                    if ($lastStatus -eq $ExpectedStatus) {
                        return
                    }
                }
                finally {
                    $errorResponse.Dispose()
                }
            }
            else {
                $lastStatus = $_.Exception.Message
            }
        }
        catch {
            $lastStatus = $_.Exception.Message
        }

        Start-Sleep -Seconds 3
    }

    throw "Expected HTTP $ExpectedStatus for $Uri, got $lastStatus."
}

function Assert-InternalReadiness {
    param(
        [Parameter(Mandatory = $true)][string]$Service,
        [Parameter(Mandatory = $true)][string]$Port
    )

    $output = Invoke-ComposeOutput exec -T $Service sh -c "wget -qO- http://127.0.0.1:$Port/actuator/health/readiness"
    if ($output -notmatch '"status":"UP"') {
        throw "Service '$Service' readiness response did not report UP. Response: $output"
    }
}

function Assert-RabbitMqTopology {
    $exchange = Get-EnvOrDefault -Name "ORDER_EVENTS_EXCHANGE" -DefaultValue "modern-ecommerce.events"
    $queue = Get-EnvOrDefault -Name "ORDER_CREATED_QUEUE" -DefaultValue "notification.order-created.v1"
    $dlq = Get-EnvOrDefault -Name "ORDER_CREATED_DLQ_QUEUE" -DefaultValue "notification.order-created.v1.dlq"

    Invoke-Compose exec -T rabbitmq sh -c "rabbitmqctl list_exchanges name | grep -Fx '$exchange'"
    Invoke-Compose exec -T rabbitmq sh -c "rabbitmqctl list_queues name | grep -Fx '$queue'"
    Invoke-Compose exec -T rabbitmq sh -c "rabbitmqctl list_queues name | grep -Fx '$dlq'"
    Invoke-Compose exec -T rabbitmq sh -c "rabbitmqctl list_bindings source_name destination_name routing_key | grep '$queue'"
}

function Invoke-OrderCreatedFlow {
    param([Parameter(Mandatory = $true)][string]$BaseUrl)

    $productPage = Invoke-HttpJson -Uri "$BaseUrl/api/product-service/products?page=0&size=1"
    $products = if ($productPage.PSObject.Properties.Name -contains "data") { @($productPage.data) } else { @($productPage.content) }
    $product = @($products)[0]
    if ($null -eq $product) {
        throw "Product catalog returned no products. Enable APP_DATA_SEED_ENABLED or provide seeded MongoDB data."
    }

    $password = "Phase7!Pass123"
    $runId = $null
    $email = $null
    $signUpCompleted = $false
    $deadline = (Get-Date).AddSeconds(120)
    $lastStatus = $null

    while ((Get-Date) -lt $deadline) {
        $runId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
        $email = "phase7-$runId@example.test"
        try {
            Invoke-HttpJson -Uri "$BaseUrl/api/auth-service/sign-up" -Method "POST" -Body @{
                firstName = "Phase"
                lastName  = "Seven"
                email     = $email
                password  = $password
            } | Out-Null
            $signUpCompleted = $true
            break
        }
        catch {
            $lastStatus = Get-HttpStatusFromError -ErrorRecord $_
            if (-not (Test-TransientHttpStatus -StatusCode $lastStatus)) {
                throw
            }
        }

        Start-Sleep -Seconds 5
    }

    if (-not $signUpCompleted) {
        throw "Sign-up did not recover from transient status $lastStatus within the timeout."
    }

    $token = Invoke-TransientHttpJson -Uri "$BaseUrl/api/auth-service/sign-in" -Method "POST" -Body @{
        email    = $email
        password = $password
    }
    $headers = @{ Authorization = "Bearer $($token.accessToken)" }
    $imageUrl = @($product.imageUrls)[0]
    $priceAtAdd = if ($null -ne $product.promotionalPrice) { $product.promotionalPrice } else { $product.price }

    Invoke-TransientHttpJson -Uri "$BaseUrl/api/cart-service/carts/me/items" -Method "POST" -Headers $headers -Body @{
        productId       = $product.id
        quantity        = 1
        priceAtAdd      = $priceAtAdd
        productSnapshot = @{
            name         = $product.name
            imageUrl     = $imageUrl
            categorySlug = $product.categorySlug
        }
    } | Out-Null

    $accepted = Invoke-TransientHttpJson -Uri "$BaseUrl/api/order-service/orders" -Method "POST" -Headers $headers -Body @{
        deliveryAddress = @{
            street         = "Stefan cel Mare 1"
            city           = "Chisinau"
            district       = "Chisinau"
            postalCode     = $null
            recipientName  = "Phase Seven"
            recipientPhone = "+37360000000"
        }
        payment         = @{
            method        = "CARD"
            transactionId = "phase7-$runId"
        }
        notes           = "Phase 7 Compose E2E validation"
    }

    if ($accepted.status -ne "ACCEPTED") {
        throw "Order creation did not return ACCEPTED."
    }

    $internalToken = Get-EnvOrDefault -Name "INTERNAL_SERVICE_TOKEN" -DefaultValue "__missing_internal_service_token__"
    if ($internalToken -eq "__missing_internal_service_token__" -or [string]::IsNullOrWhiteSpace($internalToken)) {
        throw "INTERNAL_SERVICE_TOKEN is required to validate internal notification diagnostics."
    }

    $notificationServicePort = Get-EnvOrDefault -Name "NOTIFICATION_SERVICE_PORT" -DefaultValue "8087"
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $overview = Invoke-HttpJson -Uri "http://127.0.0.1:$notificationServicePort/internal/notifications" -Headers @{ "X-Internal-Service-Token" = $internalToken }
        $match = @($overview.recentNotifications) | Where-Object { $_.orderId -eq $accepted.orderId } | Select-Object -First 1
        if ($null -ne $match) {
            return
        }

        $deadLetter = @($overview.deadLetterNotifications) | Where-Object { $_.orderId -eq $accepted.orderId } | Select-Object -First 1
        if ($null -ne $deadLetter) {
            throw "Order event reached the dead-letter diagnostic store for order $($accepted.orderId)."
        }

        Start-Sleep -Seconds 5
    }

    throw "Notification-service did not record order.created for order $($accepted.orderId) within the timeout."
}

Import-DotEnv -Path $script:EnvFilePath
$env:APP_DATA_SEED_ENABLED = "true"

$vitePort = Get-EnvOrDefault -Name "VITE_PORT" -DefaultValue "5173"
$baseUrl = "http://127.0.0.1:$vitePort"
$serviceRegistryPort = Get-EnvOrDefault -Name "SERVICE_REGISTRY_PORT" -DefaultValue "8761"
$apiGatewayPort = Get-EnvOrDefault -Name "API_GATEWAY_PORT" -DefaultValue "8080"
$containers = @(
    "modern-ecommerce-rabbitmq",
    "modern-ecommerce-service-registry",
    "modern-ecommerce-auth-service",
    "modern-ecommerce-user-service",
    "modern-ecommerce-category-service",
    "modern-ecommerce-product-service",
    "modern-ecommerce-cart-service",
    "modern-ecommerce-order-service",
    "modern-ecommerce-notification-service",
    "modern-ecommerce-api-gateway",
    "modern-ecommerce-web"
)

try {
    Invoke-Compose config --quiet
    Invoke-Compose up -d --build
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    foreach ($container in $containers) {
        Wait-ContainerHealthy -ContainerName $container -Deadline $deadline
    }

    Invoke-Compose ps
    Invoke-Compose stats --no-stream
    Invoke-Compose exec -T rabbitmq sh -c "rabbitmq-diagnostics -q ping"
    Invoke-Compose exec -T service-registry sh -c "wget -q --spider http://127.0.0.1:$serviceRegistryPort/"
    Assert-InternalReadiness -Service "auth-service" -Port (Get-EnvOrDefault -Name "AUTH_SERVICE_PORT" -DefaultValue "8081")
    Assert-InternalReadiness -Service "user-service" -Port (Get-EnvOrDefault -Name "USER_SERVICE_PORT" -DefaultValue "8082")
    Assert-InternalReadiness -Service "category-service" -Port (Get-EnvOrDefault -Name "CATEGORY_SERVICE_PORT" -DefaultValue "8083")
    Assert-InternalReadiness -Service "product-service" -Port (Get-EnvOrDefault -Name "PRODUCT_SERVICE_PORT" -DefaultValue "8084")
    Assert-InternalReadiness -Service "cart-service" -Port (Get-EnvOrDefault -Name "CART_SERVICE_PORT" -DefaultValue "8085")
    Assert-InternalReadiness -Service "order-service" -Port (Get-EnvOrDefault -Name "ORDER_SERVICE_PORT" -DefaultValue "8086")
    Assert-InternalReadiness -Service "notification-service" -Port (Get-EnvOrDefault -Name "NOTIFICATION_SERVICE_PORT" -DefaultValue "8087")
    $gatewayHealth = Invoke-ComposeOutput exec -T api-gateway sh -c "wget -qO- http://127.0.0.1:$apiGatewayPort/actuator/health"
    if ($gatewayHealth -notmatch '"status":"UP"') {
        throw "api-gateway health response did not report UP. Response: $gatewayHealth"
    }
    Invoke-Compose exec -T web sh -c "wget -q --spider http://127.0.0.1:$vitePort/"

    Assert-HttpStatus -Uri "$baseUrl/" -ExpectedStatus 200
    Assert-HttpStatus -Uri "$baseUrl/home" -ExpectedStatus 200
    Assert-HttpStatus -Uri "$baseUrl/api/product-service/products" -ExpectedStatus 200
    Assert-HttpStatus -Uri "$baseUrl/api/category-service/categories" -ExpectedStatus 200
    Assert-HttpStatus -Uri "$baseUrl/api/notification-service/internal/notifications" -ExpectedStatus 404
    Assert-HttpStatus -Uri "$baseUrl/api/product-service/internal/products/example" -ExpectedStatus 404
    Assert-HttpStatus -Uri "$baseUrl/api/user-service/users/internal/by-auth/example" -ExpectedStatus 404
    Assert-RabbitMqTopology

    if (-not $SkipOrderFlow) {
        Invoke-OrderCreatedFlow -BaseUrl $baseUrl
    }

    Invoke-Compose logs --tail 100
}
finally {
    if ($KeepStack) {
        Write-Host "Keeping Compose stack running because -KeepStack was specified."
    }
    else {
        Invoke-Compose stop
        Invoke-Compose down -v
    }
}
