param([string]$BaseUrl = "http://127.0.0.1:8475")
$ErrorActionPreference = "Stop"
function Read-ErrorBody($ex) {
    try { $r = New-Object IO.StreamReader($ex.Exception.Response.GetResponseStream()); $t = $r.ReadToEnd(); $r.Close(); return $t } catch { return "" }
}
$pw = (Get-Content "config\initial-admin-credentials.txt" | Select-String "^Password:").Line.Substring(10)
$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = "admin"; password = $pw } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[OK] Login"

# --- Supplier CRUD ---
$ts0 = Get-Date -Format "HHmmss"
$sup = Invoke-RestMethod -Method Post "$BaseUrl/api/suppliers" -Headers $h -ContentType "application/json" `
    -Body (@{ name = "Global Parts $ts0"; phone = "+1-555-0100"; email = "sales@globalparts.example"
              address = "12 Industrial Rd"; taxNumber = "TX-99881" } | ConvertTo-Json)
Write-Output "[OK] Supplier id=$($sup.id)"
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/suppliers" -Headers $h -ContentType "application/json" `
        -Body ('{"name":"global parts ' + $ts0 + '"}') | Out-Null
    Write-Output "[FAIL] duplicate supplier accepted"
} catch { Write-Output "[OK] Duplicate supplier rejected -> $((Read-ErrorBody $_) -replace '.*code\":\"(\w+)\".*','$1')" }
$supList = Invoke-RestMethod "$BaseUrl/api/suppliers?search=global" -Headers $h
if ($supList.totalElements -ge 1) { Write-Output "[OK] Supplier search works ($($supList.totalElements))" }

# --- Products for the purchase ---
$ts = Get-Date -Format "HHmmss"
$p1 = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="SKU-A-$ts"; name="Bolt M8"; unitId=1; minStock=20; costPrice=0.40; sellingPrice=0.90 } | ConvertTo-Json)
$p2 = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="SKU-B-$ts"; name="Cable Spool"; unitId=7; minStock=5; costPrice=8.00; sellingPrice=15.00 } | ConvertTo-Json)

# --- Purchase create (exact cents check) ---
$pur = Invoke-RestMethod -Method Post "$BaseUrl/api/purchases" -Headers $h -ContentType "application/json" `
    -Body (@{ supplierId = $sup.id; notes = "weekly restock"; items = @(
        @{ productId = $p1.id; quantity = 10; unitCostPrice = 3.25 },
        @{ productId = $p2.id; quantity = 5.5; unitCostPrice = 10.00 }) } | ConvertTo-Json -Depth 5)
if ($pur.totalAmount -ne 87.50) { Write-Output "[FAIL] expected total 87.50 got $($pur.totalAmount)" } else { Write-Output "[OK] Purchase $($pur.purchaseNumber) total=$($pur.totalAmount)" }

# --- Receive ---
$recv = Invoke-RestMethod -Method Post "$BaseUrl/api/purchases/$($pur.id)/receive" -Headers $h
if ($recv.status -ne "RECEIVED") { Write-Output "[FAIL] status=$($recv.status)" } else { Write-Output "[OK] Received by=$($recv.receivedByName)" }
$p1After = Invoke-RestMethod "$BaseUrl/api/products/$($p1.id)" -Headers $h
$p2After = Invoke-RestMethod "$BaseUrl/api/products/$($p2.id)" -Headers $h
if ("$($p1After.currentStock)" -ne "10") { Write-Output "[FAIL] p1 stock=$($p1After.currentStock)" } else { Write-Output "[OK] p1 stock=10" }
if ("$($p2After.currentStock)" -ne "5.5") { Write-Output "[FAIL] p2 stock=$($p2After.currentStock)" } else { Write-Output "[OK] p2 stock=5.5" }
$mv = Invoke-RestMethod "$BaseUrl/api/inventory/movements?productId=$($p1.id)&movementType=STOCK_IN" -Headers $h
$refOk = $mv.content | Where-Object { $_.reference -eq $pur.purchaseNumber }
if (-not $refOk) { Write-Output "[FAIL] movement reference missing" } else { Write-Output "[OK] STOCK_IN movement ref=$($refOk.reference)" }

# --- Double receive must fail ---
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/purchases/$($pur.id)/receive" -Headers $h | Out-Null
    Write-Output "[FAIL] double receive accepted"
} catch {
    $b = Read-ErrorBody $_
    if ($b -match "INVALID_PURCHASE_STATE") { Write-Output "[OK] Double receive rejected" } else { Write-Output "[FAIL] $b" }
}

# --- Cancel pending ---
$pur2 = Invoke-RestMethod -Method Post "$BaseUrl/api/purchases" -Headers $h -ContentType "application/json" `
    -Body (@{ supplierId = $sup.id; items = @( @{ productId = $p1.id; quantity = 2; unitCostPrice = 3.00 } ) } | ConvertTo-Json -Depth 5)
$cx = Invoke-RestMethod -Method Post "$BaseUrl/api/purchases/$($pur2.id)/cancel" -Headers $h
if ($cx.status -ne "CANCELLED") { Write-Output "[FAIL] cancel status=$($cx.status)" } else { Write-Output "[OK] Cancelled $($cx.purchaseNumber)" }

Write-Output "P9 SMOKE COMPLETE"
