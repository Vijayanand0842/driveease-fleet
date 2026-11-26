# DriveEase Fleet Lifecycle & Rental Asset Management Platform (PS005)

Spring Boot 3.5 & Spring Cloud 2025 microservices platform for commercial fleet telematics, real-time vehicle availability, and reservation lifecycle management.

## Architecture

| Service | Port | Responsibility | Database |
| :--- | :--- | :--- | :--- |
| `eureka-server` | `8761` | Netflix Eureka service registry | - |
| `api-gateway` | `8080` | Spring Cloud Gateway routing, JWT edge validation, Web Dashboard | - |
| `user-service` | `8081` | Registration, BCrypt auth, JWT issuance, RBAC profiles | `driveease_user_db` |
| `vehicle-service` | `8082` | Fleet catalog, telematics status, maintenance logs | `driveease_vehicle_db` |
| `booking-service` | `8083` | Overlap-free bookings, pickup/return lifecycle, Feign sync | `driveease_booking_db` |

## Demo Accounts
- **Admin:** `admin@driveease.com` / `Admin@123`
- **Fleet Manager:** `manager@driveease.com` / `Manager@123`
- **Customer:** `alex@driveease.com` / `Customer@123`

## Local Deployment
```powershell
mvn clean package
.\scripts\start-local.ps1
```
Open `http://localhost:8080` for the DriveEase Dashboard and `http://localhost:8761` for Eureka.

## Docker Compose Deployment
```bash
docker compose up --build -d
```
