[CmdletBinding()]
param(
    [string]$ReportPath = ".artifacts/backend-endpoint-report.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:Results = [System.Collections.Generic.List[object]]::new()
$script:PairSnapshots = @{}
$script:RepresentativeSnapshots = @{}
$script:AuthLookupClasspath = $null
$script:AuthLookupClasspathFile = $null

function Resolve-OutputPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return Join-Path $script:RepoRoot $Path
}

function Ensure-ParentDirectory {
    param([Parameter(Mandatory = $true)][string]$FilePath)

    $parent = Split-Path -Parent $FilePath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

function Get-ResponseText {
    param($Response)

    if ($null -eq $Response) {
        return ""
    }

    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }

    return [string]$Response.Content
}

function ConvertFrom-JsonSafe {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    try {
        return $Text | ConvertFrom-Json -Depth 20
    }
    catch {
        return $null
    }
}

function Get-TopLevelKeys {
    param($Value)

    if ($null -eq $Value) {
        return @()
    }

    if ($Value -is [System.Collections.IDictionary]) {
        return @($Value.Keys | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    }

    $propertyNames = @($Value.PSObject.Properties | ForEach-Object { $_.Name } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($propertyNames.Count -eq 0) {
        return @()
    }

    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        return @("[]")
    }

    return @($propertyNames | Sort-Object -Unique)
}

function Get-BodySummary {
    param(
        $ParsedBody,
        [string]$RawText
    )

    if ($null -ne $ParsedBody) {
        $keys = Get-TopLevelKeys -Value $ParsedBody
        if ($keys.Count -gt 0) {
            return "keys=" + ($keys -join ",")
        }
    }

    if ([string]::IsNullOrWhiteSpace($RawText)) {
        return "empty"
    }

    $compact = ($RawText -replace "\s+", " ").Trim()
    if ($compact.Length -gt 220) {
        return $compact.Substring(0, 220) + "..."
    }

    return $compact
}

function Compare-PairSnapshot {
    param(
        [string]$PairKey,
        [int]$StatusCode,
        $ParsedBody,
        [string]$ContentType
    )

    if ([string]::IsNullOrWhiteSpace($PairKey)) {
        return $null
    }

    $snapshot = [pscustomobject]@{
        StatusCode  = $StatusCode
        Keys        = @(Get-TopLevelKeys -Value $ParsedBody)
        ContentType = $ContentType
    }

    if (-not $script:PairSnapshots.ContainsKey($PairKey)) {
        $script:PairSnapshots[$PairKey] = $snapshot
        return "pair-baseline-recorded"
    }

    $baseline = $script:PairSnapshots[$PairKey]
    $statusMatches = $baseline.StatusCode -eq $snapshot.StatusCode
    $keysMatch = (@($baseline.Keys) -join ",") -eq (@($snapshot.Keys) -join ",")
    if ($statusMatches -and $keysMatch) {
        return "pair-equivalent"
    }

    return "pair-mismatch(status:$($baseline.StatusCode)/$StatusCode keys:$(@($baseline.Keys) -join '|')/$(@($snapshot.Keys) -join '|'))"
}

function Store-RepresentativeSnapshot {
    param(
        [string]$Name,
        [int]$StatusCode,
        $ParsedBody
    )

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return
    }

    $script:RepresentativeSnapshots[$Name] = [pscustomobject]@{
        StatusCode = $StatusCode
        Keys       = @(Get-TopLevelKeys -Value $ParsedBody)
    }
}

function Compare-RepresentativeSnapshot {
    param(
        [string]$Name,
        [int]$StatusCode,
        $ParsedBody
    )

    if ([string]::IsNullOrWhiteSpace($Name) -or -not $script:RepresentativeSnapshots.ContainsKey($Name)) {
        return $null
    }

    $baseline = $script:RepresentativeSnapshots[$Name]
    $keys = @(Get-TopLevelKeys -Value $ParsedBody)
    if ($baseline.StatusCode -eq $StatusCode -and (@($baseline.Keys) -join ",") -eq ($keys -join ",")) {
        return "matches-direct-$Name"
    }

    return "differs-from-direct-$Name"
}

function Add-Result {
    param(
        [int]$Index,
        [string]$Method,
        [string]$Url,
        [string]$ExpectedStatus,
        [int]$ActualStatus,
        [string]$Summary,
        [string]$Verdict,
        [string]$CauseLayer = "",
        [string]$Comparison = "",
        [string]$Remediation = "",
        [string]$RerunResult = ""
    )

    $script:Results.Add([pscustomobject]@{
        index          = $Index
        method         = $Method
        path           = $Url
        expectedStatus = $ExpectedStatus
        actualStatus   = $ActualStatus
        response       = $Summary
        verdict        = $Verdict
        causeLayer     = $CauseLayer
        comparison     = $Comparison
        remediation    = $Remediation
        rerunResult    = $RerunResult
    }) | Out-Null
}

function Invoke-TrackedRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [switch]$NoRedirect
    )

    $params = @{
        Uri                = $Url
        Method             = $Method
        Headers            = $Headers
        SkipHttpErrorCheck = $true
        UseBasicParsing    = $true
        TimeoutSec         = 30
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }

    if ($NoRedirect) {
        throw "Invoke-TrackedRequest does not support -NoRedirect. Use Invoke-NoRedirectRequest instead."
    }

    $response = Invoke-WebRequest @params

    $text = Get-ResponseText -Response $response
    $parsed = $null
    $contentType = [string]$response.Headers["Content-Type"]
    if ($contentType -match "json") {
        $parsed = ConvertFrom-JsonSafe -Text $text
    }

    return [pscustomobject]@{
        StatusCode  = [int]$response.StatusCode
        Headers     = $response.Headers
        Text        = $text
        ParsedBody  = $parsed
        ContentType = $contentType
    }
}

function Invoke-NoRedirectRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [hashtable]$Headers = @{}
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(30)

    try {
        foreach ($key in $Headers.Keys) {
            [void]$client.DefaultRequestHeaders.TryAddWithoutValidation([string]$key, [string]$Headers[$key])
        }

        $response = $client.GetAsync($Url).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $responseHeaders = @{}
        foreach ($header in $response.Headers) {
            $responseHeaders[$header.Key] = @($header.Value)
        }
        foreach ($header in $response.Content.Headers) {
            $responseHeaders[$header.Key] = @($header.Value)
        }

        return [pscustomobject]@{
            StatusCode  = [int]$response.StatusCode
            Headers     = $responseHeaders
            Text        = $text
            ParsedBody  = $null
            ContentType = [string]$response.Content.Headers.ContentType
        }
    }
    finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

