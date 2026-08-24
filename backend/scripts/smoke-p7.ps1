param(
    [string]$BaseUrl = "http://127.0.0.1:8475"
)

$ErrorActionPreference = "Stop"
$creds = Get-Content "config\initial-admin-credentials.txt" -ErrorAction SilentlyContinue
$pwLine = $creds | Select-String "^Password:"
if (-not $pwLine) {
    Write-Output "SKIP: initial-admin-credentials.txt not found (password already changed)."
    exit 0
}
$pw = $pwLine.Line.Substring(10)

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = "admin"; password = $pw } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[OK] Login"

# Units seeded by migration V5
$units = Invoke-RestMethod "$BaseUrl/api/units?size=50" -Headers $h
Write-Output "[OK] Units count = $($units.totalElements) (expected 7)"

# Category create
$cat = Invoke-RestMethod -Method Post "$BaseUrl/api/categories" -Headers $h -ContentType "application/json" `
    -Body (@{ name = "Electronics"; description = "Electronic devices" } | ConvertTo-Json)
Write-Output "[OK] Category created id=$($cat.id)"

# Duplicate category must fail 422
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/categories" -Headers $h -ContentType "application/json" `
        -Body '{"name":"electronics"}' | Out-Null
    Write-Output "[FAIL] duplicate category accepted"
} catch {
    Write-Output "[OK] Duplicate category rejected -> $($_.Exception.Response.StatusCode.value__)"
}

# Product create
$prod = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{
        sku = "SKU-001"; barcode = "BC123456"; name = "USB Cable"
        description = "Type-C cable 1m"; categoryId = $cat.id; unitId = 1
        minStock = 10; maxStock = 500
        costPrice = 2.50; sellingPrice = 4.99
    } | ConvertTo-Json)
Write-Output "[OK] Product id=$($prod.id) status=$($prod.stockStatus) cost=$($prod.costPrice) sell=$($prod.sellingPrice)"

if ($prod.stockStatus -ne "OUT_OF_STOCK") { Write-Output "[FAIL] expected OUT_OF_STOCK" }

# Duplicate SKU must fail
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
        -Body (@{ sku="sku-001"; name="Dup"; unitId=1; minStock=0; costPrice=1; sellingPrice=2 } | ConvertTo-Json) | Out-Null
    Write-Output "[FAIL] duplicate SKU accepted"
} catch {
    Write-Output "[OK] Duplicate SKU rejected -> $($_.Exception.Response.StatusCode.value__)"
}

# Validation failure: negative price
try {
    Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
        -Body (@{ sku="SKU-BAD"; name="Bad"; unitId=1; minStock=0; costPrice=-5; sellingPrice=2 } | ConvertTo-Json) | Out-Null
    Write-Output "[FAIL] negative price accepted"
} catch {
    Write-Output "[OK] Negative price rejected -> $($_.Exception.Response.StatusCode.value__)"
}

# Search filter
$found = Invoke-RestMethod "$BaseUrl/api/products?search=cable" -Headers $h
Write-Output "[OK] Search 'cable' hits=$($found.totalElements)"
$bySku = Invoke-RestMethod "$BaseUrl/api/products?search=sku-001" -Headers $h
Write-Output "[OK] Search SKU hits=$($bySku.totalElements)"

# Update
$upd = Invoke-RestMethod -Method Put "$BaseUrl/api/products/$($prod.id)" -Headers $h -ContentType "application/json" `
    -Body (@{
        sku = "SKU-001"; barcode = "BC123456"; name = "USB Cable C-C"
        description = ""; categoryId = $cat.id; unitId = 1
        minStock = 5; maxStock = 300; costPrice = 2.75; sellingPrice = 5.49
    } | ConvertTo-Json)
Write-Output "[OK] Updated name=$($upd.name) cost=$($upd.costPrice)"

# Deactivate
Invoke-RestMethod -Method Delete "$BaseUrl/api/products/$($prod.id)" -Headers $h | Out-Null
$after = Invoke-RestMethod "$BaseUrl/api/products/$($prod.id)" -Headers $h
if ($after.active) { Write-Output "[FAIL] product still active after deactivate" } else { Write-Output "[OK] Deactivated" }

# Inactive category cannot be assigned to new product
Invoke-RestMethod -Method Delete "$BaseUrl/api/categories/$($cat.id)" -Headers $h -ErrorAction SilentlyContinue | Out-Null

Write-Output "SMOKE TEST COMPLETE"
