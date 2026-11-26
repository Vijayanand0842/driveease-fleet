param([switch]$KeepAlive)
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$JavaExe = "C:\Program Files\Java\jdk-24\bin\java.exe"
$LogDir = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Wait-ForHealth {
    param([string]$Url, [int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 3
            if ($resp.status -eq "UP") {
                return $true
            }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Url"
}

function Start-ServiceJar {
    param([string]$Name, [string]$JarPath)
    $outLog = Join-Path $LogDir "$Name.out.log"
    $errLog = Join-Path $LogDir "$Name.err.log"
    $proc = Start-Process -FilePath $JavaExe -ArgumentList "-jar", "`"$JarPath`"" `
        -WorkingDirectory $Root -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -WindowStyle Hidden -PassThru
    return @{ name = $Name; pid = $proc.Id }
}

$pids = @()

Write-Host "Starting eureka-server (8761)..."
$pids += Start-ServiceJar "eureka-server" (Join-Path $Root "eureka-server\target\eureka-server-1.0.0.jar")
Wait-ForHealth "http://localhost:8761/actuator/health" 90
Write-Host "eureka-server is UP."

Write-Host "Starting user-service (8081), vehicle-service (8082), booking-service (8083)..."
$pids += Start-ServiceJar "user-service" (Join-Path $Root "user-service\target\user-service-1.0.0.jar")
$pids += Start-ServiceJar "vehicle-service" (Join-Path $Root "vehicle-service\target\vehicle-service-1.0.0.jar")
$pids += Start-ServiceJar "booking-service" (Join-Path $Root "booking-service\target\booking-service-1.0.0.jar")

Wait-ForHealth "http://localhost:8081/actuator/health" 90
Wait-ForHealth "http://localhost:8082/actuator/health" 90
Wait-ForHealth "http://localhost:8083/actuator/health" 90
Write-Host "Core domain microservices are UP."

Write-Host "Starting api-gateway (8080)..."
$pids += Start-ServiceJar "api-gateway" (Join-Path $Root "api-gateway\target\api-gateway-1.0.0.jar")
Wait-ForHealth "http://localhost:8080/actuator/health" 90
Write-Host "api-gateway is UP."

$pids | ConvertTo-Json | Set-Content (Join-Path $Root ".driveease-pids.json")
Write-Host "All DriveEase services deployed locally!"
Write-Host "Dashboard & Gateway: http://localhost:8080"
Write-Host "Eureka Registry:     http://localhost:8761"

if ($KeepAlive) {
    Write-Host "Running in daemon keep-alive mode..."
    while ($true) {
        Start-Sleep -Seconds 10
    }
}
