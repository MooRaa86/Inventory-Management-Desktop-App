param([string]$BaseUrl = "http://127.0.0.1:8475")
$ErrorActionPreference = "Stop"
$pw = (Get-Content "config\initial-admin-credentials.txt" | Select-String "^Password:").Line.Substring(10)
$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = "admin"; password = $pw } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[OK] Login"

$sku = "SKU-P8-{0}" -f (Get-Date -Format "HHmmss")
$prod = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku = $sku; name = "Inventory Test Widget"; unitId = 1; minStock = 5
              costPrice = 1.00; sellingPrice = 2.00 } | ConvertTo-Json)
Write-Output "[OK] Product id=$($prod.id) sku=$($prod.sku)"

$m1 = Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-in" -Headers $h -ContentType "application/json" `
    -Body (@{ productId = $prod.id; quantity = 50; reference = "PO-001"; notes = "initial delivery" } | ConvertTo-Json)
if ("$($m1.previousStock)->$($m1.newStock)" -ne "0->50") { Write-Output "[FAIL] stock-in got $($m1.previousStock)->$($m1.newStock)" } else { Write-Output "[OK] Stock-in 0->50 type=$($m1.movementType)" }

$m2 = Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-out" -Headers $h -ContentType "application/json" `
    -Body (@{ productId = $prod.id; quantity = 20; reason = "Sale order SO-11"; reference = "SO-11" } | ConvertTo-Json)
if ("$($m2.previousStock)->$($m2.newStock)" -ne "50->30") { Write-Output "[FAIL] stock-out got $($m2.previousStock)->$($m2.newStock)" } else { Write-Output "[OK] Stock-out 50->30" }

# Insufficient stock must be rejected
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-out" -Headers $h -ContentType "application/json" `
        -Body (@{ productId = $prod.id; quantity = 100; reason = "too much" } | ConvertTo-Json) | Out-Null
    Write-Output "[FAIL] insufficient stock accepted!"
} catch {
    $resp = $_.Exception.Response
    $code = [int]$resp.StatusCode
    $bodyText = ""
    try {
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        $bodyText = $reader.ReadToEnd()
        $reader.Close()
    } catch {}
    if ($code -eq 422 -and $bodyText -match "INSUFFICIENT_STOCK") {
        Write-Output "[OK] Insufficient stock rejected -> $bodyText"
    } else {
        Write-Output "[FAIL] expected 422 INSUFFICIENT_STOCK, got code=$code body=$bodyText"
    }
}

$aOut = Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/adjust" -Headers $h -ContentType "application/json" `
    -Body (@{ productId = $prod.id; direction = "OUT"; quantity = 5; reason = "Damaged items" } | ConvertTo-Json)
if ("$($aOut.previousStock)->$($aOut.newStock)" -ne "30->25") { Write-Output "[FAIL] adjust out $($aOut.previousStock)->$($aOut.newStock)" } else { Write-Output "[OK] Adjustment OUT 30->25 type=$($aOut.movementType)" }

Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/adjust" -Headers $h -ContentType "application/json" `
    -Body (@{ productId = $prod.id; direction = "IN"; quantity = 10; reason = "Found in back room" } | ConvertTo-Json) | Out-Null

# Invalid quantities rejected
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/inventory/stock-in" -Headers $h -ContentType "application/json" `
        -Body (@{ productId = $prod.id; quantity = 0 } | ConvertTo-Json) | Out-Null
    Write-Output "[FAIL] zero quantity accepted"
} catch { Write-Output "[OK] Zero quantity rejected -> $($_.Exception.Response.StatusCode.value__)" }

# Movement history integrity
$mv = Invoke-RestMethod "$BaseUrl/api/inventory/movements?productId=$($prod.id)" -Headers $h
Write-Output "[OK] Movements recorded=$($mv.totalElements)"
$chainOk = $true
$rows = $mv.content | Sort-Object { $_.id }
for ($i = 1; $i -lt $rows.Count; $i++) {
    if ("$($rows[$i].previousStock)" -ne "$($rows[$i-1].newStock)") { $chainOk = $false }
}
if (-not $chainOk) { Write-Output "[FAIL] movement chain broken!" } else { Write-Output "[OK] Ledger chain consistent: " + (($rows | ForEach-Object { "$($_.movementType):$($_.previousStock)->$($_.newStock)" }) -join ' | ') }

$pAfter = Invoke-RestMethod "$BaseUrl/api/products/$($prod.id)" -Headers $h
if ("$($pAfter.currentStock)" -ne "$($rows[-1].newStock)") { Write-Output "[FAIL] product stock mismatch" } else { Write-Output "[OK] Product currentStock=$($pAfter.currentStock) matches ledger" }

Write-Output "P8 SMOKE COMPLETE product_id=$($prod.id)"