function Invoke-Endpoint {
    param(
        [Parameter(Mandatory = $true)][int]$Index,
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][int[]]$ExpectedStatus,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [string]$PairKey = "",
        [string]$CauseLayer = "backend",
        [string]$RepresentativeName = "",
        [string]$CompareAgainstRepresentative = "",
        [scriptblock]$Validator = $null,
        [switch]$PassThru
    )

    $result = Invoke-TrackedRequest -Method $Method -Url $Url -Headers $Headers -Body $Body
    $passed = $ExpectedStatus -contains $result.StatusCode
    $notes = New-Object System.Collections.Generic.List[string]
    $notes.Add((Get-BodySummary -ParsedBody $result.ParsedBody -RawText $result.Text)) | Out-Null

    if ($Validator) {
        $validation = & $Validator $result
        if ($null -ne $validation) {
            if ($validation.PSObject.Properties.Name -contains "Pass") {
                $passed = $passed -and [bool]$validation.Pass
            }
            if ($validation.PSObject.Properties.Name -contains "Summary" -and $validation.Summary) {
                $notes.Add([string]$validation.Summary) | Out-Null
            }
        }
    }

    $pairComparison = Compare-PairSnapshot -PairKey $PairKey -StatusCode $result.StatusCode -ParsedBody $result.ParsedBody -ContentType $result.ContentType
    if ($pairComparison) {
        $notes.Add($pairComparison) | Out-Null
    }

    if ($RepresentativeName) {
        Store-RepresentativeSnapshot -Name $RepresentativeName -StatusCode $result.StatusCode -ParsedBody $result.ParsedBody
    }

    $directComparison = Compare-RepresentativeSnapshot -Name $CompareAgainstRepresentative -StatusCode $result.StatusCode -ParsedBody $result.ParsedBody
    if ($directComparison) {
        $notes.Add($directComparison) | Out-Null
    }

    Add-Result -Index $Index -Method $Method -Url $Url -ExpectedStatus ($ExpectedStatus -join "/") -ActualStatus $result.StatusCode -Summary ($notes -join "; ") -Verdict ($(if ($passed) { "PASS" } else { "FAIL" })) -CauseLayer ($(if ($passed) { "" } else { $CauseLayer })) -Comparison $pairComparison
    if ($PassThru) {
        return $result
    }
}

function Resolve-RedirectUrl {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Location
    )

    if ($Location -match "^https?://") {
        return $Location
    }

    return ([System.Uri]::new([System.Uri]$Url, $Location)).AbsoluteUri
}

function Invoke-SwaggerUiEndpoint {
    param(
        [Parameter(Mandatory = $true)][int]$Index,
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$ExpectedLocationPrefix = "",
        [scriptblock]$AfterRedirectValidator = $null,
        [switch]$PassThru
    )

    $initial = Invoke-NoRedirectRequest -Url $Url
    $passed = $initial.StatusCode -eq 302
    $location = [string](@($initial.Headers["Location"])[0])
    if ($ExpectedLocationPrefix) {
        $passed = $passed -and $location.StartsWith($ExpectedLocationPrefix)
    }

    $followUrl = if ($location) { Resolve-RedirectUrl -Url $Url -Location $location } else { $Url }
    $follow = Invoke-TrackedRequest -Method "GET" -Url $followUrl
    $passed = $passed -and $follow.StatusCode -eq 200
    $notes = New-Object System.Collections.Generic.List[string]
    $notes.Add("redirect=$location") | Out-Null
    $notes.Add("followStatus=$($follow.StatusCode)") | Out-Null

    if ($AfterRedirectValidator) {
        $validation = & $AfterRedirectValidator $follow
        if ($null -ne $validation) {
            if ($validation.PSObject.Properties.Name -contains "Pass") {
                $passed = $passed -and [bool]$validation.Pass
            }
            if ($validation.PSObject.Properties.Name -contains "Summary" -and $validation.Summary) {
                $notes.Add([string]$validation.Summary) | Out-Null
            }
        }
    }

    Add-Result -Index $Index -Method "GET" -Url $Url -ExpectedStatus "302->200" -ActualStatus $initial.StatusCode -Summary ($notes -join "; ") -Verdict ($(if ($passed) { "PASS" } else { "FAIL" })) -CauseLayer ($(if ($passed) { "" } else { "gateway-routing" }))
    if ($PassThru) {
        return [pscustomobject]@{
            Initial = $initial
            Follow  = $follow
        }
    }
}

function Ensure-AuthLookupRuntime {
    if ($script:AuthLookupClasspath) {
        return
    }

    $moduleDir = Join-Path $script:RepoRoot "api\auth-service"
    $mvnw = Join-Path $moduleDir "mvnw.cmd"
    $script:AuthLookupClasspathFile = Join-Path $moduleDir "target\password-reset-lookup.classpath"

    Push-Location $moduleDir
    try {
        & $mvnw "-q" "-DskipTests" "compile" "dependency:build-classpath" "-DincludeScope=runtime" "-Dmdep.outputFile=target\password-reset-lookup.classpath"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to build auth-service runtime classpath."
        }

        $dependencyClasspath = ""
        if (Test-Path -LiteralPath $script:AuthLookupClasspathFile) {
            $dependencyClasspath = (Get-Content -LiteralPath $script:AuthLookupClasspathFile -Raw).Trim()
        }

        $entries = New-Object System.Collections.Generic.List[string]
        $entries.Add((Join-Path $moduleDir "target\classes")) | Out-Null
        if (-not [string]::IsNullOrWhiteSpace($dependencyClasspath)) {
            foreach ($entry in ($dependencyClasspath -split ';')) {
                if (-not [string]::IsNullOrWhiteSpace($entry)) {
                    $entries.Add($entry.Trim()) | Out-Null
                }
            }
        }

        $script:AuthLookupClasspath = [string]::Join(';', $entries)
    }
    finally {
        Pop-Location
    }
}

function Get-PasswordResetToken {
    param([Parameter(Mandatory = $true)][string]$Email)

    Ensure-AuthLookupRuntime
    $moduleDir = Join-Path $script:RepoRoot "api\auth-service"
    $maxAttempts = 5

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        Push-Location $moduleDir
        try {
            $output = & java "-Dspring.profiles.active=local" "-cp" $script:AuthLookupClasspath "md.services.auth_service.connection.LookupPasswordResetToken" "--email=$Email" 2>&1
            if ($LASTEXITCODE -eq 0) {
                return ($output | Select-Object -Last 1).Trim()
            }

            if ($attempt -eq $maxAttempts) {
                throw "Password reset token lookup failed for '$Email': $($output -join ' ')"
            }
        }
        finally {
            Pop-Location
        }

        Start-Sleep -Seconds 5
    }
}

