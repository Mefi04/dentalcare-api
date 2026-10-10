$ErrorActionPreference = 'Stop'

$headers = @{
    'Content-Type' = 'application/json'
    'Idempotency-Key' = [guid]::NewGuid().ToString()
}
$rand = Get-Random -Minimum 10000 -Maximum 99999
$body = @{
    fullName = "Test Live User $rand"
    phone = "502555$rand"
    requestedAt = (Get-Date).AddDays(3).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:00Z')
    reason = 'Revision'
} | ConvertTo-Json

Write-Host "1. Submitting public request..."
$res = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/public/appointment-requests" -Method Post -Headers $headers -Body $body
$reqId = $res.requestId
$token = $res.conversationToken
Write-Host "Created requestId: $reqId"
Write-Host "Got token: $($token.Substring(0, 10))..."

Write-Host "2. Getting conversation..."
$convHeaders = @{ 'Authorization' = "Bearer $token" }
$conv = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/public/appointment-requests/$reqId/conversation" -Method Get -Headers $convHeaders
Write-Host "Status: $($conv.status)"

# Now login as admin/reception to propose a time
Write-Host "3. Logging in as admin..."
$loginBody = @{
    cui = '3224802211326'
    password = 'Holamundo#123'
} | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Headers @{'Content-Type'='application/json'} -Body $loginBody
$jwt = $loginRes.accessToken
Write-Host "Got admin JWT!"

# Get a dentist user id
Write-Host "4. Getting professionals..."
$profs = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/public/professionals" -Method Get
$dentist = if ($profs -is [array]) { $profs[0] } else { $profs }
Write-Host "Dentist: $($dentist.fullName) ($($dentist.id))"

# Propose a time
Write-Host "5. Proposing time..."
$propTime = (Get-Date).AddDays(4).ToUniversalTime().ToString('yyyy-MM-ddTHH:00:00Z')
$propBody = @{
    professionalId = $dentist.id
    proposedAt = $propTime
} | ConvertTo-Json
$propRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/appointment-requests/$reqId/proposal" -Method Post -Headers @{
    'Content-Type' = 'application/json'
    'Authorization' = "Bearer $jwt"
} -Body $propBody
Write-Host "Proposed! Request status: $($propRes.status)"

# Now accept proposal as public user!
Write-Host "6. Accepting proposal as public user..."
$decHeaders = @{
    'Content-Type' = 'application/json'
    'Authorization' = "Bearer $token"
    'Idempotency-Key' = [guid]::NewGuid().ToString()
}
$decBody = @{
    decision = 'ACCEPT'
} | ConvertTo-Json

try {
    $decRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/public/appointment-requests/$reqId/decision" -Method Post -Headers $decHeaders -Body $decBody
    Write-Host "SUCCESS! Status: $($decRes.status), appointmentId: $($decRes.appointmentId)"
} catch {
    Write-Host "FAILED TO ACCEPT!"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $respText = $reader.ReadToEnd()
    Write-Host "StatusCode: $($_.Exception.Response.StatusCode)"
    Write-Host "Response Body: $respText"
}