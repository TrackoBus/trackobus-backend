# 🚌 TrackoBus - Backend

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-blue.svg)](https://postgis.net/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![OSRM](https://img.shields.io/badge/OSRM-Routing%20Engine-black.svg)](http://project-osrm.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)

This is the backend of the Trackobus application. It enables commuters to track public transit in real time with sub-second latency, access ETA and distance information, crowdsourced location broadcasting, anti-spoofing validation, and an automated backup rider failover system.

---

## 📑 Table of Contents

- [Technology Stack](#-technology-stack)
- [Core Features & Functionalities](#-core-features--functionalities)
  - [1. Real-Time Bus Tracking & Live ETA](#1-real-time-bus-tracking--live-eta)
  - [2. Location Sharing & Anti-Spoofing Pipeline](#2-location-sharing--anti-spoofing-pipeline)
  - [3. The Failsafe: Backup Rider System](#3-the-failsafe-backup-rider-system)
  - [4. Gamification & Points Engine](#4-gamification--points-engine)
- [Prerequisites](#-prerequisites)
- [Environment Configuration](#-environment-configuration)
- [Setup & Running Guide](#-setup--running-guide)
  - [Step 1: Configure Firebase Admin SDK](#step-1-configure-firebase-admin-sdk)
  - [Step 2: Prepare OSRM Routing Data](#step-2-prepare-osrm-routing-data)
  - [Step 3: Run with Docker Compose (Recommended)](#step-3-run-with-docker-compose-recommended)
  - [Step 4: Run in Local Development Mode (Hybrid)](#step-4-run-in-local-development-mode-hybrid)
- [Cloud Hosting & Production Deployment](#-cloud-hosting--production-deployment)
- [API & WebSocket Specification](#-api--websocket-specification)
  - [REST Endpoints](#rest-endpoints)
  - [WebSocket STOMP Protocol](#websocket-stomp-protocol)
- [Project Directory Structure](#-project-directory-structure)

---

## 🛠 Technology Stack

| Technology | Role |
| :--- | :--- |
| **Java 25 & Spring Boot 4.x** | Core backend framework handling business logic, STOMP WebSockets, security filters, and data orchestration. |
| **PostgreSQL 16 + PostGIS** | Spatial database storing user profiles, route polylines, and executing high-precision spatial queries (`ST_DWithin`). |
| **Redis 7** | High-speed in-memory store for transient location data, Pub/Sub live distribution, Geospatial index searches, and FIFO backup queues (`ZSET`). |
| **OSRM (Open Source Routing Machine)** | High-performance routing engine for road-network ETA calculation, and total route distance computation. |
| **Firebase Admin SDK** | Secure token-based user authentication across both REST APIs and WebSocket handshake/connections. |
| **Google Maps API** | Route data fetching, coordinate ingestion, and automated route polyline seeding into PostGIS. (Routes are fetched only once when the server starts for the first time, and the route data is then stored in the PostgreSQL database.) |
| **Docker & Docker Compose** | Multi-container orchestration bundling Spring Boot, PostgreSQL/PostGIS, Redis, and OSRM into an isolated network. |

---

## 🚀 Core Features & Functionalities

### 1. Real-Time Bus Tracking & Live ETA
- **Route Discovery & Auto-Subscription**: When a commuter selects a bus route, the frontend fetches route geometry and metadata from PostgreSQL and automatically subscribes to the route's live WebSocket channel (`/topic/routes/{routeNumber}`).
- **Instant Map Population**: Instead of waiting for the next GPS broadcast, the backend immediately queries Redis active bus hashes (`active-buses:{routeNumber}`) to populate the commuter's map instantly.
- **Road-Accurate Live ETA**: As the commuter tracks the bus, the backend issues requests to the localized OSRM routing engine using road coordinates to compute accurate distance and ETA metrics.
- **Ghost Bus Prevention**: Active bus state in Redis expires automatically (70 seconds TTL) if location broadcasts cease, removing stale buses from the map.

### 2. Location Sharing & Anti-Spoofing Pipeline
- **Session Initiation**: Passengers broadcasting location generate a session with a unique `BusID`, initial coordinates, and a verified Firebase token.
- **Initial Spatial Validation**: The backend executes a PostGIS `ST_DWithin` spatial query to confirm the user is within **50 meters** of the official route polyline before allowing broadcasting.
- **Continuous Validation & Strike System**:
  - **Proximity**: Periodically verifies the broadcaster remains within 50m of the designated route.
  - **Movement Verification**: Verifies the broadcaster has traveled at least **2 km** from the starting point to eliminate stationary spoofing.
  - **3-Strike Policy**: If a user stops moving for too long or drifts off-route, strikes are issued. Reaching 3 strikes results in immediate eviction to preserve map integrity.
- **Community Reports**: Trackers can submit fake bus reports. Reaching 3 community reports triggers immediate bus termination and cleanup.

### 3. The Failsafe: Backup Rider System
- **Proximity Detection & Opt-In**: When an actively tracked bus moves within **100 meters** of a waiting commuter, the app prompts them to opt in as a **Backup Rider**.
- **Priority Queue Management**: Accepted backup riders are rewarded with gamification points and queued into a Redis Sorted Set (`ZSET`) indexed by timestamp (FIFO queue).
- **Seamless Automated Failover**: If the primary broadcaster drops off (due to departure, battery drain, signal loss, or strike eviction), the `WebSocketEventListener` catches the disconnect, immediately pops the top backup rider from Redis, and broadcasts a promotion event (`/queue/promotion`) to make them the new primary broadcaster with zero interruption for other commuters.

### 4. Gamification & Points Engine
- **Contribution Rewards**: Users earn reward points based on total road distance traveled (calculated via OSRM) and community upvotes/likes received during the broadcast session.
- **Failsafe Point Persistence**: If a session ends abruptly, the backend disconnect listener automatically executes final distance point calculations and persists points to the user's profile in PostgreSQL.

---

## 📦 Prerequisites

Before setting up TrackoBus, ensure you have the following installed on your machine:

- **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** (or Docker Engine + Docker Compose)
- **Git**
- **cURL** and **Bash** (for OSRM data processing script)
- **Java 25 JDK** & **Maven** *(Optional: only needed if running the Spring Boot app natively outside Docker)*
- **Firebase Account**: A Firebase project with Authentication enabled.
- **Google Maps API Key**: Directions API enabled for database route seeding.

---

## ⚙️ Environment Configuration

Create a `.env` file in the root of the repository by copying the provided `.env.example`:

```bash
cp .env.example .env
```

Configure your environment variables in `.env`:

```env
# Database Credentials
DB_NAME=trackobus
DB_USERNAME=trackobusadmin
DB_PASSWORD=your_secure_password_here

# API Credentials
GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
```

### Additional Configurable Variables

| Variable | Default (Docker) | Default (Local / Cloud) | Description |
| :--- | :--- | :--- | :--- |
| `DB_HOST` | `db` | `localhost` or Cloud DB host | PostgreSQL server host |
| `DB_SSL_MODE` | `?sslmode=disable` | `?sslmode=require` | SSL mode for database connection |
| `REDIS_HOST` | `redis` | `localhost` or Cloud Redis host | Redis server host |
| `REDIS_PORT` | `6379` | `6379` | Redis port |
| `REDIS_PASSWORD` | `""` | `your_redis_password` | Redis authentication password |
| `REDIS_SSL` | `false` | `true` (for cloud like Azure Cache) | Enable Redis SSL |
| `OSRM_SERVER_URL` | `http://osrm:5000` | `http://localhost:5000` | OSRM routing engine base URL |

---

## 🚀 Setup & Running Guide

### Step 1: Configure Firebase Admin SDK

The backend uses Firebase for authentication and token validation.

1. Go to the [Firebase Console](https://console.firebase.google.com/) -> **Project Settings** -> **Service Accounts**.
2. Click **Generate new private key** to download your JSON credentials.
3. Rename the file to `firebase-service-account.json` and place it inside:
   ```
   src/main/resources/firebase-service-account.json
   ```

---

### Step 2: Prepare OSRM Routing Data

The OSRM engine requires pre-processed OpenStreetMap (OSM) routing files for road-snapped calculations. A helper script [`setup-osrm.sh`](file:///setup-osrm.sh) is provided to automate downloading Sri Lanka map data and running the OSRM extraction and contraction pipeline.

#### On Linux / macOS / Git Bash / WSL:

Make the script executable and run:
```bash
chmod +x setup-osrm.sh
./setup-osrm.sh
```

#### On Windows (PowerShell / Command Prompt):

If you cannot run bash scripts, execute the following commands in order:

```powershell
# 1. Create data directory
mkdir osrm-data

# 2. Download Sri Lanka OpenStreetMap data
curl.exe -L http://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf -o osrm-data/sri-lanka-latest.osm.pbf

# 3. Extract road network graph with car profile
docker run --rm -v "${PWD}/osrm-data:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/sri-lanka-latest.osm.pbf

# 4. Partition the data
docker run --rm -v "${PWD}/osrm-data:/data" osrm/osrm-backend osrm-partition /data/sri-lanka-latest.osrm

# 5. Customize the routing graph
docker run --rm -v "${PWD}/osrm-data:/data" osrm/osrm-backend osrm-customize /data/sri-lanka-latest.osrm
```

> **Note**: This creates the processed map files in `./osrm-data/` which are mounted by the OSRM container.

---

### Step 3: Run with Docker Compose (Recommended)

To build and run the entire backend stack (PostgreSQL + PostGIS, Redis, OSRM, and Spring Boot) with a single command:

```bash
docker compose up --build -d
```

#### Check Service Status

```bash
docker compose ps
```

All 4 services should be healthy and running:
- `trackobus-db` -> `localhost:5432`
- `trackobus-redis` -> `localhost:6379`
- `trackobus-osrm` -> `localhost:5000`
- `trackobus-backend` -> `localhost:8080` (Base path: `http://localhost:8080/trck`)

#### View Logs

```bash
# Follow logs for the Spring Boot application
docker compose logs -f app

# Follow logs for all services
docker compose logs -f
```

#### Stop All Services

```bash
docker compose down
```

---

### Step 4: Run in Local Development Mode (Hybrid)

If you prefer to develop and debug the Spring Boot backend inside your IDE (VS Code, IntelliJ IDEA, Eclipse) or via Maven CLI:

1. **Start backing services using Docker**:
   ```bash
   docker compose up -d db redis osrm
   ```

2. **Run Spring Boot application locally**:
   - **Linux / macOS**:
     ```bash
     ./mvnw spring-boot:run
     ```
   - **Windows**:
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```

---

## ☁️ Cloud Hosting & Production Deployment

The backend is cloud-ready and can be deployed to providers such as **Microsoft Azure**, **AWS**, **GCP**, or **DigitalOcean**.

### Cloud Architecture Recommendations

1. **Database**:
   - Use a managed PostgreSQL instance (e.g., Azure Database for PostgreSQL Flexible Server, AWS RDS).
   - Ensure the **PostGIS** extension is activated:
     ```sql
     CREATE EXTENSION IF NOT EXISTS postgis;
     ```
   - In production, set `DB_SSL_MODE=?sslmode=require`.

2. **Redis**:
   - Use managed Redis (e.g., Azure Cache for Redis, AWS ElastiCache, Upstash).
   - Enable SSL (`REDIS_SSL=true`) and provide `REDIS_PASSWORD`.

3. **OSRM Routing Engine**:
   - Deploy `osrm/osrm-backend` on a container service (Azure Container Apps, AWS ECS, or VM) mounting the generated `osrm-data` folder.
   - Set `OSRM_SERVER_URL` on the Spring Boot backend to point to your cloud OSRM instance.

4. **Spring Boot Backend**:
   - Build and publish the Docker container using the included multi-stage [`Dockerfile`](file:///Dockerfile).
   - Deploy as a container app / Kubernetes pod / App Service.
   - Pass production secrets via cloud environment variables or Key Vault.

---

## 🔌 API & WebSocket Specification

### Application Context Path
All REST and WebSocket endpoints are rooted at:
```
http://<host>:8080/trck
```

### REST Endpoints

#### Authentication (`/api/auth`)
- `POST /api/auth/register`: Register user profile with Firebase token verification.
- `POST /api/auth/login`: Authenticate and fetch user profile.

#### Route Discovery (`/api/routes`)
- `GET /api/routes`: List all available bus routes.
- `GET /api/routes/{routeNumber}`: Fetch route details and polyline coordinates.
- `GET /api/routes/proxCheck?routeNumber=..&longitude=..&latitude=..`: PostGIS proximity check (within 50m).
- `GET /api/routes/{routeNumber}/closest?lat=..&lng=..`: Find the nearest active bus using Redis GeoSearch (20km radius).

#### Tracking & Validation (`/api/live-tracking` & `/api/tracking`)
- `POST /api/tracking/start-trip`: Generates a unique `BusID` for a new broadcasting session.
- `GET /api/live-tracking/routes/{routeNumber}`: Fetch current active bus locations on a route from Redis cache.
- `GET /api/live-tracking/routes/{routeNumber}/buses/{busId}/eta?lat=..&lng=..`: Query road-based ETA and distance from OSRM.
- `POST /api/live-tracking/buses/{busId}/validate`: Perform continuous anti-spoofing validation checks.
- `POST /api/live-tracking/buses/{busId}/report?routeNumber=..`: Submit fake bus reports (3 reports trigger bus termination).
- `POST /api/live-tracking/buses/{busId}/backup?isOptingIn=..`: Opt in or out of the Backup Rider queue.
- `POST /api/live-tracking/buses/{busId}/like`: Like a bus broadcast and notify listeners live.
- `POST /api/live-tracking/buses/{busId}/points/calculate`: Calculate distance & upvote gamification points for a completed trip.
- `POST /api/live-tracking/buses/backup/addPoints`: Award bonus points to backup riders.

### WebSocket STOMP Protocol

- **Endpoint**: `/trck/ws-live-tracking` (Supports SockJS fallback)
- **Authentication**: STOMP `CONNECT` header with `Authorization: Bearer <FIREBASE_ID_TOKEN>`

| Destination | Type | Description |
| :--- | :--- | :--- |
| `/app/ping` | Send | Location ping sent by primary broadcasters (`LocationPingDto`). |
| `/topic/routes/{routeNumber}` | Subscribe | Live bus location updates broadcasted for a given route. |
| `/topic/buses/{busId}/likes` | Subscribe | Live upvote and like counter updates for a specific bus. |
| `/user/queue/promotion` | Subscribe | Direct alert to promote a Backup Rider to Primary Broadcaster upon disconnect. |

---

## 📁 Project Directory Structure

```
trackobus-backend/
├── .env.example              # Example environment variable template
├── Dockerfile                # Multi-stage Docker build file (JDK 25 build -> JRE 25 runtime)
├── docker-compose.yml        # Docker Compose configuration for full stack
├── pom.xml                   # Maven dependencies and build configuration
├── setup-osrm.sh             # Bash script to fetch and process OSRM routing data
├── src/
│   └── main/
│       ├── java/Group16/TrackoBus/backend/
│       │   ├── TrackoBusApplication.java   # Spring Boot Application Entry Point
│       │   ├── config/                     # Security, Redis, Firebase, WebSockets, Seeder
│       │   ├── controller/                 # REST & WebSocket Controllers
│       │   ├── dto/                        # Data Transfer Objects & Requests/Responses
│       │   ├── entity/                     # JPA Entities (Users, Routes, etc.)
│       │   ├── repository/                 # Spring Data JPA & Spatial Repositories
│       │   ├── security/                   # Firebase Token Authentication Filter
│       │   ├── service/                    # Business Logic, OSRM, Tracking, Points, Events
│       │   └── utils/                      # Helper utilities
│       └── resources/
│           ├── application.yaml            # Application configuration & datasource settings
│           └── firebase-service-account.json # Firebase Admin credentials (User provided)
└── target/                                 # Maven build output
```