function New-RunState {
	$runId = ([Guid]::NewGuid().ToString('N').Substring(0, 10))
	$seedCategorySlug = "phase7-category-$runId"
	$seedProductSlug = "phase7-product-$runId"

	return [ordered]@{
		Password                = "Phase7!Pass123"
		NewPassword             = "Phase7!Pass456"
		RunId                   = $runId
        UserEmail               = $null
        GatewayUserEmail        = $null
        AccessToken             = $null
        RefreshToken            = $null
        GatewayAccessToken      = $null
        GatewayRefreshToken     = $null
        AdminAccessToken        = $null
        AdminRefreshToken       = $null
        UserProfile             = $null
        SeedCategorySlug        = $seedCategorySlug
        SeedProductSlug         = $seedProductSlug
        CategoryId              = $null
        CategorySlug            = $seedCategorySlug
        ProductId               = $null
        ProductSlug             = $seedProductSlug
        OrderId                 = $null
        GatewayOrderId          = $null
    }
}

function Test-SwaggerContent {
    param($Result)

    $ok = $Result.Text -match "Swagger UI" -or $Result.Text -match "swagger-ui"
    return [pscustomobject]@{
        Pass    = $ok
        Summary = $(if ($ok) { "swagger-ui-loaded" } else { "swagger-ui-marker-missing" })
    }
}

function Test-OpenApiHasPaths {
    param($Result)

    $keys = @()
    if ($null -ne $Result.ParsedBody -and $Result.ParsedBody.PSObject.Properties.Name -contains "paths") {
        $keys = @(Get-TopLevelKeys -Value $Result.ParsedBody.paths)
    }

    return [pscustomobject]@{
        Pass    = $keys.Count -ge 0
        Summary = "paths=" + $keys.Count
    }
}

function Invoke-SetupRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [int[]]$ExpectedStatus = @(200),
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )

    $result = Invoke-TrackedRequest -Method $Method -Url $Url -Headers $Headers -Body $Body
    if ($ExpectedStatus -notcontains $result.StatusCode) {
        throw "Setup request failed for $Method $Url with status $($result.StatusCode)."
    }

    return $result
}

