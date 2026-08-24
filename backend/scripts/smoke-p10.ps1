param([string]$BaseUrl = "http://127.0.0.1:8475")
$ErrorActionPreference = "Stop"
function Read-Err($ex) {
    try { $r = New-Object IO.StreamReader($ex.Exception.Response.GetResponseStream()); $t = $r.ReadToEnd(); $r.Close(); return $t } catch { return "" }
}
$pw = (Get-Content "config\initial-admin-credentials.txt" | Select-String "^Password:").Line.Substring(10)
$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = "admin"; password = $pw } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[OK] Login"

$ts = Get-Date -Format "HHmmss"
# Products with known stock
$pa = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="ISS-A-$ts"; name="Issue Test A"; unitId=1; minStock=1; costPrice=1; sellingPrice=2 } | ConvertTo-Json)
$pb = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="ISS-B-$ts"; name="Issue Test B"; unitId=4; minStock=1; costPrice=1; sellingPrice=2 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-in" -Headers $h -ContentType "application/json" `
    -Body (@{ productId=$pa.id; quantity=20 } | ConvertTo-Json) | Out-Null
Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-in" -Headers $h -ContentType "application/json" `
    -Body (@{ productId=$pb.id; quantity=10 } | ConvertTo-Json) | Out-Null

# --- Create DRAFT ---
$iss = Invoke-RestMethod -Method Post "$BaseUrl/api/issues" -Headers $h -ContentType "application/json" `
    -Body (@{ department="Maintenance"; requestedBy="John (floor 2)"; notes="monthly consumables"; items=@(
        @{ productId=$pa.id; quantity=5 },
        @{ productId=$pb.id; quantity=2 }) } | ConvertTo-Json -Depth 5)
if ($iss.status -ne "DRAFT") { Write-Output "[FAIL] create status=$($iss.status)" } else { Write-Output "[OK] Issue $($iss.issueNumber) DRAFT lines=$($iss.items.Count)" }

# --- Update draft ---
$upd = Invoke-RestMethod -Method Put "$BaseUrl/api/issues/$($iss.id)" -Headers $h -ContentType "application/json" `
    -Body (@{ department="Maintenance"; requestedBy="John"; notes=""; items=@(
        @{ productId=$pa.id; quantity=6 },
        @{ productId=$pb.id; quantity=3 }) } | ConvertTo-Json -Depth 5)
if ("$($upd.items[0].quantity)/$($upd.items[1].quantity)" -ne "6/3") { Write-Output "[FAIL] update quantities wrong" } else { Write-Output "[OK] Draft updated 6/3" }

# --- Complete from DRAFT must fail ---
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss.id)/complete" -Headers $h | Out-Null
    Write-Output "[FAIL] complete-from-DRAFT accepted"
} catch {
    if ((Read-Err $_) -match "INVALID_ISSUE_STATE") { Write-Output "[OK] Complete from DRAFT rejected" } else { Write-Output "[FAIL] $(Read-Err $_)" }
}

# --- Approve then complete ---
Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss.id)/approve" -Headers $h | Out-Null
$done = Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss.id)/complete" -Headers $h
if ($done.status -ne "COMPLETED") { Write-Output "[FAIL] complete status=$($done.status)" } else { Write-Output "[OK] COMPLETED approvedBy/approved flow ok" }
$paAfter = Invoke-RestMethod "$BaseUrl/api/products/$($pa.id)" -Headers $h
$pbAfter = Invoke-RestMethod "$BaseUrl/api/products/$($pb.id)" -Headers $h
if ("$($paAfter.currentStock)" -ne "14") { Write-Output "[FAIL] pa=$($paAfter.currentStock) expected 14" } else { Write-Output "[OK] A stock 20->14" }

# --- Overdraw at complete time rolls back ---
$pOver = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="ISS-C-$ts"; name="Issue Test C"; unitId=1; minStock=1; costPrice=1; sellingPrice=2 } | ConvertTo-Json)
Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-in" -Headers $h -ContentType "application/json" `
    -Body (@{ productId=$pOver.id; quantity=2 } | ConvertTo-Json) | Out-Null
$iss2 = Invoke-RestMethod -Method Post "$BaseUrl/api/issues" -Headers $h -ContentType "application/json" `
    -Body (@{ department="Ops"; items=@( @{ productId=$pOver.id; quantity=99 } ) } | ConvertTo-Json -Depth 5)
Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss2.id)/approve" -Headers $h | Out-Null
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss2.id)/complete" -Headers $h | Out-Null
    Write-Output "[FAIL] overdraw accepted!"
} catch {
    if ((Read-Err $_) -match "INSUFFICIENT_STOCK") { Write-Output "[OK] Overdraw rejected INSUFFICIENT_STOCK" } else { Write-Output "[FAIL] $(Read-Err $_)" }
}
$pOverAfter = Invoke-RestMethod "$BaseUrl/api/products/$($pOver.id)" -Headers $h
if ("$($pOverAfter.currentStock)" -ne "2") { Write-Output "[FAIL] rollback failed, stock=$($pOverAfter.currentStock)" } else { Write-Output "[OK] Rollback verified: stock still 2, status=$($pOverAfter.stockStatus)" }

# --- Cancel completed must fail ---
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/issues/$($iss.id)/cancel" -Headers $h | Out-Null
    Write-Output "[FAIL] cancel-COMPLETED accepted"
} catch {
    if ((Read-Err $_) -match "INVALID_ISSUE_STATE") { Write-Output "[OK] Cancel of COMPLETED rejected" } else { Write-Output "[FAIL] $(Read-Err $_)" }
}

Write-Output "P10 SMOKE COMPLETE"
