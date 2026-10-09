param([switch]$KeepRunning)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo
# Disposable credentials stay in this process; never printed or written to evidence.
$rsa = [System.Security.Cryptography.RSA]::Create(2048)
$env:VERIFY_JWT_PRIVATE_KEY = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
$env:VERIFY_JWT_PUBLIC_KEY = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
$env:VERIFY_DB_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:GRAFANA_ADMIN_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:GRAFANA_ADMIN_USER = 'monitor'
$env:GRAFANA_PORT = '13001'
$compose = @('-p','dentalcare-issue149','-f','docker-compose.yml','-f','monitoring/compose.verify.yml','--profile','monitoring')
function Compose { & docker compose @compose @args; if ($LASTEXITCODE -ne 0) { throw 'Docker Compose command failed' } }
function PromQuery([string]$query) {
    $uri = 'http://localhost:9090/api/v1/query?query=' + [Uri]::EscapeDataString($query)
    $raw = Compose exec -T prometheus wget -qO- $uri
    $result = ($raw -join "`n") | ConvertFrom-Json
    if ($result.status -ne 'success') { throw 'Prometheus query failed' }
    return $result.data.result
}
function Require([bool]$condition, [string]$message) { if (-not $condition) { throw $message } }
$evidence = [ordered]@{ timestamp = [DateTime]::UtcNow.ToString('o'); project = 'dentalcare-issue149' }
try {
    Compose up -d --build backend verification-db grafana
    Require (-not ((Compose ps --status running --services) -contains "prometheus")) 'Prometheus must be stopped before the cold burst'
    Write-Output 'Waiting for isolated backend; Prometheus remains stopped'
    $ready = $false
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        Start-Sleep -Seconds 3
        try { $health = ((Compose exec -T backend wget -qO- http://localhost:9091/actuator/health) -join "`n") | ConvertFrom-Json; if ($health.status -eq "UP") { $ready=$true; break } } catch { }
    }
    Require $ready 'Isolated backend did not become healthy'
    $base = 'http://127.0.0.1:18080'
    $ids = @()
    # No clinical route or business write. Headers/query contain synthetic sentinels only.
    for ($i=0; $i -lt 40; $i++) {
        $response = Invoke-WebRequest "$base/api/v1/audit-events?token=PRIVATE_SENTINEL_QUERY" -Headers @{
            Authorization='Bearer PRIVATE_SENTINEL_JWT'; 'X-Request-ID'='PRIVATE_SENTINEL_ID'; 'X-Conversation-Token'='PRIVATE_SENTINEL_CONVERSATION'
        } -SkipHttpErrorCheck
        Require ($response.StatusCode -eq 401) 'Expected synthetic 401'
        $ids += [string]$response.Headers['X-Request-ID'][0]
    }
    Require (($ids | Select-Object -Unique).Count -eq 40) 'Correlation IDs must be unique'
    $evidence.correlationIdsUnique = $true
    $evidence.burstBeforeFirstScrape = $true
    Compose up -d prometheus
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 3
        try { $series = @(PromQuery 'up{job="dentalcare"}'); if ($series.Count -gt 0 -and $series[0].value[1] -eq '1') { $ready=$true; break } } catch { }
    }
    Require $ready 'Backend was not scraped successfully'
    $evidence.scrape = @(PromQuery 'up{job="dentalcare"}')
    $evidence.hikari = @(PromQuery 'hikaricp_connections_max')
    Require ($evidence.hikari.Count -gt 0) 'Hikari metrics missing'
    $evidence.auditBaseline = @(PromQuery 'dentalcare_audit_failures_total')
    Require ($evidence.auditBaseline.Count -eq 1 -and $evidence.auditBaseline[0].value[1] -eq '0') 'Missing zero audit baseline'
    $evidence.storageBaseline = @(PromQuery 'dentalcare_storage_operations_seconds_count{result="failure"}')
    Require ($evidence.storageBaseline.Count -eq 5) 'Missing bounded storage baselines'
    $evidence.coldHttpWindow = @(PromQuery 'dentalcare_http_window_requests{status="401",route="application"}')
    Require ($evidence.coldHttpWindow.Count -eq 1 -and $evidence.coldHttpWindow[0].value[1] -eq '40') 'Cold HTTP burst was not observed exactly once'
    foreach ($path in @('/actuator/prometheus','/actuator/metrics','/actuator/env','/actuator/health')) {
        $response = Invoke-WebRequest "$base$path" -SkipHttpErrorCheck
        Require ($response.StatusCode -in @(401,403,404)) 'Management endpoint exposed on public port'
    }
    $evidence.publicManagementDenied = $true
    Write-Output 'Cold 401 burst observed after first scrape; waiting for alert evaluation'
    $firing = $false
    for ($attempt=0; $attempt -lt 18; $attempt++) {
        Start-Sleep -Seconds 5
        $alerts = @(PromQuery 'ALERTS{alertname="AuthenticationFailures",alertstate="firing"}')
        if ($alerts.Count -gt 0) { $firing=$true; $evidence.alertFiring=$alerts; break }
    }
    Require $firing 'AuthenticationFailures did not fire'
    $evidence.httpP95 = @(PromQuery 'histogram_quantile(0.95,sum by (le) (http_server_requests_seconds_bucket{uri!="management"}))')
    Require ($evidence.httpP95.Count -gt 0 -and $evidence.httpP95[0].value[1] -notin @('NaN','+Inf','-Inf')) 'No finite p95 sample'
    $credential = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("monitor:$env:GRAFANA_ADMIN_PASSWORD"))
    $headers = @{ Authorization = "Basic $credential" }
    $grafana = 'http://127.0.0.1:13001'
    $dashboard = Invoke-RestMethod "$grafana/api/dashboards/uid/dentalcare-security" -Headers $headers
    Require ($dashboard.dashboard.panels.Count -ge 10) 'Dashboard not provisioned'
    $query = [Uri]::EscapeDataString('http_server_requests_seconds_count{status="401"}')
    $values = Invoke-RestMethod "$grafana/api/datasources/proxy/uid/dentalcare-prometheus/api/v1/query?query=$query" -Headers $headers
    Require ($values.data.result.Count -gt 0) 'Grafana cannot query real HTTP values'
    $evidence.grafanaHttp401 = $values.data.result
    $anonymous = Invoke-WebRequest "$grafana/api/dashboards/uid/dentalcare-security" -SkipHttpErrorCheck
    Require ($anonymous.StatusCode -eq 401) 'Grafana anonymous access must be denied'
    $evidence.grafanaAnonymousDenied = $true
    $container = Compose ps -q backend
    $ports = (& docker inspect --format '{{json .NetworkSettings.Ports}}' $container) | ConvertFrom-Json
    Require (-not $ports.'9091/tcp') 'Management port must not be published'
    Write-Output 'Alert firing confirmed; waiting for normal five-minute window recovery'
    $recovered = $false
    for ($attempt=0; $attempt -lt 80; $attempt++) {
        Start-Sleep -Seconds 5
        $alerts = @(PromQuery 'ALERTS{alertname="AuthenticationFailures",alertstate="firing"}')
        if ($alerts.Count -eq 0) { $recovered=$true; break }
    }
    Require $recovered 'Alert did not recover after traffic normalized'
    $evidence.alertRecovered = $true
    $logLines = Compose logs --no-log-prefix backend
    Require (-not (($logLines -join "`n") -match 'PRIVATE_SENTINEL|Bearer |patients/|SELECT |insert into|stack_trace')) 'Unsafe content in operational logs'
    $jsonLines = @($logLines | Where-Object { $_.StartsWith('{') })
    Require ($jsonLines.Count -gt 0) 'No structured logs found'
    foreach ($line in $jsonLines) {
        $record = $line | ConvertFrom-Json
        Require (@($record.PSObject.Properties.Name | Where-Object { $_ -notin @('timestamp','level','logger','event','request_id','exception_type','status','duration_ms') }).Count -eq 0) 'Unexpected log field'
    }
    $evidence.logAllowlistPassed = $true
    $evidence.structuredLogRecordsChecked = $jsonLines.Count
    New-Item -ItemType Directory -Force monitoring/evidence | Out-Null
    $evidence | ConvertTo-Json -Depth 20 | Set-Content monitoring/evidence/local-verification.json
    Write-Output 'Local verification passed; evidence saved without credentials'
} finally {
    if (-not $KeepRunning) { Compose down --volumes }
    $rsa.Dispose()
    Remove-Item Env:VERIFY_JWT_PRIVATE_KEY,Env:VERIFY_JWT_PUBLIC_KEY,Env:VERIFY_DB_PASSWORD,Env:GRAFANA_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
