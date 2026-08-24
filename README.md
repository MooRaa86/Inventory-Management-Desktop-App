<h1 align="center">Inventory Manager</h1>

<p align="center">
  <strong>Offline warehouse & inventory management system for Windows</strong>
</p>

<p align="center">
  Spring Boot 3 &bull; SQLite &bull; React 19 &bull; Electron &bull; Portable
</p>

---

## Table of Contents

- [Overview](#overview)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
  - [Dashboard](#dashboard)
  - [Product Catalog](#product-catalog)
  - [Stock Management](#stock-management)
  - [Purchases](#purchases)
  - [Issues](#issues)
  - [Reports & PDF Export](#reports--pdf-export)
  - [Users & Security](#users--security)
  - [Backup & Restore](#backup--restore)
  - [Settings & Branding](#settings--branding)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quick Start (Development)](#quick-start-development)
  - [Full Portable Build](#full-portable-build)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Security](#security)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

**Inventory Manager** is a production-grade, self-contained desktop application designed for
small-to-medium warehouse operations. It runs entirely offline on a single Windows workstation
with zero internet, server, or cloud dependencies.

All data lives in a local SQLite file. The application is packaged as a single portable folder
that can be copied to any Windows machine and launched with a double-click.

**Key design principles:**

- **Offline-first** &mdash; no network required at runtime
- **Portable** &mdash; no installation, no registry, no admin rights needed
- **Atomic stock engine** &mdash; every stock change is transactional; oversell is rejected and rolled back
- **Full audit trail** &mdash; every mutation is logged with user, timestamp, and change details
- **Role-based access** &mdash; four built-in roles with granular permissions
- **Professional PDF reports** &mdash; branded, styled, ready for print

---

## Screenshots

> _Screenshots are of the production portable build running on Windows._

| Login | Dashboard | Products | Purchases |
|:---:|:---:|:---:|:---:|
| Dark gradient login screen | Real-time KPIs, charts, alerts | CRUD with search, categories, stock levels | Purchase orders with stock impact |

| Reports | Settings | Users | Stock Movements |
|:---:|:---:|:---:|:---:|
| 7 report types, PDF/CSV/XLSX export | Company branding, backup schedule | RBAC with 4 roles | Full movement ledger |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Desktop (Windows)                         │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Electron Shell                         │  │
│  │   • Spawns backend process                                │  │
│  │   • Waits for /api/health                                 │  │
│  │   • Opens BrowserWindow → http://127.0.0.1:8475           │  │
│  │   • Kills backend tree on close                           │  │
│  └───────────────────────┬───────────────────────────────────┘  │
│                          │                                      │
│  ┌───────────────────────▼───────────────────────────────────┐  │
│  │              Spring Boot 3.3.4 Backend                     │  │
│  │                                                            │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐  │  │
│  │  │  REST API     │  │  Security     │  │  Reports       │  │  │
│  │  │  14 controllers│  │  JWT + RBAC  │  │  PDF/CSV/XLSX  │  │  │
│  │  └──────┬───────┘  └──────────────┘  └────────────────┘  │  │
│  │         │                                                  │  │
│  │  ┌──────▼───────┐  ┌──────────────┐  ┌────────────────┐  │  │
│  │  │  JPA/Hibernate│  │  Flyway       │  │  SQLite         │  │  │
│  │  │  (18 migrations)│ │  Schema Auth │  │  WAL + FK       │  │  │
│  │  └──────────────┘  └──────────────┘  └───────┬────────┘  │  │
│  │                                                │           │  │
│  │  ┌─────────────────────────────────────────────▼────────┐  │  │
│  │  │              data/inventory.db                        │  │  │
│  │  │  • 23 tables  •  Audit log  •  Stock ledger          │  │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  React 19 SPA (embedded in backend static/)               │  │
│  │  16 screens  •  React Router  •  Vite 8 build             │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  JRE (jlink-trimmed, ~80MB)                               │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Request Flow

```
Browser (Electron)
    │
    ▼
GET /api/products
    │
    ├─► JwtAuthenticationFilter  (validates Bearer token)
    │       │
    │       ▼
    ├─► ProductController.getProducts()
    │       │
    │       ▼
    ├─► ProductService  (business logic, filtering, pagination)
    │       │
    │       ▼
    ├─► ProductRepository  (Spring Data JPA)
    │       │
    │       ▼
    ├─► Hibernate  (SQLiteDialect)
    │       │
    │       ▼
    └─► inventory.db  (WAL mode, foreign keys ON)
```

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Language** | Java | 21 |
| **Backend Framework** | Spring Boot | 3.3.4 |
| **Database** | SQLite (via JDBC) | 3.46.1.3 |
| **Schema Management** | Flyway | 10.10.0 |
| **ORM** | Hibernate (JPA) | (Spring managed) |
| **Authentication** | JWT (jjwt) | 0.12.6 |
| **PDF Generation** | OpenPDF | 1.3.43 |
| **Excel Export** | Apache POI (SXSSF) | 5.3.0 |
| **Object Mapping** | MapStruct + Lombok | 1.6.3 |
| **Frontend** | React | 19.2.8 |
| **Build Tool (FE)** | Vite | 8.2.0 |
| **Routing** | React Router | 7.18.2 |
| **Desktop Shell** | Electron | (latest) |
| **Java Runtime** | jlink (trimmed JRE) | 21 |
| **Build Tool (BE)** | Maven | 3.9+ |

---

## Features

### Dashboard

Real-time overview of warehouse operations:

- **KPI cards** &mdash; total products, low-stock alerts, today's movements, pending purchases
- **7-day movement chart** &mdash; stock in vs. stock out trend
- **Category breakdown** &mdash; stock value distribution
- **Low stock alerts** &mdash; products below minimum threshold
- **Backup health** &mdash; last backup status, database size
- **Company name** &mdash; pulled from Settings, displayed in header
- **Refresh button** &mdash; instant data reload

### Product Catalog

Full product management with:

- Name, category, unit, supplier
- Cost price &amp; selling price (stored as integer cents for precision)
- Minimum stock threshold (triggers low-stock alerts)
- Opening quantity on creation
- **Permanent delete** with history check &mdash; blocked if stock movements exist
- Search, filter by category/supplier, pagination

### Stock Management

Atomic stock engine with:

- **Stock In** &mdash; add inventory with supplier reference and notes
- **Stock Out** &mdash; remove inventory with reason tracking
- **Adjustments** &mdash; reconcile physical counts
- **Full movement ledger** &mdash; every change recorded with type, quantity, running balance
- **Oversell protection** &mdash; concurrent withdrawals are serialized; insufficient stock is rejected with rollback
- **Stock snapshots** &mdash; current stock levels per product

### Purchases

Purchase order workflow:

- **PENDING** &rarr; **RECEIVED** &rarr; (terminal)
- Receiving a purchase **atomically increases stock** for each line item
- **Cancellation** while pending reverses any partial stock impact
- **Line items** with product, quantity, unit cost price
- **Total calculation** &mdash; sum of all line items
- **Export PDF** &mdash; professional purchase invoice with company header

### Issues

Goods issue workflow:

- **PENDING** &rarr; **APPROVED** &rarr; **COMPLETED** &rarr; (terminal)
- **Approval** by manager role
- **Completion** atomically deducts stock
- **Line items** with product, quantity
- Department/recipient tracking

### Reports &amp; PDF Export

Seven report types, each with professional PDF generation:

| Report | Description |
|--------|-------------|
| **Inventory** | Full stock levels with values, sorted by product |
| **Low Stock** | Products below minimum threshold |
| **Stock Movements** | Complete movement history with type indicators |
| **Purchases** | All purchase orders with totals |
| **Issues** | All goods issues with totals |
| **Suppliers** | Supplier directory with contact info |
| **Audit Log** | System-wide change audit trail |

**PDF Report Features:**
- Company name header bar (configurable in Settings)
- Report title with generation date and row count
- Smart column widths (wider for names, narrower for numbers)
- Right-aligned currency and number columns
- Color-coded status badges (green/orange/red)
- Alternating row colors for readability
- Summary/total rows for Inventory (total stock value) and Purchases (total amount)
- Page numbers footer

**Export formats:** PDF, CSV, XLSX, JSON

### Users &amp; Security

- **JWT authentication** &mdash; stateless, 30-minute session timeout
- **4 built-in roles:**

| Role | Permissions |
|------|------------|
| `ADMIN` | Everything, including users, settings, backup/restore |
| `WAREHOUSE_MANAGER` | Catalog, purchases, issues, reports, backups |
| `WAREHOUSE_EMPLOYEE` | Stock operations, drafts, view reports |
| `VIEWER` | Read-only access to all screens |

- **Password policy** &mdash; minimum 8 characters, complexity requirements
- **Account lockout** &mdash; configurable failed attempt threshold
- **BCrypt hashing** &mdash; passwords never stored in plaintext
- **Full audit log** &mdash; every API mutation recorded with user, action, entity, timestamp

### Backup &amp; Restore

- **Manual backups** &mdash; create on-demand from the Backups screen
- **Automatic backups** &mdash; configurable schedule (default 02:00 daily, 30-day retention)
- **Backup format** &mdash; ZIP archive containing `inventory.db` + metadata JSON
- **Integrity verification** &mdash; checksum validation on backup files
- **Export/Import** &mdash; copy backups between machines via folder or zip
- **Live restore** &mdash; restore while the app is running:
  1. Verify integrity
  2. Check schema version compatibility
  3. Save a SAFETY backup
  4. Suspend HikariCP connections
  5. Swap the database file
  6. Resume connections
  7. All without restarting the app
- **Manual restore** &mdash; copy `inventory.db` into `data/` while app is closed, delete `-wal`/`-shm` files

### Settings &amp; Branding

Configurable via the Settings screen (ADMIN role required):

| Setting | Description |
|---------|-------------|
| `app.name` | Application name (shown in sidebar) |
| `company.name` | Company name (shown in dashboard header, PDF reports) |
| `company.currency` | Currency code for reports |
| `backup.enabled` | Enable/disable automatic backups |
| `backup.time` | Daily backup time (HH:mm) |
| `backup.retention.count` | Number of automatic backups to keep |

---

## Getting Started

### Prerequisites

**For development:**

- JDK 21+ (`JAVA_HOME` environment variable set)
- Maven 3.9+
- Node.js 18+ and npm
- Git

**For portable build:**

- All of the above, plus ~500MB disk space

### Quick Start (Development)

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/InventoryManager.git
cd InventoryManager

# 2. Install frontend dependencies
cd frontend
npm install
cd ..

# 3. Start the backend (API + embedded UI)
cd backend
mvn spring-boot:run
# Backend runs on http://127.0.0.1:8475

# 4. In a separate terminal, start the frontend dev server
cd frontend
npm run dev
# Vite runs on http://localhost:5173 (proxies /api to backend)
```

**Default admin credentials:**

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | `skretting@nutreco.com` |

> **Important:** Change the admin password after first login.

### Full Portable Build

```bash
# Install all dependencies (one-time)
cd frontend && npm install && cd ..
cd desktop && npm install && cd ..

# Build the portable package
powershell -ExecutionPolicy Bypass -File scripts\package-portable.ps1
```

Output: `dist\InventoryManager\` (~400MB)

**Portable layout:**

```
dist\InventoryManager\
├── start.cmd                    ← double-click to run
├── app\                         ← Electron shell
│   ├── main.js
│   └── package.json
├── electron\                    ← prebuilt Electron binaries
├── backend\
│   ├── inventory-backend.jar    ← Spring Boot fat JAR
│   ├── data\                    ← created on first run (SQLite DB)
│   ├── backups\                 ← manual/automatic backups
│   ├── exports\                 ← generated reports
│   ├── logs\                    ← application logs
│   └── config\                  ← JWT secret, admin credentials
└── runtime\                     ← jlink-trimmed JRE (~80MB)
```

**Target machine requirements:** Windows 10/11, no Java/Node/npm needed.

---

## Project Structure

```
InventoryManager/
│
├── backend/                          # Spring Boot 3 API
│   ├── pom.xml                       # Maven config (90 Java source files)
│   └── src/
│       ├── main/
│       │   ├── java/com/company/inventory/
│       │   │   ├── auth/             # Login, JWT token endpoint
│       │   │   ├── audit/            # Audit log entity, service, controller
│       │   │   ├── backup/           # Backup/restore, automatic scheduler
│       │   │   ├── category/         # Product categories CRUD
│       │   │   ├── common/           # Money, errors, pagination, converters
│       │   │   ├── config/           # SecurityConfig (JWT, CORS, RBAC)
│       │   │   ├── dashboard/        # Dashboard aggregations, projections
│       │   │   ├── inventory/        # Stock in/out/adjustments, movement ledger
│       │   │   ├── issue/            # Goods issue workflow
│       │   │   ├── product/          # Product catalog CRUD
│       │   │   ├── purchase/         # Purchase orders CRUD
│       │   │   ├── report/           # PDF/CSV/XLSX report generation
│       │   │   ├── security/         # JWT filter, user details, secret provider
│       │   │   ├── settings/         # System settings (key-value store)
│       │   │   ├── startup/          # Admin init, directory init, schema verify
│       │   │   ├── supplier/         # Supplier CRUD
│       │   │   ├── unit/             # Unit of measure CRUD
│       │   │   └── user/             # User/role/permission management
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── static/           # Embedded React SPA (production build)
│       │       └── db/migration/     # V1–V18 Flyway SQL migrations
│       └── test/                     # 3 test files (concurrency, purchase, money)
│
├── frontend/                         # React 19 SPA
│   ├── package.json                  # Vite 8, React Router 7
│   ├── vite.config.js                # Dev proxy to :8475, build to dist/
│   └── src/
│       ├── api.js                    # HTTP client, JWT handling, download helper
│       ├── App.jsx                   # Router, auth, error boundary, sidebar
│       ├── index.css                 # Global styles, CSS variables, responsive
│       └── screens/                  # 16 screen components
│           ├── Dashboard.jsx
│           ├── Login.jsx
│           ├── Products.jsx
│           ├── Purchases.jsx
│           ├── Issues.jsx
│           ├── Stock.jsx
│           ├── Reports.jsx
│           ├── Categories.jsx
│           ├── Suppliers.jsx
│           ├── Units.jsx
│           ├── Users.jsx
│           ├── Settings.jsx
│           ├── Backups.jsx
│           ├── AuditLogs.jsx
│           ├── Crud.jsx              # Generic CRUD component
│           └── Guide.jsx             # Built-in user guide
│
├── desktop/                          # Electron shell
│   ├── main.js                       # Process manager, window lifecycle
│   └── package.json
│
├── scripts/
│   └── package-portable.ps1          # Full build + packaging script
│
├── docs/
│   └── deployment-guide.html         # Standalone deployment guide (print to PDF)
│
├── dist/                             # Build output (gitignored)
│   └── InventoryManager/             # Portable application
│
└── .gitignore
```

---

## Database Schema

The database is managed entirely by **Flyway** (`ddl-auto=none`). There are **18 migrations**
creating **23 tables**.

### Entity Relationship Diagram

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│    users      │────▶│  user_roles  │◀────│    roles      │
│              │     └──────────────┘     │              │
│  id          │                          │  id          │
│  username    │                          │  name        │
│  password    │                          └──────┬───────┘
│  email       │                                 │
│  enabled     │                          ┌──────▼───────┐
│  locked      │                          │ role_perms   │
└──────────────┘                          │              │
                                          │  role_id     │
┌──────────────┐     ┌──────────────┐     │  perm_id     │
│  categories   │     │   products   │     └──────┬───────┘
│              │◀────│              │             │
│  id          │     │  id          │     ┌──────▼───────┐
│  name        │     │  name        │     │ permissions  │
│  description │     │  category_id │     │              │
└──────────────┘     │  unit_id     │     │  id          │
                     │  supplier_id │     │  name        │
┌──────────────┐     │  cost_price  │     └──────────────┘
│    units      │◀───│  sell_price  │
│              │     │  min_stock   │
│  id          │     │  opening_qty │
│  name        │     └──────┬───────┘
│  symbol      │            │
└──────────────┘            │
                     ┌──────▼───────┐     ┌──────────────┐
┌──────────────┐     │stock_movements│     │  purchases   │
│  suppliers    │     │              │     │              │
│              │◀────│  id          │     │  id          │
│  id          │     │  product_id  │     │  supplier_id │
│  name        │     │  type        │     │  status      │
│  contact     │     │  quantity    │     │  total_amount│
│  phone       │     │  reference   │     │  notes       │
│  email       │     │  notes       │     │  created_at  │
│  address     │     │  created_at  │     └──────┬───────┘
│  tax_number  │     │  created_by  │            │
└──────────────┘     └──────────────┘     ┌──────▼───────┐
                                          │purchase_items│
┌──────────────┐     ┌──────────────┐     │              │
│    issues     │     │  issue_items │     │  id          │
│              │     │              │     │  purchase_id │
│  id          │◀────│  id          │     │  product_id  │
│  status      │     │  issue_id    │     │  quantity    │
│  notes       │     │  product_id  │     │  unit_cost   │
│  created_at  │     │  quantity    │     └──────────────┘
│  created_by  │     └──────────────┘
└──────────────┘

┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  audit_logs   │     │  backups      │     │system_settings│
│              │     │              │     │              │
│  id          │     │  id          │     │  key         │
│  user_id     │     │  filename    │     │  value       │
│  action      │     │  path        │     │  from_user   │
│  entity_type │     │  size        │     │  updated_at  │
│  entity_id   │     │  checksum    │     └──────────────┘
│  details     │     │  type        │
│  timestamp   │     │  status      │
└──────────────┘     │  created_at  │
                     └──────────────┘
```

### Key Tables

| Table | Records | Description |
|-------|---------|-------------|
| `users` | varies | Application users |
| `roles` | 4 | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_EMPLOYEE, VIEWER |
| `permissions` | ~20 | Granular action permissions |
| `products` | varies | Product catalog |
| `stock_movements` | varies | Full audit trail of all stock changes |
| `purchases` | varies | Purchase orders |
| `purchase_items` | varies | Line items per purchase |
| `issues` | varies | Goods issue orders |
| `issue_items` | varies | Line items per issue |
| `system_settings` | ~10 | Key-value configuration store |
| `audit_logs` | varies | System-wide change log |
| `backups` | varies | Backup records with checksums |

---

## API Reference

All endpoints are prefixed with `/api`. Authentication uses `Bearer` token in the `Authorization` header.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/login` | No | Login, returns JWT token |
| `GET` | `/api/health` | No | Health check (`{"status":"UP"}`) |

### Products

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET` | `/api/products` | VIEWER+ | List products (paginated, filterable) |
| `GET` | `/api/products/{id}` | VIEWER+ | Get product by ID |
| `POST` | `/api/products` | MANAGER+ | Create product (supports `openingQuantity`) |
| `PUT` | `/api/products/{id}` | MANAGER+ | Update product |
| `DELETE` | `/api/products/{id}` | ADMIN | Soft-delete product |
| `DELETE` | `/api/products/{id}/permanent` | ADMIN | Permanent delete (blocked if history exists) |

### Stock

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET` | `/api/stock` | VIEWER+ | Current stock levels |
| `POST` | `/api/stock/in` | EMPLOYEE+ | Stock in (add inventory) |
| `POST` | `/api/stock/out` | EMPLOYEE+ | Stock out (remove inventory) |
| `POST` | `/api/stock/adjust` | MANAGER+ | Stock adjustment |
| `GET` | `/api/stock/movements` | VIEWER+ | Movement history (paginated) |

### Purchases

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET` | `/api/purchases` | VIEWER+ | List purchase orders |
| `GET` | `/api/purchases/{id}` | VIEWER+ | Get purchase with line items |
| `POST` | `/api/purchases` | MANAGER+ | Create purchase (with line items) |
| `PUT` | `/api/purchases/{id}` | MANAGER+ | Update purchase |
| `POST` | `/api/purchases/{id}/receive` | MANAGER+ | Receive purchase (increases stock) |
| `POST` | `/api/purchases/{id}/cancel` | MANAGER+ | Cancel purchase |
| `GET` | `/api/purchases/{id}/export` | VIEWER+ | Export purchase invoice as PDF |

### Issues

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET` | `/api/issues` | VIEWER+ | List issues |
| `GET` | `/api/issues/{id}` | VIEWER+ | Get issue with line items |
| `POST` | `/api/issues` | MANAGER+ | Create issue |
| `PUT` | `/api/issues/{id}` | MANAGER+ | Update issue |
| `POST` | `/api/issues/{id}/approve` | MANAGER+ | Approve issue |
| `POST` | `/api/issues/{id}/complete` | MANAGER+ | Complete issue (deducts stock) |

### Reports

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `POST` | `/api/reports/{type}` | VIEWER+ | Generate report (`type`: inventory, low-stock, purchases, issues, suppliers, audit, movements) |
| `GET` | `/api/reports/files/{fileName}` | VIEWER+ | Download generated report file |

### Catalog CRUD

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET/POST/PUT/DELETE` | `/api/categories` | Varies | Category management |
| `GET/POST/PUT/DELETE` | `/api/units` | Varies | Unit of measure management |
| `GET/POST/PUT/DELETE` | `/api/suppliers` | Varies | Supplier management |

### Users &amp; Settings

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET/POST/PUT/DELETE` | `/api/users` | ADMIN | User management |
| `GET` | `/api/settings` | VIEWER+ | Get all settings |
| `PUT` | `/api/settings/{key}` | ADMIN | Update setting |

### Backups

| Method | Endpoint | Permission | Description |
|--------|----------|-----------|-------------|
| `GET` | `/api/backups` | VIEWER+ | List backups |
| `POST` | `/api/backups` | MANAGER+ | Create backup |
| `POST` | `/api/backups/{id}/verify` | MANAGER+ | Verify backup integrity |
| `POST` | `/api/backups/{id}/restore` | ADMIN | Restore from backup |
| `POST` | `/api/backups/{id}/download` | MANAGER+ | Download backup file |
| `POST` | `/api/backups/import` | ADMIN | Import backup from upload |

---

## Security

### Authentication Flow

```
┌──────────┐     POST /api/auth/login      ┌──────────┐
│  Client   │ ──────────────────────────────▶│  Server   │
│          │     { username, password }      │          │
│          │◀──────────────────────────────  │          │
│          │     { token: "eyJ..." }         │          │
└────┬─────┘                                 └──────────┘
     │
     │  GET /api/products
     │  Authorization: Bearer eyJ...
     │
     ▼
┌──────────────────────────────────────────┐
│          JwtAuthenticationFilter          │
│  1. Extract token from header             │
│  2. Validate signature & expiry           │
│  3. Load user + roles + permissions       │
│  4. Set SecurityContext                   │
└──────────────────────────────────────────┘
```

### Security Measures

| Measure | Implementation |
|---------|---------------|
| **Password hashing** | BCrypt (strength 10) |
| **Session management** | Stateless JWT (no server-side sessions) |
| **Token expiry** | 30 minutes (configurable) |
| **Network binding** | `127.0.0.1` only (not reachable from network) |
| **CSRF** | Disabled (stateless API, not applicable) |
| **CORS** | Configured for development; restricted in production |
| **JWT secret** | Auto-generated per installation, stored in `config/jwt-secret.key` |
| **Input validation** | Bean Validation (JSR 380) on all request DTOs |
| **SQL injection** | Prevented by JPA/Hibernate parameterized queries |
| **Audit trail** | Every mutation logged with user, action, entity, timestamp |
| **Account lockout** | Configurable failed attempt threshold |

### Role Permissions Matrix

| Action | ADMIN | MANAGER | EMPLOYEE | VIEWER |
|--------|:-----:|:-------:|:--------:|:------:|
| View products | ✅ | ✅ | ✅ | ✅ |
| Create/edit products | ✅ | ✅ | ❌ | ❌ |
| Delete products | ✅ | ❌ | ❌ | ❌ |
| Stock in/out | ✅ | ✅ | ✅ | ❌ |
| Stock adjustments | ✅ | ✅ | ❌ | ❌ |
| Create purchases | ✅ | ✅ | ❌ | ❌ |
| Receive purchases | ✅ | ✅ | ❌ | ❌ |
| Create issues | ✅ | ✅ | ❌ | ❌ |
| Approve/complete issues | ✅ | ✅ | ❌ | ❌ |
| View reports | ✅ | ✅ | ✅ | ✅ |
| Generate reports | ✅ | ✅ | ✅ | ❌ |
| Create/verify backups | ✅ | ✅ | ❌ | ❌ |
| Restore backups | ✅ | ❌ | ❌ | ❌ |
| Manage users | ✅ | ❌ | ❌ | ❌ |
| Edit settings | ✅ | ❌ | ❌ | ❌ |

---

## Testing

```bash
cd backend

# Run all tests
mvn test

# Run specific tests
mvn test "-Dtest=MoneyTest"
mvn test "-Dtest=InventoryServiceConcurrencyTest"
mvn test "-Dtest=PurchaseLifecycleTest"
```

### Test Coverage

| Test | What It Proves |
|------|---------------|
| **`MoneyTest`** | Integer cents arithmetic: addition, subtraction, multiplication, rounding. Ensures no floating-point drift. |
| **`InventoryServiceConcurrencyTest`** | 10 threads race to withdraw more stock than available. Proves: exactly the available quantity is sold, remaining threads get `INSUFFICIENT_STOCK`, final stock = 0, no partial writes. |
| **`PurchaseLifecycleTest`** | Full purchase workflow: create (PENDING) → receive (RECEIVED) → verify stock increased. Cancel flow: create → cancel → verify stock unchanged. |

### Running Tests in CI

```yaml
# GitHub Actions example
- name: Run backend tests
  run: cd backend && mvn -B test
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| **Port 8475 busy** | Another instance is running. Close it or set `INVENTORY_PORT` environment variable. |
| **Locked database (SQLITE_BUSY)** | Another process holds the DB file. Close all other instances of the application. |
| **White screen in Electron** | Backend didn't start in time. Wait 5 seconds and relaunch, or check `backend/logs/application.log`. |
| **Forgot admin password** | Another ADMIN can reset it via Users screen. If no ADMIN exists, stop the app and restore from backup. |
| **Database corrupted** | Restore from the most recent backup via the Backups screen, or copy a backup's `inventory.db` into `data/`. |
| **PDF reports are empty** | Ensure the `company.name` setting is configured in Settings. Reports use this for the header. |
| **422 errors on POST** | PowerShell 5.1 body encoding issue. Use `cmd /c curl` or test with the browser/Postman. |

### Logs

| Log | Location |
|-----|----------|
| Application log | `backend/logs/application.log` |
| Electron log | `backend/desktop-backend.log` (in portable root) |

---

## Deployment

### Single Machine (Recommended)

1. Build the portable package on a development machine
2. Copy `dist\InventoryManager\` to the target machine
3. Double-click `start.cmd`
4. Login with `admin` / `skretting@nutreco.com`
5. Change the admin password immediately
6. Configure company name, categories, units, and suppliers in Settings

### Multiple Machines

Each machine gets its own copy of the portable folder with its own database. To share data:

1. Run the app on Machine A
2. Create a backup via the Backups screen
3. Copy the backup zip to Machine B
4. Import the backup on Machine B via the Backups screen

### Backup Strategy

| Frequency | Type | Retention |
|-----------|------|-----------|
| Daily 02:00 | Automatic | 30 days |
| On-demand | Manual | Until deleted |
| Before restore | Safety snapshot | Until next safety |

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup

```bash
# Backend
cd backend
mvn spring-boot:run    # Runs on http://127.0.0.1:8475

# Frontend (in parallel)
cd frontend
npm install
npm run dev            # Runs on http://localhost:5173, proxies /api to :8475
```

### Code Style

- **Backend:** Standard Spring Boot conventions. Lombok for boilerplate, MapStruct for mapping.
- **Frontend:** Functional components with hooks. CSS modules via `index.css` (no CSS-in-JS).
- **Database:** All schema changes via Flyway migrations. Never modify `ddl-auto`.

---

## License

This project is proprietary software. All rights reserved.

---

<p align="center">
  Built with Spring Boot 3, React 19, and SQLite
</p>