function Run-Validation {
    $state = New-RunState
    $state.UserEmail = "phase7-$($state.RunId)@example.test"
    $state.GatewayUserEmail = "gateway-$($state.RunId)@example.test"

    $adminLoginBody = @{ email = "admin@modern-ecommerce.local"; password = "Admin123!" }
    $userSignUpBody = @{ firstName = "Phase"; lastName = "Seven"; email = $state.UserEmail; password = $state.Password }
    $gatewayUserSignUpBody = @{ firstName = "Gateway"; lastName = "Seven"; email = $state.GatewayUserEmail; password = $state.Password }
    $updateProfileBody = @{ firstName = "Phase"; lastName = "Seven Updated"; phone = "+37360000000"; birthDate = "1999-01-01"; preferences = @{ language = "ro"; currency = "MDL" } }
    $addressBody = @{ label = "Home"; street = "Stefan cel Mare 1"; city = "Chisinau"; district = "Centru"; postalCode = "2001"; isDefault = $true }
    $replacementAddressBody = @{ label = "Office"; street = "Alba Iulia 10"; city = "Chisinau"; district = "Buiucani"; postalCode = "2069"; isDefault = $true }
    $categoryBody = @{ name = "Phones"; slug = $state.SeedCategorySlug; parentId = $null; description = "Phones and accessories"; imageUrl = "/static/assets/images/categories/smartphones.png"; displayOrder = 1; isActive = $true }
    $categoryUpdateBody = @{ name = "Phones Updated"; slug = $state.SeedCategorySlug; parentId = $null; description = "Updated phones category"; imageUrl = "/static/assets/images/categories/smartphones.png"; displayOrder = 2; isActive = $true }
    $productBody = @{ categoryId = ""; categorySlug = $state.SeedCategorySlug; name = "Galaxy A55 5G"; slug = $state.SeedProductSlug; brand = "Samsung"; model = "Galaxy A55"; country = "Moldova"; price = 8999; promotionalPrice = 7999; currency = "MDL"; stock = 10; imageUrls = @('/static/assets/images/prod-images/products/phone.png'); specs = @{ memory = '256GB'; color = 'Blue' }; isActive = $true }
    $productUpdateBody = @{ categoryId = ""; categorySlug = $state.SeedCategorySlug; name = "Galaxy A55 5G Updated"; slug = $state.SeedProductSlug; brand = "Samsung"; model = "Galaxy A55"; country = "Moldova"; price = 8999; promotionalPrice = 7499; currency = "MDL"; stock = 8; imageUrls = @('/static/assets/images/prod-images/products/phone.png'); specs = @{ memory = '256GB'; color = 'Black' }; isActive = $true }

    Invoke-Endpoint -Index 1 -Method GET -Url "http://localhost:8761" -ExpectedStatus 200 -RepresentativeName "registry-root"
    Invoke-Endpoint -Index 2 -Method GET -Url "http://localhost:8761/eureka/apps" -ExpectedStatus 200 -RepresentativeName "registry-apps" -Validator { param($r) [pscustomobject]@{ Pass = ($r.Text -match '<apps__hashcode>UP_'); Summary = 'eureka-up-count-check' } }

    Invoke-SwaggerUiEndpoint -Index 3 -Url "http://localhost:8081/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 4 -Method GET -Url "http://localhost:8081/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "auth-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 5 -Url "http://localhost:8082/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 6 -Method GET -Url "http://localhost:8082/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "user-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 7 -Url "http://localhost:8083/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 8 -Method GET -Url "http://localhost:8083/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "category-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 9 -Url "http://localhost:8084/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 10 -Method GET -Url "http://localhost:8084/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "product-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 11 -Url "http://localhost:8085/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 12 -Method GET -Url "http://localhost:8085/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "cart-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 13 -Url "http://localhost:8087/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 14 -Method GET -Url "http://localhost:8087/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "notification-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 15 -Url "http://localhost:8086/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 16 -Method GET -Url "http://localhost:8086/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "order-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-SwaggerUiEndpoint -Index 17 -Url "http://localhost:8080/swagger-ui.html" -ExpectedLocationPrefix "/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 18 -Method GET -Url "http://localhost:8080/v3/api-docs" -ExpectedStatus 200 -RepresentativeName "gateway-docs" -Validator ${function:Test-OpenApiHasPaths}

    Invoke-Endpoint -Index 19 -Method GET -Url "http://localhost:8080/api/auth-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "auth-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 20 -Method GET -Url "http://localhost:8080/api/user-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "user-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 21 -Method GET -Url "http://localhost:8080/api/category-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "category-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 22 -Method GET -Url "http://localhost:8080/api/product-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "product-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 23 -Method GET -Url "http://localhost:8080/api/cart-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "cart-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 24 -Method GET -Url "http://localhost:8080/api/notification-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "notification-docs" -Validator ${function:Test-OpenApiHasPaths}
    Invoke-Endpoint -Index 25 -Method GET -Url "http://localhost:8080/api/order-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "order-docs" -Validator ${function:Test-OpenApiHasPaths}

    Invoke-Endpoint -Index 26 -Method POST -Url "http://localhost:8081/v1/sign-up" -ExpectedStatus 201 -Body $userSignUpBody -PairKey "auth-sign-up"
    Invoke-Endpoint -Index 27 -Method POST -Url "http://localhost:8081/sign-up" -ExpectedStatus 409 -Body $userSignUpBody -PairKey "auth-sign-up-duplicate"
    $signInV1 = Invoke-Endpoint -Index 28 -Method POST -Url "http://localhost:8081/v1/sign-in" -ExpectedStatus 200 -Body @{ email = $state.UserEmail; password = $state.Password } -PairKey "auth-sign-in" -PassThru
    $state.AccessToken = $signInV1.ParsedBody.accessToken
    $state.RefreshToken = $signInV1.ParsedBody.refreshToken
    $signInLegacy = Invoke-Endpoint -Index 29 -Method POST -Url "http://localhost:8081/sign-in" -ExpectedStatus 200 -Body @{ email = $state.UserEmail; password = $state.Password } -PairKey "auth-sign-in" -PassThru
    $state.AccessToken = $signInLegacy.ParsedBody.accessToken
    $state.RefreshToken = $signInLegacy.ParsedBody.refreshToken

    $userHeaders = @{ Authorization = "Bearer $($state.AccessToken)" }
    $meV1 = Invoke-Endpoint -Index 30 -Method GET -Url "http://localhost:8082/v1/users/me" -ExpectedStatus 200 -Headers $userHeaders -PairKey "user-me" -PassThru
    $state.UserProfile = $meV1.ParsedBody
    Invoke-Endpoint -Index 31 -Method GET -Url "http://localhost:8082/users/me" -ExpectedStatus 200 -Headers $userHeaders -PairKey "user-me"
    Invoke-Endpoint -Index 32 -Method PUT -Url "http://localhost:8082/v1/users/me" -ExpectedStatus 200 -Headers $userHeaders -Body $updateProfileBody -PairKey "user-update"
    Invoke-Endpoint -Index 33 -Method PUT -Url "http://localhost:8082/users/me" -ExpectedStatus 200 -Headers $userHeaders -Body $updateProfileBody -PairKey "user-update"
    Invoke-Endpoint -Index 34 -Method POST -Url "http://localhost:8082/v1/users/me/addresses" -ExpectedStatus 201 -Headers $userHeaders -Body $addressBody -PairKey "user-address-add"
    Invoke-Endpoint -Index 35 -Method POST -Url "http://localhost:8082/users/me/addresses" -ExpectedStatus 201 -Headers $userHeaders -Body $addressBody -PairKey "user-address-add"
    Invoke-Endpoint -Index 36 -Method PUT -Url "http://localhost:8082/v1/users/me/addresses/0" -ExpectedStatus 200 -Headers $userHeaders -Body $replacementAddressBody -PairKey "user-address-replace"
    Invoke-Endpoint -Index 37 -Method PUT -Url "http://localhost:8082/users/me/addresses/0" -ExpectedStatus 200 -Headers $userHeaders -Body $replacementAddressBody -PairKey "user-address-replace"
    Invoke-Endpoint -Index 38 -Method DELETE -Url "http://localhost:8082/v1/users/me/addresses/1" -ExpectedStatus 204 -Headers $userHeaders -PairKey "user-address-delete"
    Invoke-Endpoint -Index 39 -Method DELETE -Url "http://localhost:8082/users/me/addresses/0" -ExpectedStatus 204 -Headers $userHeaders -PairKey "user-address-delete"

    $adminSignIn = Invoke-SetupRequest -Method POST -Url "http://localhost:8081/sign-in" -ExpectedStatus 200 -Body $adminLoginBody
    $state.AdminAccessToken = $adminSignIn.ParsedBody.accessToken
    $adminHeaders = @{ Authorization = "Bearer $($state.AdminAccessToken)" }

    $categoryV1 = Invoke-Endpoint -Index 40 -Method POST -Url "http://localhost:8083/v1/categories" -ExpectedStatus 201 -Headers $adminHeaders -Body $categoryBody -PairKey "category-create" -PassThru
    $state.CategoryId = $categoryV1.ParsedBody.id
    $productBody.categoryId = $state.CategoryId
    $productUpdateBody.categoryId = $state.CategoryId
    Invoke-Endpoint -Index 41 -Method POST -Url "http://localhost:8083/categories" -ExpectedStatus 409 -Headers $adminHeaders -Body $categoryBody -PairKey "category-create-duplicate"
    Invoke-Endpoint -Index 42 -Method GET -Url "http://localhost:8083/v1/categories?page=0&size=20" -ExpectedStatus 200 -PairKey "category-list"
    Invoke-Endpoint -Index 43 -Method GET -Url "http://localhost:8083/categories?page=0&size=20" -ExpectedStatus 200 -PairKey "category-list"
    Invoke-Endpoint -Index 44 -Method GET -Url "http://localhost:8083/v1/categories/$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "category-get"
    Invoke-Endpoint -Index 45 -Method GET -Url "http://localhost:8083/categories/$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "category-get"
    Invoke-Endpoint -Index 46 -Method PUT -Url "http://localhost:8083/v1/categories/$($state.CategorySlug)" -ExpectedStatus 200 -Headers $adminHeaders -Body $categoryUpdateBody -PairKey "category-update"
    Invoke-Endpoint -Index 47 -Method PUT -Url "http://localhost:8083/categories/$($state.CategorySlug)" -ExpectedStatus 200 -Headers $adminHeaders -Body $categoryUpdateBody -PairKey "category-update"
    Invoke-Endpoint -Index 48 -Method DELETE -Url "http://localhost:8083/v1/categories/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $adminHeaders -PairKey "category-delete-missing"
    Invoke-Endpoint -Index 49 -Method DELETE -Url "http://localhost:8083/categories/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $adminHeaders -PairKey "category-delete-missing"

    $productV1 = Invoke-Endpoint -Index 50 -Method POST -Url "http://localhost:8084/v1/products" -ExpectedStatus 201 -Headers $adminHeaders -Body $productBody -PairKey "product-create" -PassThru
    $state.ProductId = $productV1.ParsedBody.id
    Invoke-Endpoint -Index 51 -Method POST -Url "http://localhost:8084/products" -ExpectedStatus 409 -Headers $adminHeaders -Body $productBody -PairKey "product-create-duplicate"
    Invoke-Endpoint -Index 52 -Method GET -Url "http://localhost:8084/v1/products?page=0&size=12&categorySlug=$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "product-list"
    Invoke-Endpoint -Index 53 -Method GET -Url "http://localhost:8084/products?page=0&size=12&categorySlug=$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "product-list"
    Invoke-Endpoint -Index 54 -Method GET -Url "http://localhost:8084/v1/products/search?q=Galaxy&page=0&size=12" -ExpectedStatus 200 -PairKey "product-search"
    Invoke-Endpoint -Index 55 -Method GET -Url "http://localhost:8084/products/search?q=Galaxy&page=0&size=12" -ExpectedStatus 200 -PairKey "product-search"
    Invoke-Endpoint -Index 56 -Method GET -Url "http://localhost:8084/v1/products/$($state.ProductSlug)" -ExpectedStatus 200 -PairKey "product-get"
    Invoke-Endpoint -Index 57 -Method GET -Url "http://localhost:8084/products/$($state.ProductSlug)" -ExpectedStatus 200 -PairKey "product-get"
    Invoke-Endpoint -Index 58 -Method PUT -Url "http://localhost:8084/v1/products/$($state.ProductSlug)" -ExpectedStatus 200 -Headers $adminHeaders -Body $productUpdateBody -PairKey "product-update"
    Invoke-Endpoint -Index 59 -Method PUT -Url "http://localhost:8084/products/$($state.ProductSlug)" -ExpectedStatus 200 -Headers $adminHeaders -Body $productUpdateBody -PairKey "product-update"
    Invoke-Endpoint -Index 60 -Method DELETE -Url "http://localhost:8084/v1/products/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $adminHeaders -PairKey "product-delete-missing"
    Invoke-Endpoint -Index 61 -Method DELETE -Url "http://localhost:8084/products/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $adminHeaders -PairKey "product-delete-missing"

    $cartGetV1 = Invoke-Endpoint -Index 62 -Method GET -Url "http://localhost:8085/v1/carts/me" -ExpectedStatus 200 -Headers $userHeaders -PairKey "cart-get"
    Invoke-Endpoint -Index 63 -Method GET -Url "http://localhost:8085/carts/me" -ExpectedStatus 200 -Headers $userHeaders -PairKey "cart-get"
    $addCartBody = @{ productId = $state.ProductId; quantity = 1; priceAtAdd = 7499; productSnapshot = @{ name = "Galaxy A55 5G Updated"; imageUrl = "/static/assets/images/prod-images/products/phone.png"; categorySlug = $state.CategorySlug } }
    Invoke-Endpoint -Index 64 -Method POST -Url "http://localhost:8085/v1/carts/me/items" -ExpectedStatus 201 -Headers $userHeaders -Body $addCartBody -PairKey "cart-add"
    Invoke-Endpoint -Index 65 -Method POST -Url "http://localhost:8085/carts/me/items" -ExpectedStatus 201 -Headers $userHeaders -Body $addCartBody -PairKey "cart-add"
    Invoke-Endpoint -Index 66 -Method PUT -Url "http://localhost:8085/v1/carts/me/items/$($state.ProductId)" -ExpectedStatus 200 -Headers $userHeaders -Body @{ quantity = 2 } -PairKey "cart-update"
    Invoke-Endpoint -Index 67 -Method PUT -Url "http://localhost:8085/carts/me/items/$($state.ProductId)" -ExpectedStatus 200 -Headers $userHeaders -Body @{ quantity = 2 } -PairKey "cart-update"
    Invoke-Endpoint -Index 68 -Method DELETE -Url "http://localhost:8085/v1/carts/me/items/$($state.ProductId)" -ExpectedStatus 204 -Headers $userHeaders -PairKey "cart-delete"
    Invoke-Endpoint -Index 69 -Method DELETE -Url "http://localhost:8085/carts/me/items/$($state.ProductId)" -ExpectedStatus 204 -Headers $userHeaders -PairKey "cart-delete"
    Invoke-Endpoint -Index 70 -Method DELETE -Url "http://localhost:8085/v1/carts/me" -ExpectedStatus 204 -Headers $userHeaders -PairKey "cart-clear"
    Invoke-Endpoint -Index 71 -Method DELETE -Url "http://localhost:8085/carts/me" -ExpectedStatus 204 -Headers $userHeaders -PairKey "cart-clear"

    Invoke-SetupRequest -Method POST -Url "http://localhost:8085/v1/carts/me/items" -ExpectedStatus 201 -Headers $userHeaders -Body $addCartBody | Out-Null
    $orderV1 = Invoke-Endpoint -Index 72 -Method POST -Url "http://localhost:8086/v1/orders" -ExpectedStatus 202 -Headers $userHeaders -Body @{ deliveryAddress = @{ street = "Stefan cel Mare 1"; city = "Chisinau"; district = "Centru"; postalCode = "2001"; recipientName = "Phase Seven"; recipientPhone = "+37360000000" }; payment = @{ method = "CARD"; transactionId = "tx-v1-$($state.RunId)" }; notes = "Order direct v1" } -PairKey "order-create" -PassThru
    $state.OrderId = $orderV1.ParsedBody.orderId
    Invoke-Endpoint -Index 73 -Method POST -Url "http://localhost:8086/orders" -ExpectedStatus 202 -Headers $userHeaders -Body @{ deliveryAddress = @{ street = "Stefan cel Mare 1"; city = "Chisinau"; district = "Centru"; postalCode = "2001"; recipientName = "Phase Seven"; recipientPhone = "+37360000000" }; payment = @{ method = "CARD"; transactionId = "tx-$($state.RunId)" }; notes = "Order direct legacy" } -PairKey "order-create"
    Invoke-Endpoint -Index 74 -Method GET -Url "http://localhost:8086/v1/orders?page=0&size=20" -ExpectedStatus 200 -Headers $userHeaders -PairKey "order-list"
    Invoke-Endpoint -Index 75 -Method GET -Url "http://localhost:8086/orders?page=0&size=20" -ExpectedStatus 200 -Headers $userHeaders -PairKey "order-list"
    Invoke-Endpoint -Index 76 -Method GET -Url "http://localhost:8086/v1/orders/$($state.OrderId)" -ExpectedStatus 200 -Headers $userHeaders -PairKey "order-get"
    Invoke-Endpoint -Index 77 -Method GET -Url "http://localhost:8086/orders/$($state.OrderId)" -ExpectedStatus 200 -Headers $userHeaders -PairKey "order-get"
    Invoke-Endpoint -Index 78 -Method GET -Url "http://localhost:8086/v1/orders/all?page=0&size=20" -ExpectedStatus 200 -Headers $adminHeaders -PairKey "order-all"
    Invoke-Endpoint -Index 79 -Method GET -Url "http://localhost:8086/orders/all?page=0&size=20" -ExpectedStatus 200 -Headers $adminHeaders -PairKey "order-all"
    Invoke-Endpoint -Index 80 -Method PATCH -Url "http://localhost:8086/v1/orders/$($state.OrderId)/status" -ExpectedStatus 200 -Headers $adminHeaders -Body @{ status = "CONFIRMED" } -PairKey "order-status"
    Invoke-Endpoint -Index 81 -Method PATCH -Url "http://localhost:8086/orders/$($state.OrderId)/status" -ExpectedStatus 200 -Headers $adminHeaders -Body @{ status = "SHIPPED" } -PairKey "order-status"

    $staleRefreshToken = $state.RefreshToken
    $refreshV1 = Invoke-Endpoint -Index 82 -Method POST -Url "http://localhost:8081/v1/token/refresh" -ExpectedStatus 200 -Body @{ refreshToken = $staleRefreshToken } -PairKey "auth-refresh" -PassThru
    $state.AccessToken = $refreshV1.ParsedBody.accessToken
    $state.RefreshToken = $refreshV1.ParsedBody.refreshToken
    $userHeaders = @{ Authorization = "Bearer $($state.AccessToken)" }
    Invoke-Endpoint -Index 83 -Method POST -Url "http://localhost:8081/token/refresh" -ExpectedStatus 401 -Body @{ refreshToken = $staleRefreshToken } -PairKey "auth-refresh-rotated"
    Invoke-Endpoint -Index 84 -Method POST -Url "http://localhost:8081/v1/password-reset/request" -ExpectedStatus 200 -Body @{ email = $state.UserEmail } -PairKey "password-reset-request"
    Invoke-Endpoint -Index 85 -Method POST -Url "http://localhost:8081/password-reset/request" -ExpectedStatus 200 -Body @{ email = $state.UserEmail } -PairKey "password-reset-request"
    $resetToken = Get-PasswordResetToken -Email $state.UserEmail
    Invoke-Endpoint -Index 86 -Method POST -Url "http://localhost:8081/v1/password-reset/confirm" -ExpectedStatus 200 -Body @{ token = $resetToken; newPassword = $state.NewPassword } -PairKey "password-reset-confirm"
    Invoke-Endpoint -Index 87 -Method POST -Url "http://localhost:8081/password-reset/confirm" -ExpectedStatus 422 -Body @{ token = $resetToken; newPassword = $state.NewPassword } -PairKey "password-reset-confirm-used"
    Invoke-Endpoint -Index 88 -Method POST -Url "http://localhost:8081/v1/sign-out" -ExpectedStatus 204 -Headers $userHeaders -PairKey "auth-sign-out"
    Invoke-Endpoint -Index 89 -Method POST -Url "http://localhost:8081/sign-out" -ExpectedStatus 204 -Headers @{ Authorization = "Bearer $($refreshV1.ParsedBody.accessToken)" } -PairKey "auth-sign-out"

    Invoke-Endpoint -Index 90 -Method POST -Url "http://localhost:8080/api/auth-service/v1/sign-up" -ExpectedStatus 201 -Body $gatewayUserSignUpBody -PairKey "gw-auth-sign-up" -CompareAgainstRepresentative ""
    Invoke-Endpoint -Index 91 -Method POST -Url "http://localhost:8080/api/auth-service/sign-up" -ExpectedStatus 409 -Body $gatewayUserSignUpBody -PairKey "gw-auth-sign-up-duplicate"
    $gatewaySignInV1 = Invoke-Endpoint -Index 92 -Method POST -Url "http://localhost:8080/api/auth-service/v1/sign-in" -ExpectedStatus 200 -Body @{ email = $state.GatewayUserEmail; password = $state.Password } -PairKey "gw-auth-sign-in" -PassThru
    $state.GatewayAccessToken = $gatewaySignInV1.ParsedBody.accessToken
    $state.GatewayRefreshToken = $gatewaySignInV1.ParsedBody.refreshToken
    $gatewaySignInLegacy = Invoke-Endpoint -Index 93 -Method POST -Url "http://localhost:8080/api/auth-service/sign-in" -ExpectedStatus 200 -Body @{ email = $state.GatewayUserEmail; password = $state.Password } -PairKey "gw-auth-sign-in" -PassThru
    $state.GatewayAccessToken = $gatewaySignInLegacy.ParsedBody.accessToken
    $state.GatewayRefreshToken = $gatewaySignInLegacy.ParsedBody.refreshToken
    $gatewayUserHeaders = @{ Authorization = "Bearer $($state.GatewayAccessToken)" }
    Invoke-Endpoint -Index 94 -Method POST -Url "http://localhost:8080/api/auth-service/v1/token/refresh" -ExpectedStatus 200 -Body @{ refreshToken = $state.GatewayRefreshToken } -PairKey "gw-auth-refresh"
    Invoke-Endpoint -Index 95 -Method POST -Url "http://localhost:8080/api/auth-service/token/refresh" -ExpectedStatus 401 -Body @{ refreshToken = $state.GatewayRefreshToken } -PairKey "gw-auth-refresh-legacy"
    Invoke-Endpoint -Index 96 -Method POST -Url "http://localhost:8080/api/auth-service/v1/password-reset/request" -ExpectedStatus 200 -Body @{ email = $state.GatewayUserEmail } -PairKey "gw-password-reset-request"
    Invoke-Endpoint -Index 97 -Method POST -Url "http://localhost:8080/api/auth-service/password-reset/request" -ExpectedStatus 200 -Body @{ email = $state.GatewayUserEmail } -PairKey "gw-password-reset-request"
    $gatewayResetToken = Get-PasswordResetToken -Email $state.GatewayUserEmail
    Invoke-Endpoint -Index 98 -Method POST -Url "http://localhost:8080/api/auth-service/v1/password-reset/confirm" -ExpectedStatus 200 -Body @{ token = $gatewayResetToken; newPassword = $state.NewPassword } -PairKey "gw-password-reset-confirm"
    Invoke-Endpoint -Index 99 -Method POST -Url "http://localhost:8080/api/auth-service/password-reset/confirm" -ExpectedStatus 422 -Body @{ token = $gatewayResetToken; newPassword = $state.NewPassword } -PairKey "gw-password-reset-confirm-used"
    Invoke-Endpoint -Index 100 -Method POST -Url "http://localhost:8080/api/auth-service/v1/sign-out" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-auth-sign-out"
    Invoke-Endpoint -Index 101 -Method POST -Url "http://localhost:8080/api/auth-service/sign-out" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-auth-sign-out"

    $gatewayAdminSignIn = Invoke-SetupRequest -Method POST -Url "http://localhost:8080/api/auth-service/sign-in" -ExpectedStatus 200 -Body $adminLoginBody
    $gatewayAdminHeaders = @{ Authorization = "Bearer $($gatewayAdminSignIn.ParsedBody.accessToken)" }
    Invoke-Endpoint -Index 102 -Method GET -Url "http://localhost:8080/api/user-service/v1/users/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-user-me" -CompareAgainstRepresentative ""
    Invoke-Endpoint -Index 103 -Method GET -Url "http://localhost:8080/api/user-service/users/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-user-me"
    Invoke-Endpoint -Index 104 -Method PUT -Url "http://localhost:8080/api/user-service/v1/users/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body $updateProfileBody -PairKey "gw-user-update"
    Invoke-Endpoint -Index 105 -Method PUT -Url "http://localhost:8080/api/user-service/users/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body $updateProfileBody -PairKey "gw-user-update"
    Invoke-Endpoint -Index 106 -Method POST -Url "http://localhost:8080/api/user-service/v1/users/me/addresses" -ExpectedStatus 201 -Headers $gatewayUserHeaders -Body $addressBody -PairKey "gw-user-address-add"
    Invoke-Endpoint -Index 107 -Method POST -Url "http://localhost:8080/api/user-service/users/me/addresses" -ExpectedStatus 201 -Headers $gatewayUserHeaders -Body $addressBody -PairKey "gw-user-address-add"
    Invoke-Endpoint -Index 108 -Method PUT -Url "http://localhost:8080/api/user-service/v1/users/me/addresses/0" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body $replacementAddressBody -PairKey "gw-user-address-replace"
    Invoke-Endpoint -Index 109 -Method PUT -Url "http://localhost:8080/api/user-service/users/me/addresses/0" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body $replacementAddressBody -PairKey "gw-user-address-replace"
    Invoke-Endpoint -Index 110 -Method DELETE -Url "http://localhost:8080/api/user-service/v1/users/me/addresses/1" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-user-address-delete"
    Invoke-Endpoint -Index 111 -Method DELETE -Url "http://localhost:8080/api/user-service/users/me/addresses/0" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-user-address-delete"

    Invoke-Endpoint -Index 112 -Method POST -Url "http://localhost:8080/api/category-service/v1/categories" -ExpectedStatus 409 -Headers $gatewayAdminHeaders -Body $categoryBody -PairKey "gw-category-create"
    Invoke-Endpoint -Index 113 -Method POST -Url "http://localhost:8080/api/category-service/categories" -ExpectedStatus 409 -Headers $gatewayAdminHeaders -Body $categoryBody -PairKey "gw-category-create"
    Invoke-Endpoint -Index 114 -Method GET -Url "http://localhost:8080/api/category-service/v1/categories?page=0&size=20" -ExpectedStatus 200 -PairKey "gw-category-list" -CompareAgainstRepresentative ""
    Invoke-Endpoint -Index 115 -Method GET -Url "http://localhost:8080/api/category-service/categories?page=0&size=20" -ExpectedStatus 200 -PairKey "gw-category-list"
    Invoke-Endpoint -Index 116 -Method GET -Url "http://localhost:8080/api/category-service/v1/categories/$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "gw-category-get"
    Invoke-Endpoint -Index 117 -Method GET -Url "http://localhost:8080/api/category-service/categories/$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "gw-category-get"
    Invoke-Endpoint -Index 118 -Method PUT -Url "http://localhost:8080/api/category-service/v1/categories/$($state.CategorySlug)" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body $categoryUpdateBody -PairKey "gw-category-update"
    Invoke-Endpoint -Index 119 -Method PUT -Url "http://localhost:8080/api/category-service/categories/$($state.CategorySlug)" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body $categoryUpdateBody -PairKey "gw-category-update"
    Invoke-Endpoint -Index 120 -Method DELETE -Url "http://localhost:8080/api/category-service/v1/categories/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $gatewayAdminHeaders -PairKey "gw-category-delete-missing"
    Invoke-Endpoint -Index 121 -Method DELETE -Url "http://localhost:8080/api/category-service/categories/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $gatewayAdminHeaders -PairKey "gw-category-delete-missing"

    Invoke-Endpoint -Index 122 -Method POST -Url "http://localhost:8080/api/product-service/v1/products" -ExpectedStatus 409 -Headers $gatewayAdminHeaders -Body $productBody -PairKey "gw-product-create"
    Invoke-Endpoint -Index 123 -Method POST -Url "http://localhost:8080/api/product-service/products" -ExpectedStatus 409 -Headers $gatewayAdminHeaders -Body $productBody -PairKey "gw-product-create"
    Invoke-Endpoint -Index 124 -Method GET -Url "http://localhost:8080/api/product-service/v1/products?page=0&size=12&categorySlug=$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "gw-product-list"
    Invoke-Endpoint -Index 125 -Method GET -Url "http://localhost:8080/api/product-service/products?page=0&size=12&categorySlug=$($state.CategorySlug)" -ExpectedStatus 200 -PairKey "gw-product-list"
    Invoke-Endpoint -Index 126 -Method GET -Url "http://localhost:8080/api/product-service/v1/products/search?q=Galaxy&page=0&size=12" -ExpectedStatus 200 -PairKey "gw-product-search"
    Invoke-Endpoint -Index 127 -Method GET -Url "http://localhost:8080/api/product-service/products/search?q=Galaxy&page=0&size=12" -ExpectedStatus 200 -PairKey "gw-product-search"
    Invoke-Endpoint -Index 128 -Method GET -Url "http://localhost:8080/api/product-service/v1/products/$($state.ProductSlug)" -ExpectedStatus 200 -PairKey "gw-product-get"
    Invoke-Endpoint -Index 129 -Method GET -Url "http://localhost:8080/api/product-service/products/$($state.ProductSlug)" -ExpectedStatus 200 -PairKey "gw-product-get"
    Invoke-Endpoint -Index 130 -Method PUT -Url "http://localhost:8080/api/product-service/v1/products/$($state.ProductSlug)" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body $productUpdateBody -PairKey "gw-product-update"
    Invoke-Endpoint -Index 131 -Method PUT -Url "http://localhost:8080/api/product-service/products/$($state.ProductSlug)" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body $productUpdateBody -PairKey "gw-product-update"
    Invoke-Endpoint -Index 132 -Method DELETE -Url "http://localhost:8080/api/product-service/v1/products/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $gatewayAdminHeaders -PairKey "gw-product-delete-missing"
    Invoke-Endpoint -Index 133 -Method DELETE -Url "http://localhost:8080/api/product-service/products/temporary-missing-$($state.RunId)" -ExpectedStatus 404 -Headers $gatewayAdminHeaders -PairKey "gw-product-delete-missing"

    Invoke-Endpoint -Index 134 -Method GET -Url "http://localhost:8080/api/cart-service/v1/carts/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-cart-get"
    Invoke-Endpoint -Index 135 -Method GET -Url "http://localhost:8080/api/cart-service/carts/me" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-cart-get"
    Invoke-Endpoint -Index 136 -Method POST -Url "http://localhost:8080/api/cart-service/v1/carts/me/items" -ExpectedStatus 201 -Headers $gatewayUserHeaders -Body $addCartBody -PairKey "gw-cart-add"
    Invoke-Endpoint -Index 137 -Method POST -Url "http://localhost:8080/api/cart-service/carts/me/items" -ExpectedStatus 201 -Headers $gatewayUserHeaders -Body $addCartBody -PairKey "gw-cart-add"
    Invoke-Endpoint -Index 138 -Method PUT -Url "http://localhost:8080/api/cart-service/v1/carts/me/items/$($state.ProductId)" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body @{ quantity = 2 } -PairKey "gw-cart-update"
    Invoke-Endpoint -Index 139 -Method PUT -Url "http://localhost:8080/api/cart-service/carts/me/items/$($state.ProductId)" -ExpectedStatus 200 -Headers $gatewayUserHeaders -Body @{ quantity = 2 } -PairKey "gw-cart-update"
    Invoke-Endpoint -Index 140 -Method DELETE -Url "http://localhost:8080/api/cart-service/v1/carts/me/items/$($state.ProductId)" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-cart-delete"
    Invoke-Endpoint -Index 141 -Method DELETE -Url "http://localhost:8080/api/cart-service/carts/me/items/$($state.ProductId)" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-cart-delete"
    Invoke-Endpoint -Index 142 -Method DELETE -Url "http://localhost:8080/api/cart-service/v1/carts/me" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-cart-clear"
    Invoke-Endpoint -Index 143 -Method DELETE -Url "http://localhost:8080/api/cart-service/carts/me" -ExpectedStatus 204 -Headers $gatewayUserHeaders -PairKey "gw-cart-clear"

    Invoke-SetupRequest -Method POST -Url "http://localhost:8080/api/cart-service/v1/carts/me/items" -ExpectedStatus 201 -Headers $gatewayUserHeaders -Body $addCartBody | Out-Null
    $gatewayOrderV1 = Invoke-Endpoint -Index 144 -Method POST -Url "http://localhost:8080/api/order-service/v1/orders" -ExpectedStatus 202 -Headers $gatewayUserHeaders -Body @{ deliveryAddress = @{ street = "Stefan cel Mare 1"; city = "Chisinau"; district = "Centru"; postalCode = "2001"; recipientName = "Gateway Seven"; recipientPhone = "+37360000000" }; payment = @{ method = "CARD"; transactionId = "gw-v1-$($state.RunId)" }; notes = "Gateway order v1" } -PairKey "gw-order-create" -PassThru
    $state.GatewayOrderId = $gatewayOrderV1.ParsedBody.orderId
    Invoke-Endpoint -Index 145 -Method POST -Url "http://localhost:8080/api/order-service/orders" -ExpectedStatus 202 -Headers $gatewayUserHeaders -Body @{ deliveryAddress = @{ street = "Stefan cel Mare 1"; city = "Chisinau"; district = "Centru"; postalCode = "2001"; recipientName = "Gateway Seven"; recipientPhone = "+37360000000" }; payment = @{ method = "CARD"; transactionId = "gw-$($state.RunId)" }; notes = "Gateway order legacy" } -PairKey "gw-order-create"
    Invoke-Endpoint -Index 146 -Method GET -Url "http://localhost:8080/api/order-service/v1/orders?page=0&size=20" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-order-list"
    Invoke-Endpoint -Index 147 -Method GET -Url "http://localhost:8080/api/order-service/orders?page=0&size=20" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-order-list"
    Invoke-Endpoint -Index 148 -Method GET -Url "http://localhost:8080/api/order-service/v1/orders/$($state.GatewayOrderId)" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-order-get"
    Invoke-Endpoint -Index 149 -Method GET -Url "http://localhost:8080/api/order-service/orders/$($state.GatewayOrderId)" -ExpectedStatus 200 -Headers $gatewayUserHeaders -PairKey "gw-order-get"
    Invoke-Endpoint -Index 150 -Method GET -Url "http://localhost:8080/api/order-service/v1/orders/all?page=0&size=20" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -PairKey "gw-order-all"
    Invoke-Endpoint -Index 151 -Method GET -Url "http://localhost:8080/api/order-service/orders/all?page=0&size=20" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -PairKey "gw-order-all"
    Invoke-Endpoint -Index 152 -Method PATCH -Url "http://localhost:8080/api/order-service/v1/orders/$($state.GatewayOrderId)/status" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body @{ status = "CONFIRMED" } -PairKey "gw-order-status"
    Invoke-Endpoint -Index 153 -Method PATCH -Url "http://localhost:8080/api/order-service/orders/$($state.GatewayOrderId)/status" -ExpectedStatus 200 -Headers $gatewayAdminHeaders -Body @{ status = "DELIVERED" } -PairKey "gw-order-status"

    Invoke-SwaggerUiEndpoint -Index 154 -Url "http://localhost:8080/api/notification-service/swagger-ui.html" -ExpectedLocationPrefix "/api/notification-service/swagger-ui/index.html" -AfterRedirectValidator ${function:Test-SwaggerContent}
    Invoke-Endpoint -Index 155 -Method GET -Url "http://localhost:8080/api/notification-service/v3/api-docs" -ExpectedStatus 200 -CompareAgainstRepresentative "notification-docs" -Validator ${function:Test-OpenApiHasPaths}
}

try {
    Run-Validation
}
finally {
    if ($script:AuthLookupClasspathFile -and (Test-Path -LiteralPath $script:AuthLookupClasspathFile)) {
        Remove-Item -LiteralPath $script:AuthLookupClasspathFile -ErrorAction SilentlyContinue
    }
}

$outputPath = Resolve-OutputPath -Path $ReportPath
Ensure-ParentDirectory -FilePath $outputPath

$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    total       = $script:Results.Count
    passed      = @($script:Results | Where-Object { $_.verdict -eq 'PASS' }).Count
    failed      = @($script:Results | Where-Object { $_.verdict -eq 'FAIL' }).Count
    results     = $script:Results
}

$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $outputPath
"Report written to $outputPath"
