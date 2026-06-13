$ErrorActionPreference = 'Stop'

$appUrl = if ($env:APP_URL) { $env:APP_URL } else { 'http://localhost:3000' }
$email = if ($env:APP_ADMIN_EMAIL) { $env:APP_ADMIN_EMAIL } else { 'admin@app.local' }
$password = if ($env:APP_ADMIN_PASSWORD) { $env:APP_ADMIN_PASSWORD } else { 'admin123' }

Write-Host "Logging into $appUrl as $email..."

$loginBody = @{
  email = $email
  password = $password
} | ConvertTo-Json

$loginResponse = Invoke-WebRequest `
  -UseBasicParsing `
  -Method POST `
  -Uri "$appUrl/api/auth/login" `
  -ContentType 'application/json' `
  -Body $loginBody

$cookieHeader = (($loginResponse.Headers['Set-Cookie'] -split ';')[0]).Trim()

Write-Host 'Triggering item vector reindex...'

$reindexResponse = Invoke-WebRequest `
  -UseBasicParsing `
  -Method POST `
  -Uri "$appUrl/api/items/reindex" `
  -Headers @{ Cookie = $cookieHeader }

$reindexResponse.Content
Write-Host 'Reindex request completed.'
