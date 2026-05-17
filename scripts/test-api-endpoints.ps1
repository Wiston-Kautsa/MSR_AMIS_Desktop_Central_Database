$identifier = if ($env:MSR_AMIS_TEST_LOGIN_IDENTIFIER) { $env:MSR_AMIS_TEST_LOGIN_IDENTIFIER } else { $env:MSR_AMIS_SETUP_ADMIN_EMAIL }
$password = if ($env:MSR_AMIS_TEST_LOGIN_PASSWORD) { $env:MSR_AMIS_TEST_LOGIN_PASSWORD } else { 'admin123' }
if ([string]::IsNullOrWhiteSpace($identifier)) {
  throw 'Set MSR_AMIS_TEST_LOGIN_IDENTIFIER or MSR_AMIS_SETUP_ADMIN_EMAIL before running this script.'
}

$body = @{
  identifier = $identifier
  password   = $password
} | ConvertTo-Json -Compress

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8090/api/auth/login" `
  -ContentType "application/json" `
  -Body $body

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }

Write-Host "`nLOGIN OK`n" -ForegroundColor Green
$login.user | Format-List

Write-Host "`nUSERS`n" -ForegroundColor Cyan
(Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8090/api/users" `
  -Headers $headers) |
  Select-Object id, fullName, email, role, status |
  Format-Table -AutoSize

Write-Host "`nEQUIPMENT`n" -ForegroundColor Cyan
(Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8090/api/equipment" `
  -Headers $headers) |
  Select-Object assetCode, name, category, status |
  Format-Table -AutoSize

Write-Host "`nASSIGNMENTS`n" -ForegroundColor Cyan
(Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8090/api/assignments" `
  -Headers $headers) |
  Select-Object id, person, equipmentType, quantity, distributedCount, status |
  Format-Table -AutoSize

Write-Host "`nPENDING RETURNS`n" -ForegroundColor Cyan
(Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8090/api/assignments/pending-returns" `
  -Headers $headers) |
  Select-Object id, person, equipmentType, quantity, distributedCount, status |
  Format-Table -AutoSize

Write-Host "`nDASHBOARD`n" -ForegroundColor Cyan
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8090/api/dashboard/summary" `
  -Headers $headers | Format-List
