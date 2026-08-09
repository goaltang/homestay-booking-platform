# Homestay Booking Platform

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.2-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)
![Vite](https://img.shields.io/badge/Vite-5%2F6-646CFF.svg)

English | **[中文](README.md)**

A full-featured homestay booking platform connecting guests, hosts, and platform administrators. It covers the complete lifecycle: property listing, platform review, online search, booking & payment, check-in / check-out, reviews, earnings analytics, and back-office governance.

> 25 business modules · 100K+ lines of code · 223 commits · 16 months of continuous iteration · 50+ project docs

## Background

Homestay is a **solo-delivered** full-stack project spanning 25 business modules and 100K+ lines of code, with a Spring Boot 3 backend, Vue 3 frontends, and full integrations with MySQL / Redis / Elasticsearch / Alipay.

Development started in February 2025 and continues to this day. I use AI coding tools (Cursor / Claude Code / Kimi Code CLI, etc.) for ~90% of the coding, while I handle **architecture design, product decisions, code review, critical module implementation, and quality control**.

This workflow lets a single engineer deliver all three frontends (guest / host / admin) end-to-end — a real snapshot of what full-stack engineering looks like in the AI era of 2026.

> Toolchain evolution: Cursor Pro → Claude Code CLI → Kimi Code CLI

## Table of Contents

- [Highlights](#highlights)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [User Roles](#user-roles)
- [Feature Overview](#feature-overview)
- [Core Business Flows](#core-business-flows)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Testing & Building](#testing--building)
- [Documentation Index](#documentation-index)
- [Security Notes](#security-notes)
- [License](#license)

## Highlights

- **Three frontends, one backend**: Guest, host, and admin apps share a unified API layer covering consumption, operations, and platform governance.
- **Elasticsearch property search**: Full-text search, faceted filtering, geo-coordinate indexing, similar-property recommendations, and personalized ranking.
- **Personalized recommendation engine**: Builds user profiles from order history, favorites, and browsing behavior. Four strategies — trending, personalized, location-based, and similar listings — with three-tier graceful degradation and result diversification.
- **Dynamic pricing engine**: Weekend surcharges, holiday adjustments, multi-night discounts, early-booking discounts. Scope covers global / city / host / property-group / individual listing. Supports make-up workday detection and order price snapshot locking.
- **User behavior tracking & profiling**: Async event collection for search, browse, click, favorite, booking, and share events. Scheduled profile aggregation (price / location / room-type / amenity preferences) feeds back into the recommendation engine.
- **Coupon marketing system**: Template management, user claiming (pessimistic locking to prevent over-claiming), order redemption, expiry cleanup, batch issuance, and ROI analytics.
- **Automated order state machine**: Scheduled tasks for auto check-in, auto check-out, and timeout cancellation (three-tier: pending confirmation / pending payment / payment in progress). Supports bulk historical order status repair.
- **Intelligent property feature analysis**: Auto-generates property feature tags across 8 dimensions — type, price competitiveness, amenity mix, location advantage, booking activity, weekend popularity, guest reviews, and check-in convenience. Dynamically boosts matching tag priority based on search criteria.
- **Redis distributed locking**: Atomic unlock via Lua scripts prevents accidental deletion of other threads' locks. Auto-degrades to lock-free mode when Redis is unavailable.
- **Price competitiveness analysis**: Multi-tier comparison (same area / same city / same type) with seasonal factors, outputting competitiveness grades and pricing suggestions.
- **Map-based search**: AMap (Gaode) integration for location search, nearby discovery, distance calculation, and map display.
- **Payment integration**: Alipay sandbox payments (page redirect + QR code), with order payment, async callbacks, status queries, and refunds.
- **Production-grade backend**: Unified responses, global exception handling, permission annotations, DTO mapping, caching, database migrations, and audit logging.

### Message Architecture (RabbitMQ - Three Scenarios)

```mermaid
flowchart LR
    subgraph S1["① Order Timeout · DLX Delayed Queue"]
        direction LR
        O1[Order created] --> O2[Delayed queue<br/>TTL 2h] --> O3[DLX dead-letter<br/>→ consumer queue] --> O4[Idempotent check<br/>→ auto cancel]
    end
    subgraph S2["② Batch Coupon Issuance · Event-driven + Retry Queue"]
        direction LR
        C1[Task created] --> C2[Main queue] --> C3[Consumer issues coupons<br/>item by item] --> C4[Failures → retry queue<br/>auto-retry after 60s, max 3]
    end
    subgraph S3["③ Notification Push · Reliable Delivery"]
        direction LR
        N1[Transaction commit] --> N2[Main queue] --> N3[WebSocket<br/>real-time push] --> N4[No loss on crash<br/>re-push after restart]
    end
```

> Common pattern across all three: main queue + retry/delayed queue (TTL dead-letter back to main), manual consumer ack + idempotency check, `mq-enabled` toggle for graceful degradation, scheduled tasks as fallback. Full diagrams: `obsidian-vault/03-后端/后端-RabbitMQ 消息架构.md`.

## Tech Stack

| Layer | Technologies |
|---|---|
| Guest Frontend | Vue 3, TypeScript, Vite, Vue Router, Pinia, Element Plus, Axios, ECharts, AMap, SockJS, STOMP |
| Admin Frontend | Vue 3, TypeScript, Vite, Vue Router, Pinia, Element Plus, Axios, ECharts |
| Backend | Java 17, Spring Boot 3.0.2, Spring Web, Spring Security, Spring Data JPA, Spring Validation |
| Data & Cache | MySQL 8.0, Redis, Redisson, Flyway, Elasticsearch |
| Communication & Integration | JWT, WebSocket (STOMP), Alipay SDK, SMTP Email |
| Build Tools | Maven, npm, MapStruct, Lombok, Docker Compose |

## Architecture

```mermaid
graph TB
    subgraph C["Clients"]
        F1[Guest App<br/>Vue 3 + TS]
        F2[Host App<br/>Vue 3 + TS]
        F3[Admin App<br/>Vue 3 + TS]
    end

    subgraph B["Backend · Spring Boot 3 + Java 17"]
        S1[Auth<br/>Security + JWT]
        S2[Core Business<br/>Listings/Orders/Payments]
        S3[Pricing Engine<br/>Dynamic Rates]
        S4[ES Search<br/>+ Recommendations]
        S5[Marketing<br/>Coupons/ROI]
        S6[Notifications<br/>WebSocket]
    end

    subgraph D["Data & Cache"]
        D1[(MySQL 8<br/>Flyway)]
        D2[(Redis<br/>Locks + Cache)]
        D3[(Elasticsearch<br/>IK + Geo)]
    end

    subgraph E["External Services"]
        E1[Alipay]
        E2[AMap]
        E3[SMTP Email]
    end

    F1 --> S1
    F2 --> S1
    F3 --> S1
    F1 --> S2
    F2 --> S2
    F3 --> S2
    F1 --> S3
    F2 --> S3
    F1 --> S4
    F1 --> S5
    F2 --> S5
    F1 --> S6
    F2 --> S6

    S2 --> D1
    S3 --> D1
    S4 --> D3
    S4 --> D2
    S5 --> D1
    S6 --> D2
    S2 --> D2
    S2 --> E1
    S2 --> E2
    S1 --> E3
```

## User Roles

| Role | Description |
|---|---|
| Guest (anonymous) | Browse homepage, search listings, view public property details |
| Guest (registered) | Favorite listings, place orders, pay, request refunds, write reviews, send messages |
| Host | Register as host, publish listings, manage orders, handle check-in/check-out, view earnings |
| Administrator | Review listings, manage users and orders, handle reports and disputes, view analytics, configure platform rules |

## Feature Overview

### Guest App

| Module | Capabilities |
|---|---|
| Authentication | Registration, login, JWT auth, password reset, profile management |
| Homepage Recommendations | Trending listings, personalized recommendations, location-based suggestions |
| Property Search | Elasticsearch keyword search, faceted filters, sorting, pagination, URL param persistence, similar listings |
| Map Search | AMap display, nearby search, coordinate positioning, distance calculation |
| Property Details | Photos, amenities, smart feature tags, pricing, location, host info, review list |
| Online Booking | Date selection, real-time dynamic pricing, order preview, inventory validation |
| Online Payment | Alipay page redirect or QR code payment, payment status query, success redirect |
| Order Management | Order list, order details, cancellation, refund requests, status tracking |
| Favorites & Reviews | Add/remove favorites, post-order reviews, view my reviews |
| Notifications | Guest-host instant messaging, unread badges, system notifications (WebSocket real-time push) |

### Host App

| Module | Capabilities |
|---|---|
| Host Onboarding | Registration form, identity verification, host profile management |
| Host Dashboard | Listings, orders, revenue, reviews, and recent orders overview |
| Listing Management | Create, edit, draft save, submit for review, list/delist, delete, group management |
| Listing Publishing | Step-by-step input: basic info, location, amenities, description, photos |
| Order Processing | View orders, confirm/reject orders, refund review, dispute handling |
| Check-in / Check-out | Generate check-in credentials, self-service codes, process check-in, checkout settlement, deposits and extra charges |
| Earnings Management | Total earnings, monthly earnings, unsettled balance, daily/monthly trend charts, data export |
| Review Management | Rating distribution, review list, host replies, unreplied reminders |
| Notifications | Conversation list, chat history, order and review notifications |

### Admin App

| Module | Capabilities |
|---|---|
| Review Workbench | Pending listings, batch review, review history, review statistics |
| Listing Governance | Listing management, forced delisting, violation records, property type and amenity management |
| User Management | User list, enable/disable, identity verification review |
| Order Management | Multi-condition filtering, refund approval, dispute resolution |
| Violation Management | Report list, process/dismiss reports, violation scanning, duplicate report stats |
| Analytics | Orders, revenue, users, listings overview and trend analysis |
| Pricing Rules | Global / city / host / property multi-level pricing rule configuration with priority management |
| Coupon Management | Template creation, batch issuance, usage statistics, ROI analysis |
| System Config | Platform settings, policy configuration, fee configuration |
| Announcements | Publish system notifications and event announcements |
| Audit Logs | Admin operation logs, login logs |

### Backend Capabilities

| Capability | Description |
|---|---|
| Unified Response | `ApiResponse<T>` wrapping `success`, `code`, `message`, `data`, and `timestamp` |
| Auth & Authorization | Spring Security + JWT stateless authentication with role-based access control |
| Data Access | Spring Data JPA with Repository and Specification for complex queries |
| Database Migration | Flyway-managed schema evolution (V1 – V49, 41 scripts) |
| Caching | Redis for hot data and recommendation caching; Spring Cache with Caffeine |
| Distributed Locking | Redis + Lua script atomic unlock with fault-tolerant degradation |
| Real-time Communication | WebSocket (STOMP) for chat messages and notification push |
| Search Service | Elasticsearch-based property search with incremental sync and full rebuild |
| Recommendation Service | Multi-strategy engine + user profiling + behavior tracking, with caching and degradation |
| Pricing Engine | Multi-dimensional dynamic pricing rules with date-level and order-level adjustments, priority and stacking control |
| Payment Integration | Alipay sandbox: page redirect, QR code, async notifications, order queries, refunds |
| Exception Handling | `@RestControllerAdvice` for unified business and system exception handling |
| Object Mapping | MapStruct for Entity / DTO / Request / Response conversion |
| Audit Logging | Async recording of admin operations, login events, and key business state changes |
| Scheduled Tasks | Auto order state transitions, timeout handling, coupon cleanup, user profile aggregation |

## Core Business Flows

### Listing Publication & Review

```text
Host creates listing draft
  -> Add location, amenities, description, photos
  -> Submit for review
  -> Admin reviews
  -> Approved: goes live / Rejected: returned for edits
```

### Order Lifecycle

```text
Guest places order
  -> Pending Payment
  -> Paid
  -> Host Confirms
  -> Ready for Check-in
  -> Checked In (auto via scheduled task)
  -> Checked Out (auto via scheduled task)
  -> Completed
  -> Guest Reviews
```

### Refunds & Disputes

```text
Guest requests refund
  -> Host reviews
  -> Approved -> Refund in progress -> Refund completed
  -> Rejected -> Guest files dispute -> Admin intervenes
```

### Earnings Settlement

```text
Order completed
  -> Host earnings generated
  -> Added to settleable balance
  -> Host views earnings stats and trends
```

## Project Structure

```text
homestay3/
├── homestay-front/          # Guest + Host app, Vue 3 + Vite
├── homestay-admin/          # Admin app, Vue 3 + Vite
├── homestay-backend/        # Backend API, Spring Boot
├── docs/                    # Project documentation
│   └── INSTALL.md           # Installation guide (with AI Agent instructions)
├── tools/                   # Local utility scripts
├── docker-compose.yml       # Docker Compose config (Elasticsearch)
├── README.md                # Project overview (Chinese)
├── README_EN.md             # Project overview (English)
└── .gitignore               # Git ignore rules
```

Backend layering:

```text
com.homestay3.homestaybackend
├── config/                  # Security, cache, CORS, WebSocket, payment configs
├── controller/              # REST API controllers
├── service/                 # Core business logic
│   ├── search/              # Search & recommendation services
│   └── gateway/             # Payment gateway
├── repository/              # JPA data access layer
├── entity/                  # Database entities
├── dto/                     # Data transfer objects
├── mapper/                  # MapStruct mappings
├── model/                   # Enums and constants
├── exception/               # Global exception handling
├── security/                # JWT & auth
├── util/                    # Utility classes
└── job/                     # Scheduled tasks
```

## Quick Start

> For the full installation guide (including AI Agent automated setup instructions), see [docs/INSTALL.md](docs/INSTALL.md).

### Prerequisites

| Dependency | Recommended Version | Required |
|---|---|---|
| JDK | 17+ | ✅ |
| Maven | 3.6+ | ✅ |
| MySQL | 8.0+ | ✅ |
| Redis | 6.0+ | ✅ |
| Elasticsearch | 8.5+ | ❌ (optional, needed for search) |
| Node.js | 18+ | ✅ |
| npm | 9+ | ✅ |
| Docker + Docker Compose | Latest stable | ❌ (only for Elasticsearch) |

### 1. Clone the Repository

```bash
git clone https://github.com/goaltang/homestay3.git
cd homestay3
```

### 2. Start Infrastructure

**MySQL** — Create the database:

```bash
mysql -u root -p -e "CREATE DATABASE homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**Redis** — Ensure Redis is running on `localhost:6379`:

```bash
redis-server --daemonize yes
```

**Elasticsearch (optional)** — Start via Docker Compose:

```bash
docker-compose up -d elasticsearch
```

> If you don't need search, set `elasticsearch.enabled=false` in `application.properties`. The backend will gracefully degrade to JPA database search.

### 3. Configure the Backend

Copy the config template and customize:

```bash
cd homestay-backend
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

Edit `application-local.properties` with your local MySQL password, Redis password, JWT secret, etc.

> You can also edit `application.properties` directly, but do not commit sensitive values to the repository.

### 4. Start the Backend

```bash
cd homestay-backend
mvn clean compile
mvn spring-boot:run
```

The backend runs at `http://localhost:8080` by default. Flyway automatically runs all database migrations (V1 – V49, 41 scripts) on startup — no manual table creation needed.

### 5. Start the Guest & Host App

```bash
cd homestay-front
cp .env.example .env.local   # Optional: configure AMap API keys
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` requests to `http://127.0.0.1:8080`.

### 6. Start the Admin App

```bash
cd homestay-admin
npm install
npm run dev
```

Open `http://localhost:5174`. Vite proxies `/api` requests to the backend.

### First-Time Setup

The project does not ship with a pre-seeded admin account. On first backend startup, `DataInitializer` automatically seeds default amenity data.

- **Guest / Host**: Register an account on the guest app (`localhost:5173`).
- **Admin**: After registering, promote the user in the database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

## Configuration

Backend configuration file:

```text
homestay-backend/src/main/resources/application.properties
```

Key settings for local development:

| Setting | Description |
|---|---|
| `spring.datasource.*` | MySQL connection URL, username, password |
| `spring.data.redis.*` | Redis host, port, password, database index |
| `spring.elasticsearch.*` | Elasticsearch connection URI (optional) |
| `elasticsearch.enabled` | Set to `false` to skip ES and fall back to JPA search |
| `jwt.secret` | JWT signing secret |
| `spring.mail.*` | SMTP email service configuration |
| `payment.alipay.*` | Alipay sandbox app ID, keys, gateway URL, callbacks |
| `file.upload-dir` | Uploaded file storage directory |

Frontend environment variables:

| File | Description |
|---|---|
| `homestay-front/.env.example` | Guest app env template (AMap API keys, etc.) |

## Testing & Building

### Backend

```bash
cd homestay-backend
mvn test
mvn clean package
```

### Guest App

```bash
cd homestay-front
npm run build
```

### Admin App

```bash
cd homestay-admin
npm run build
```

## Documentation Index

| Document | Description |
|---|---|
| [Installation Guide](docs/INSTALL.md) | Detailed setup instructions with AI Agent automation guide |
| [Project Structure Overview](docs/项目结构总览.md) | Directory layout and module responsibilities |
| [Tech Stack Guide](docs/项目技术栈说明.md) | Technology choices and dependency notes |
| [Dev Environment Setup](docs/开发环境配置指南.md) | Local development environment preparation |
| [Admin App Structure](docs/homestay-admin%20详细结构.md) | Admin frontend directory details |
| [Guest App Docs](homestay-front/README.md) | Guest & host frontend documentation |
| [Admin App Docs](homestay-admin/README.md) | Admin frontend documentation |

## Security Notes

Sensitive values in `application.properties` (database password, JWT secret, Alipay private keys, etc.) are placeholders.
Copy the file to `application-local.properties` and fill in your real local values (already excluded via `.gitignore`).

## License

MIT License
