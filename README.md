<p align="center">
  <img src="assets/logo.png" alt="Zenith Logo" width="180" style="border-radius: 24px;" />
</p>

# ⚡ Zenith DSL

> **A modern, concise language on top of Java that eliminates Spring Boot boilerplate.**

Zenith is a domain-specific language designed to abstract away the ceremony-heavy parts of enterprise Java. Instead of manually writing 7 to 9 separate files (Entity, DTOs, Repository, Service, ServiceImpl, Controller, Mapper, and Exception Handlers) and dozens of repetitive annotations for every database table, you declare your models, relationships, validation rules, and REST APIs in a single, clean `.zen` file.

Zenith then transpiles your specification into **production-grade Spring Boot 3 + Java 17** code in milliseconds.

---

## 🥊 The Problem: Java's 9-File Ceremony

To expose a standard CRUD endpoint with validation, pagination, and relationships in idiomatic Spring Boot, Java requires:

```
src/main/java/com/example/store/
├── entity/Product.java                     (~60 lines: @Entity, @Table, @Id, @Column, getters/setters)
├── dto/CreateProductRequest.java           (~30 lines: Java record with @NotBlank, @Positive, etc.)
├── dto/UpdateProductRequest.java           (~25 lines: Java record)
├── dto/ProductResponse.java                (~25 lines: Java record)
├── mapper/ProductMapper.java               (~40 lines: mapping DTOs <-> Entities)
├── repository/ProductRepository.java       (~20 lines: @Repository, JpaRepository, custom queries)
├── service/ProductService.java             (~15 lines: Service interface)
├── service/impl/ProductServiceImpl.java    (~70 lines: @Service, @Transactional, paging, search)
└── controller/ProductController.java       (~60 lines: @RestController, @GetMapping, @PostMapping...)
```
👉 **Total:** **9 files, ~350 lines of boilerplate** per resource!

---

## ✨ The Zenith Solution

With Zenith, the exact same resource is expressed in **18 lines of clean, readable code**:

```zen
model Product {
    id: Long @id @auto
    sku: String(length = 32) @unique
    name: String(min = 3, max = 150)
    description: String? @text
    price: Decimal(precision = 12, scale = 2) @positive
    stock: Int @min(0) = 0
    status: ProductStatus = ACTIVE
    createdAt: DateTime @autoNow
    updatedAt: DateTime @autoUpdate

    relations {
        category: Category @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            search: [name, sku]
            filter: [status]
            sort: [price, createdAt]
        }

        post "/{id}/archive" -> ProductResponse {
            description: "Archives a product"
            guard: status != ProductStatus.ARCHIVED, "Product is already archived"
            action: {
                status = ProductStatus.ARCHIVED,
                stock = 0
            }
        }
    }
}
```

---

## ⚡ Build & Installation Scripts

Zenith includes dedicated one-click automation scripts to quickly compile the CLI compiler and install the VS Code extension:

### 1. Build & Update Zenith CLI
Whenever you update the parser, lexer, AST, or generators (`src/main/java/`):
```cmd
.\build-cli.bat
```
* **What it does**: Scans all Java sources, compiles them to `target/classes`, and updates the standalone executable fat JAR `bin\zenith.jar`.
* **Important**: Close any running `zenith studio` or Java processes before running to avoid Windows file locks on `bin\zenith.jar`.

### 2. Package & Install VS Code Extension
Whenever you update syntax highlighting (`syntaxes/zenith.tmLanguage.json`), icons, or extension configurations:
```cmd
.\update-extension.bat
```
*(Or from within `zenith-vscode`: `.\update.bat`)*
* **What it does**: Packages the extension into a `.vsix` archive and immediately forces an install into your local VS Code via `code --install-extension`.
* **Apply changes**: Press `Ctrl + Shift + P` in VS Code and choose **Developer: Reload Window**.

---

## 🛠️ CLI Commands & DX (Phase 4)

Zenith includes self-contained launcher scripts in the `bin/` directory:

* **Windows (Command Prompt):** `.\bin\zenith.bat <command>`
* **Windows (PowerShell):** `.\bin\zenith.ps1 <command>`
* **Linux / macOS:** `./bin/zenith <command>`

### 1. `zenith watch <file.zen> [-o <out-dir>]` 🔥 *(Hot Reload)*
Monitors your `.zen` file and automatically re-compiles the entire Spring Boot backend in milliseconds whenever you save:
```cmd
.\bin\zenith.bat watch examples\ecommerce.zen -o generated\shopsphere
```
Output:
```
>> Watching file: examples\ecommerce.zen
>> Target output: generated\shopsphere
   [SUCCESS] Recompiled in 120ms -> 56 files up-to-date (4 models)

>> File changed at 22:15:20. Recompiling...
   [SUCCESS] Recompiled in 115ms -> 56 files up-to-date (4 models)
```

### 2. `zenith build <file.zen> [-o <output-dir>]`
Compiles your schema and generates the complete Spring Boot 3 application.

### 3. `zenith inspect <file.zen>`
Analyzes and prints all parsed models, relations, field constraints, and REST API paths.

