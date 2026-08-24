$ErrorActionPreference = 'Stop'

$BaseUrl = "http://127.0.0.1:8475"
$root = Split-Path -Parent $PSScriptRoot

function Read-Err($ex) {
    try {
        $stream = $ex.Exception.Response.GetResponseStream()
        $reader = New-Object IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch { return "" }
}

$username = (Get-Content (Join-Path $root "config\initial-admin-credentials.txt") | Select-String "^Username:").Line.Substring(10).Trim()
$password = (Get-Content (Join-Path $root "config\initial-admin-credentials.txt") | Select-String "^Password:").Line.Substring(10).Trim()
$login = Invoke-RestMethod -Method Post "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail=$username; password=$password } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
"[OK] Login"

# 1) manual backup
$b1 = Invoke-RestMethod -Method Post "$BaseUrl/api/backups" -Headers $h -ContentType "application/json" `
    -Body '{"note":"smoke manual backup"}'
if ($b1.status -ne "SUCCESS" -or -not $b1.verified) { throw "backup not SUCCESS/verified" }
$manualDir = Join-Path $root "backups\manual\$($b1.filename)"
if (-not (Test-Path $manualDir)) { throw "zip missing: $manualDir" }
if ((Get-Item $manualDir).Length -lt 5000) { throw "zip suspiciously small" }
"[OK] Manual backup $($b1.filename) size=$($b1.sizeBytes)"

# zip contains db + metadata.json
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zf = [System.IO.Compression.ZipFile]::OpenRead($manualDir)
$names = $zf.Entries.FullName
$zf.Dispose()
if (-not ($names -contains "inventory.db") -or -not ($names -contains "metadata.json")) {
    throw "zip entries wrong: $($names -join ',')"
}
"[OK] Zip contains inventory.db + metadata.json"

# 2) list contains it
$list = Invoke-RestMethod -Method Get "$BaseUrl/api/backups" -Headers $h
if (-not ($list | Where-Object { $_.id -eq $b1.id })) { throw "list missing backup" }
"[OK] Backup list count=$($list.Count)"

# 3) verify endpoint
Invoke-RestMethod -Method Post "$BaseUrl/api/backups/$($b1.id)/verify" -Headers $h | Out-Null
"[OK] Verify endpoint OK"

# 4) download
$wc = New-Object System.Net.WebClient
$wc.Headers.Add("Authorization", $h.Authorization)
$dl = $wc.DownloadData("$BaseUrl/api/backups/$($b1.id)/file")
if ($dl.Length -lt 5000) { throw "download small: $($dl.Length)" }
"[OK] Download $($dl.Length) bytes"

# 5) export to folder
$expDir = Join-Path $root "tmp-backup-export"
$exp = Invoke-RestMethod -Method Post "$BaseUrl/api/backups/$($b1.id)/export" -Headers $h -ContentType "application/json" `
    -Body (@{ targetDir = $expDir } | ConvertTo-Json)
if (-not (Test-Path $exp.savedTo)) { throw "exported file missing" }
"[OK] Exported to $($exp.savedTo)"

# 6) import from that exported copy
$imp = Invoke-RestMethod -Method Post "$BaseUrl/api/backups/import" -Headers $h -ContentType "application/json" `
    -Body (@{ sourcePath = $exp.savedTo; note="smoke import" } | ConvertTo-Json)
if ($imp.backupType -ne "IMPORTED" -or -not $imp.verified) { throw "import wrong" }
"[OK] Imported as $($imp.filename)"

# 7) RESTORE round-trip proves the file actually swapped:
#    take snapshot BEFORE creating marker product
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$cat = Invoke-RestMethod -Method Post "$BaseUrl/api/categories" -Headers $h -ContentType "application/json" `
    -Body (@{ name="RB-CAT-$stamp"; description="" } | ConvertTo-Json)
$unitResp = Invoke-RestMethod -Method Get "$BaseUrl/api/units?size=50" -Headers $h
$unitId = ($unitResp.content | Select-Object -First 1).id
$snap = Invoke-RestMethod -Method Post "$BaseUrl/api/backups" -Headers $h -ContentType "application/json" `
    -Body '{"note":"pre-marker restore snapshot"}'
"[OK] Pre-marker snapshot $($snap.filename)"

# create marker product AFTER the snapshot
$marker = Invoke-RestMethod -Method Post "$BaseUrl/api/products" -Headers $h -ContentType "application/json" `
    -Body (@{ sku="RB-$stamp"; barcode=""; name="RestoreMarker $stamp"; categoryId=$cat.id; unitId=$unitId;
              minStock=0; maxStock=0; costPrice=1; sellingPrice=2; description="" } | ConvertTo-Json)
"[OK] Marker product created id=$($marker.id)"

# restore the pre-marker snapshot
Invoke-RestMethod -Method Post "$BaseUrl/api/backups/$($snap.id)/restore" -Headers $h -ContentType "application/json" -Body '{}' | Out-Null
"[OK] Restore executed"

# marker must be gone; category too (both created after snapshot)
try {
    Invoke-RestMethod -Method Get "$BaseUrl/api/products/$($marker.id)" -Headers $h | Out-Null
    throw "marker still exists after restore"
} catch {
    $errBody = Read-Err $_
    if ($errBody -notmatch "NOT_FOUND" -and $errBody -notmatch "404") { throw "expected 404 got: $errBody" }
}
"[OK] Marker gone after restore - database file was really swapped"

# safety backup should exist from restore flow
$list2 = Invoke-RestMethod -Method Get "$BaseUrl/api/backups" -Headers $h
if (-not ($list2 | Where-Object { $_.backupType -eq "SAFETY" })) { throw "no SAFETY backup created by restore" }
"[OK] Safety backup auto-created during restore"

# app still healthy on restored DB
$d = Invoke-RestMethod -Method Get "$BaseUrl/api/dashboard" -Headers $h
"[OK] Dashboard still works post-restore (products=$($d.totalProducts))"

# 8) delete imported record
Invoke-RestMethod -Method Delete "$BaseUrl/api/backups/$($imp.id)" -Headers $h | Out-Null
$list3 = Invoke-RestMethod -Method Get "$BaseUrl/api/backups" -Headers $h
if ($list3 | Where-Object { $_.id -eq $imp.id }) { throw "delete failed" }
if (Test-Path (Join-Path $root "backups\manual\$($imp.filename)")) { throw "file not deleted" }
"[OK] Backup delete removes row + file"

"P14 SMOKE COMPLETE"
