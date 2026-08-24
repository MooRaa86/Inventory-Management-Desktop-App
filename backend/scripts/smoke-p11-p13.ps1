$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$BaseUrl = "http://127.0.0.1:8475"
$root = Split-Path -Parent $PSScriptRoot

# --- login ---
$credLine = Get-Content (Join-Path $root "data\..\config\initial-admin-credentials.txt") |
    Select-String "^Password:"
$password = $credLine.Line.Substring(10).Trim()
$username = (Get-Content (Join-Path $root "config\initial-admin-credentials.txt") | Select-String "^Username:").Line.Substring(10).Trim()

function Read-Err($ex) {
    try {
        $stream = $ex.Exception.Response.GetResponseStream()
        $reader = New-Object IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch { return "" }
}

$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail=$username; password=$password } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
"[OK] Login"

# --- P11 dashboard ---
$d = Invoke-RestMethod -Method Get "$BaseUrl/api/dashboard" -Headers $h
if ($d.totalProducts -lt 1) { throw "dashboard totalProducts=0" }
if (-not $d.recentMovements) { throw "dashboard recentMovements empty" }
if (-not $d.stockInOutChart -and -not $d.lowStockProducts) { throw "dashboard charts empty" }
if ($null -eq $d.backup) { throw "dashboard backup section missing" }
"[OK] Dashboard totals products=$($d.totalProducts) activeSuppliers=$($d.activeSuppliers) pendingPurchases=$($d.pendingPurchases)"

# --- P12 reports ---
$inv = Invoke-RestMethod -Method Post "$BaseUrl/api/reports/inventory" -Headers $h -ContentType "application/json" `
    -Body '{"format":"JSON"}'
if ($inv.rows.Count -lt 1) { throw "inventory report rows empty" }
"[OK] Report inventory JSON rows=$($inv.rows.Count)"

foreach ($spec in @(
    @{ t="low-stock"; f="CSV" },
    @{ t="movements"; f="XLSX" },
    @{ t="purchases"; f="PDF" },
    @{ t="suppliers"; f="CSV" },
    @{ t="issues";    f="CSV" },
    @{ t="audit";     f="CSV" })) {
    $body = @{ format = $spec.f } | ConvertTo-Json
    $gen = Invoke-RestMethod -Method Post "$BaseUrl/api/reports/$($spec.t)" -Headers $h -ContentType "application/json" -Body $body
    if (-not $gen.fileName) { throw "$($spec.t) export missing fileName" }
    $dl = Join-Path $root "exports\reports\$($gen.fileName)"
    if (-not (Test-Path $dl)) { throw "$($spec.t) file missing on disk: $dl" }
    if ((Get-Item $dl).Length -lt 50) { throw "$($spec.t) file suspiciously small" }
}
"[OK] All 6 report exports written to exports\reports"

# download round-trip
$csvGen = Invoke-RestMethod -Method Post "$BaseUrl/api/reports/inventory" -Headers $h -ContentType "application/json" `
    -Body '{"format":"CSV"}'
$wc = New-Object System.Net.WebClient
$wc.Headers.Add("Authorization", $h.Authorization)
$dlBytes = $wc.DownloadData("$BaseUrl/api/reports/files/$($csvGen.fileName)")
if ($dlBytes.Length -lt 50) { throw "download too small: $($dlBytes.Length)" }
"[OK] Download endpoint serves files ($($dlBytes.Length) bytes)"

# path traversal guard
try {
    $wc2 = New-Object System.Net.WebClient
    $wc2.Headers.Add("Authorization", $h.Authorization)
    $wc2.DownloadData("$BaseUrl/api/reports/files/..%5C..%5Cconfig%5Cjwt-secret.key") | Out-Null
    throw "path traversal not blocked"
} catch {
    $msg = "$($_.Exception.Message)"
    if ($msg -match "path traversal not blocked") { throw }
    "[OK] Path traversal blocked ($($msg.Substring(0,[Math]::Min(60,$msg.Length))))"
}

# --- P13 audit api ---
$logs = Invoke-RestMethod -Method Get "$BaseUrl/api/audit-logs?size=5" -Headers $h
if ($logs.content.Count -lt 1) { throw "audit logs empty" }
$filtered = Invoke-RestMethod -Method Get "$BaseUrl/api/audit-logs?action=REPORT_EXPORT&size=5" -Headers $h
if ($filtered.content.Count -lt 1) { throw "audit filter REPORT_EXPORT empty" }
"[OK] Audit search + filter (REPORT_EXPORT hits=$($filtered.content.Count))"

