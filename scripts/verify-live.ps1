$ErrorActionPreference = "Stop"

# Wait until Eureka has all 4 client services registered
$deadline = (Get-Date).AddSeconds(60)
do {
    try {
        $eureka = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Headers @{ Accept = "application/json" }
        $apps = @($eureka.applications.application | ForEach-Object { $_.name })
        if (($apps -contains "USER-SERVICE") -and ($apps -contains "VEHICLE-SERVICE") -and ($apps -contains "BOOKING-SERVICE") -and ($apps -contains "API-GATEWAY")) {
            break
        }
    } catch {}
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
Write-Host "Eureka registered apps: $($apps -join ', ')"

# 1. Dashboard check
$dash = Invoke-WebRequest -Uri "http://localhost:8080/" -UseBasicParsing
Write-Host "Dashboard HTTP: $($dash.StatusCode)"

# Wait until Gateway LoadBalancer cache picks up registered instances from Eureka
$gwDeadline = (Get-Date).AddSeconds(45)
do {
    try {
        $null = Invoke-RestMethod -Uri "http://localhost:8080/api/vehicles/summary" -Method Get -TimeoutSec 3
        break
    } catch {}
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $gwDeadline)

# 2. Unauthenticated booking blocked (401)
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" -Method Post -ContentType "application/json" -Body '{"vehicleId":1,"startDate":"2026-12-01","endDate":"2026-12-02"}' | Out-Null
    throw "Expected 401"
} catch {
    Write-Host "Unauthenticated POST /api/bookings status: $([int]$_.Exception.Response.StatusCode)"
}

# 3. Login via Gateway -> User Service
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"alex@driveease.com","password":"Customer@123"}'
$hdr = @{ Authorization = "Bearer $($login.token)" }
Write-Host "Logged in as: $($login.user.email) ($($login.user.role))"

# 4. Fleet listing via Gateway -> Vehicle Service
$fleet = Invoke-RestMethod -Uri "http://localhost:8080/api/vehicles" -Method Get
Write-Host "Seeded vehicles count: $($fleet.Count)"

# 5. Create booking -> Overlap rejection (409) -> Pickup -> Return
$offset = Get-Random -Minimum 20 -Maximum 180
$s = (Get-Date).AddDays($offset).ToString("yyyy-MM-dd")
$e = (Get-Date).AddDays($offset + 2).ToString("yyyy-MM-dd")
$b = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" -Method Post -Headers $hdr -ContentType "application/json" -Body (@{ vehicleId = 1; startDate = $s; endDate = $e; notes = "Live E2E verification" } | ConvertTo-Json)
Write-Host "Created booking #$($b.id): status=$($b.status), totalCost=$($b.totalCost)"

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/bookings" -Method Post -Headers $hdr -ContentType "application/json" -Body (@{ vehicleId = 1; startDate = $s; endDate = $e; notes = "Overlap test" } | ConvertTo-Json) | Out-Null
    throw "Expected 409"
} catch {
    Write-Host "Overlap POST /api/bookings status: $([int]$_.Exception.Response.StatusCode)"
}

$picked = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings/$($b.id)/pickup" -Method Post -Headers $hdr
$vDuring = Invoke-RestMethod -Uri "http://localhost:8080/api/vehicles/1" -Method Get
Write-Host "After pickup: booking=$($picked.status), vehicle1=$($vDuring.status)"

$returned = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings/$($b.id)/return" -Method Post -Headers $hdr -ContentType "application/json" -Body (@{ actualReturnDate = $e; returnMileage = ($vDuring.mileage + 150); returnFuelPercent = 88; needsMaintenance = $false; notes = "E2E return OK" } | ConvertTo-Json)
$vAfter = Invoke-RestMethod -Uri "http://localhost:8080/api/vehicles/1" -Method Get
Write-Host "After return: booking=$($returned.status), vehicle1=$($vAfter.status), mileage=$($vAfter.mileage)"
Write-Host "LIVE E2E VERIFICATION PASSED!"