### 4. `zenith validate <file.zen>`
Validates schema syntax, types, and foreign key references.

### 5. `zenith init [file.zen]`
Scaffolds a new starter schema file.

---

## 🎨 VS Code Extension (Phase 4)

A dedicated VS Code extension is included under `zenith-vscode/`:
* **Syntax Highlighting**: Keywords (`config`, `model`, `enum`, `guard`, `action`, `endpoints`, `crud`), HTTP methods, decorators (`@id`, `@password`, `@role`), and operators.
* **Auto-closing pairs**: Braces `{}`, brackets `[]`, and quotes.
* **Snippets**: Instant scaffolding for `config`, `model`, `enum`, and `endpoint`.

**Installation**:
Run `.\update-extension.bat` from the root directory to package and install the extension into VS Code automatically. Simply reload your window (`Ctrl+Shift+P` -> `Developer: Reload Window`) to enjoy instant `.zen` syntax highlighting!

---

## 🔒 Enterprise Features (Phase 2 & 3)

### 1. Spring Security 6 & JWT Authentication
Set `auth: jwt` in `config` and designate a model with `@password` and `@role`:
```zen
model Customer {
    id: Long @id @auto
    email: String @unique @email
    password: String @password
    role: UserRole = CUSTOMER @role
}
```
Zenith generates:
* `SecurityConfig.java`: Stateless session, CORS, CSRF disabled, BCrypt encoder.
* `JwtService.java` & `JwtAuthenticationFilter.java`: Bearer token authorization.
* `AuthController.java`: `POST /api/v1/auth/login` and `POST /api/v1/auth/register`.
* Passwords are automatically hidden and excluded from all Response DTOs.

### 2. Interactive Swagger UI / OpenAPI 3
* Live interactive Swagger UI at: `http://localhost:8080/swagger-ui.html`
* `@Tag` and `@Operation` auto-documented on all endpoints.

### 3. Flyway Database Migrations
* Generates `src/main/resources/db/migration/V1__initial_schema.sql` with exact DDL, foreign keys, and indexes.

### 4. API Security & Role-Based Access Control (RBAC)

Zenith integrates with Spring Security 6 to generate method-level `@PreAuthorize` security checks across standard CRUD and custom endpoints.

#### Default Security Convention
* **Read operations** (`GET /`, `GET /{id}`): Public (`@PreAuthorize("permitAll()")`).
* **Write operations** (`POST`, `PUT`, `DELETE`): Authenticated (`@PreAuthorize("isAuthenticated()")`).

#### CRUD Security Options
```zen
model Product {
    endpoints {
        // Option A: Open public CRUD (no token needed)
        crud { public: true }

        // Option B: Full authenticated CRUD (Bearer JWT required for all)
        crud { auth: true }

        // Option C: Global role restriction (requires ADMIN or MANAGER)
        crud { roles: [ADMIN, MANAGER] }

        // Option D: Granular per-operation RBAC
        crud {
            list: public              // Anyone can browse catalog
            get: public               // Anyone can view details
            create: [ADMIN, EDITOR]   // Editors & Admins can create
            update: [ADMIN, EDITOR]   // Editors & Admins can update
            delete: [ADMIN]           // Only Admins can delete
        }
    }
}
```

#### Custom Endpoint Security Options
```zen
// 1. Explicitly Public Route
get "/catalog/featured" -> List<ProductResponse> {
    public: true                      // @PreAuthorize("permitAll()")
}

// 2. Authenticated-Only Route
post "/{id}/rate" -> Void {
    auth: true                        // @PreAuthorize("isAuthenticated()")
}

// 3. Multi-Role Restriction
post "/{id}/archive" -> ProductResponse {
    auth: [ADMIN, MANAGER]            // @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
}

// 4. Declarative Role Guard + Domain Business Rule
post "/{id}/publish" -> ProductResponse {
    guard: role in [ADMIN, PUBLISHER], "Must be a publisher or admin"
    guard: status == ProductStatus.DRAFT, "Only draft products can be published"
    action: {
        status = ProductStatus.PUBLISHED
    }
}

// 5. User-Scoped Route (Filters automatically for currentUser)
get "/my-products" -> List<ProductResponse> {
    user: true
}
```

### 5. Declarative Logic & Domain Actions
Zenith supports complete domain business logic: instantiate secondary entities, record audit trails, notify owners, and resolve authenticated users:
```zen
post "/{id}/complete" -> Void {
    auth: true
    guard: status == TaskStatus.IN_PROGRESS, "Task must be in progress to complete"
    action: {
        status = TaskStatus.DONE

        // Secondary entity instantiation
        create AuditLog {
            action = "TASK_COMPLETED",
            details = "Task " + entity.title + " completed by " + currentUser.email
        }

        // Notification hook
        notify(entity.project.owner, "Task completed: " + entity.title)
    }
}
```