# --- P13 users api ---
$users = Invoke-RestMethod -Method Get "$BaseUrl/api/users?search=admin" -Headers $h
if ($users.totalElements -lt 1) { throw "admin user not found" }
$adminId = ($users.content | Where-Object { $_.roles -contains "ADMIN" } | Select-Object -First 1).id
"[OK] Users list (admin id=$adminId)"

$roles = Invoke-RestMethod -Method Get "$BaseUrl/api/users/roles" -Headers $h
$nonAdminRole = ($roles | Where-Object { $_.name -ne "ADMIN" } | Select-Object -First 1).name
if (-not $nonAdminRole) { throw "no secondary role found" }

$stamp = Get-Date -Format "yyyyMMddHHmmss"
# weak password rejected
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/users" -Headers $h -ContentType "application/json" `
        -Body (@{ username="u$stamp"; email="u$stamp@t.local"; fullName="Tmp User"; password="short"; roles=@($nonAdminRole) } | ConvertTo-Json) | Out-Null
    throw "weak password accepted"
} catch {
    $errBody = Read-Err $_
    if ($errBody -notmatch "WEAK_PASSWORD") { throw "expected WEAK_PASSWORD got: $errBody" }
}
"[OK] Weak password rejected"

$newUser = Invoke-RestMethod -Method Post "$BaseUrl/api/users" -Headers $h -ContentType "application/json" `
    -Body (@{ username="u$stamp"; email="u$stamp@t.local"; fullName="Tmp User"; password="Passw0rd123"; roles=@($nonAdminRole) } | ConvertTo-Json)
"[OK] User created $($newUser.username) roles=$($newUser.roles -join '+')"

$upd = Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($newUser.id)" -Headers $h -ContentType "application/json" `
    -Body (@{ fullName="Tmp Renamed" } | ConvertTo-Json)
if ($upd.fullName -ne "Tmp Renamed") { throw "profile update failed" }
"[OK] Profile updated"

Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($newUser.id)/password" -Headers $h -ContentType "application/json" `
    -Body '{"newPassword":"NewPass456","mustChangePassword":true}' | Out-Null
"[OK] Password reset by admin"

$roleSet = Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($newUser.id)/roles" -Headers $h -ContentType "application/json" `
    -Body (@{ roles=@($nonAdminRole,"WAREHOUSE_MANAGER") } | ConvertTo-Json)
"[OK] Roles assigned -> $($roleSet.roles -join '+')"

# duplicate username guard
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/users" -Headers $h -ContentType "application/json" `
        -Body (@{ username="u$stamp"; email="x$stamp@t.local"; fullName="Dup"; password="Passw0rd123"; roles=@($nonAdminRole) } | ConvertTo-Json) | Out-Null
    throw "duplicate username accepted"
} catch {
    $errBody = Read-Err $_
    if ($errBody -notmatch "DUPLICATE_USERNAME") { throw "expected DUPLICATE_USERNAME got: $errBody" }
}
"[OK] Duplicate username rejected"

# last-admin demotion guard: strip ADMIN from our own (only) admin account
try {
    Invoke-RestMethod -Method Put "$BaseUrl/api/users/$adminId/roles" -Headers $h -ContentType "application/json" `
        -Body (@{ roles=@($nonAdminRole) } | ConvertTo-Json) | Out-Null
    throw "last admin demotion allowed"
} catch {
    $errBody = Read-Err $_
    if ($errBody -notmatch "LAST_ADMIN") { throw "expected LAST_ADMIN got: $errBody" }
}
"[OK] Last-admin demotion rejected"

# cannot disable self
try {
    Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($login.user.id)/active?value=false" -Headers $h | Out-Null
    throw "self-disable allowed"
} catch {
    $errBody = Read-Err $_
    if ($errBody -notmatch "CANNOT_DISABLE_SELF") { throw "expected CANNOT_DISABLE_SELF got: $errBody" }
}
"[OK] Self-disable rejected"

# disable / enable other user
Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($newUser.id)/active?value=false" -Headers $h | Out-Null
$disabled = Invoke-RestMethod -Method Get "$BaseUrl/api/users/$($newUser.id)" -Headers $h
if ($disabled.active) { throw "user still active after disable" }
Invoke-RestMethod -Method Put "$BaseUrl/api/users/$($newUser.id)/active?value=true" -Headers $h | Out-Null
"[OK] Disable/enable user"

"P11-P13 SMOKE COMPLETE"