### 6. File Uploads & Media Storage (`@file`, `@image`)
Provider-independent object storage abstraction supporting **MinIO** for local development and **AWS S3** for production cloud deployments with zero code changes:
```zen
config {
    storage: minio               // minio (local dev) | s3 (cloud)
    storageBucket: "media-bucket"
}

model Customer {
    id: UUID @id @auto
    email: String @email @unique
    // Validated image uploads with MIME constraint
    avatar: String? @image(maxSize = "5MB", types = ["image/jpeg", "image/png"], path = "avatars/")
    // Document upload with size constraint
    resume: String? @file(maxSize = "20MB", types = ["application/pdf"], path = "resumes/")
}
```
* **Generic REST API**: `POST /api/v1/files/upload`, `GET /api/v1/files/{fileKey}`, `DELETE /api/v1/files/{fileKey}`.
* **Entity-Bound Endpoints**: `POST /api/v1/customers/{id}/avatar`, `GET ...`, `DELETE ...` (generated only when `endpoints` and `crud` exist on the model).
* **Zero-code cloud migration**: Switch between local MinIO and AWS S3 via environment variables (`STORAGE_PROVIDER=s3`, `AWS_REGION`, etc.).

### 7. Declarative Database Seeding
Populate dev and staging environments with deterministic seed data and foreign key cross-referencing:
```zen
seed UserEntity {
    [
        { email: "admin@app.io", role: ADMIN, fullName: "Admin", password: "password123" }
    ]
}

seed Task {
    [
        {
            title: "Configure CI/CD",
            status: TODO,
            assignee: seed UserEntity[0]     // Cross-references the 1st UserEntity
        }
    ]
}
```

### 8. Modular Multi-File Schemas (`import`)
Decompose enterprise architectures into modular `.zen` domain files:
```zen
// schema.zen (Entry schema)
config {
    app: "EnterpriseCRM"
    database: postgres
    auth: jwt
    storage: minio
}

import "./auth.zen"       // UserEntity & Role
import "./customers.zen"  // Customer & Address
import "./orders.zen"     // Order & OrderItems
```

### 9. Custom Data Transfer Objects (DTO)
Define custom request and response payloads decoupled from JPA database models:
```zen
dto AssignTaskRequest {
    assigneeId: UUID
    comment: String?
}

dto TaskSummaryResponse {
    totalTasks: Int
    completedTasks: Int
}
```

---

---

## 🌐 Full-Stack Code Generation (Phase 5)

Zenith bridges the gap between backend and frontend by generating complete client SDKs and container infrastructure directly from your `.zen` definition:

### 1. TypeScript & React Query Client SDK (`sdk/typescript/`)
Zenith automatically generates an end-to-end typed client for your frontend:
* **Strict TypeScript Interfaces** (`src/types.ts`): Model interfaces, Create/Update request payloads, enums, pagination models, and auth responses.
* **Pure Fetch HTTP Client** (`src/client.ts`): Zero-dependency API client with built-in JWT bearer token injection:
  ```typescript
  import { createClient } from './sdk/typescript/src/client';

  const api = createClient({
    baseUrl: 'http://localhost:8080',
    getToken: () => localStorage.getItem('token')
  });

  // Fully typed autocompletion:
  const products = await api.products.list({ page: 0, size: 10, search: 'shirt' });
  const newProduct = await api.products.create({ name: 'T-Shirt', price: 29.99 });
  ```
* **React Query Hooks** (`src/react-query.ts`): Instant integration with `@tanstack/react-query` v4 & v5:
  ```tsx
  import { useProductList, useCreateProduct } from './sdk/typescript/src/react-query';

  function ProductCatalog() {
    const { data, isLoading } = useProductList(api, { page: 0, size: 20 });
    const createProduct = useCreateProduct(api);

    if (isLoading) return <div>Loading products...</div>;
    return (
      <ul>
        {data?.content.map(p => <li key={p.id}>{p.name} - ${p.price}</li>)}
      </ul>
    );
  }
  ```

### 2. Instant Docker & Production Containerization
Zenith generates multi-stage container deployment out of the box:
* **`Dockerfile`**: Multi-stage build using `maven:3.9-eclipse-temurin-17` builder and an unprivileged alpine runtime container (`eclipse-temurin:17-jre-alpine`).
* **`docker-compose.yml`**: Pairs your Spring Boot service with the configured database (e.g. PostgreSQL with auto healthcheck, persistent volumes, and credentials).
* **`.dockerignore`**: Pre-configured to keep container builds fast and lightweight.

Run your entire stack with one command:
```bash
docker-compose up --build
```

---

## 🧪 Testing the Generated Project

```cmd
cd generated\shopsphere
mvn spring-boot:run
```

Available endpoints:
* `Swagger UI`: `http://localhost:8080/swagger-ui.html`
* `Auth Login`: `POST http://localhost:8080/api/v1/auth/login`
* `Auth Register`: `POST http://localhost:8080/api/v1/auth/register`
* `Products`: `GET/POST http://localhost:8080/api/v1/products`
* `H2 Console`: `http://localhost:8080/h2-console`

---

## 🗺️ Roadmap & Missing Features

For the full architectural roadmap, prioritization, implementation complexity, and planned syntax for upcoming Zenith features, see **[ROADMAP.md](ROADMAP.md)**.
