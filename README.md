<p align="center">
  <img src="assets/logo.png" alt="Zenith Logo" width="180" style="border-radius: 24px;" />
</p>

# Zenith DSL

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

- **What it does**: Scans all Java sources, compiles them to `target/classes`, and updates the standalone executable fat JAR `bin\zenith.jar`.
- **Important**: Close any running `zenith studio` or Java processes before running to avoid Windows file locks on `bin\zenith.jar`.

### 2. Package & Install VS Code Extension

Whenever you update syntax highlighting (`syntaxes/zenith.tmLanguage.json`), icons, or extension configurations:

```cmd
.\update-extension.bat
```

_(Or from within `zenith-vscode`: `.\update.bat`)_

- **What it does**: Packages the extension into a `.vsix` archive and immediately forces an install into your local VS Code via `code --install-extension`.
- **Apply changes**: Press `Ctrl + Shift + P` in VS Code and choose **Developer: Reload Window**.

---

## 🛠️ CLI Commands & DX (Phase 4)

Zenith includes self-contained launcher scripts in the `bin/` directory:

- **Windows (Command Prompt):** `.\bin\zenith.bat <command>`
- **Windows (PowerShell):** `.\bin\zenith.ps1 <command>`
- **Linux / macOS:** `./bin/zenith <command>`

### 1. `zenith watch <file.zen> [-o <out-dir>]` 🔥 _(Hot Reload)_

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

### 6. `zenith studio <file.zen> [--port 3000]` ⚡ _(Zenith Studio PRO)_

Launches **Zenith Studio PRO** — an in-browser visual architecture IDE featuring live entity relation diagrams (ERD), full Monaco code editor with live syntax diagnostics, clickable import navigation, instant ZIP export, and in-browser REST API testing.

```cmd
.\bin\zenith.bat studio examples\ecommerce.zen
```

---

## 🎨 VS Code Extension (Phase 4)

A dedicated VS Code extension is included under `zenith-vscode/`:

- **Syntax Highlighting**: Keywords (`config`, `model`, `enum`, `guard`, `action`, `endpoints`, `crud`), HTTP methods, decorators (`@id`, `@password`, `@role`), and operators.
- **Auto-closing pairs**: Braces `{}`, brackets `[]`, and quotes.
- **Snippets**: Instant scaffolding for `config`, `model`, `enum`, and `endpoint`.

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

- `SecurityConfig.java`: Stateless session, CORS, CSRF disabled, BCrypt encoder.
- `JwtService.java` & `JwtAuthenticationFilter.java`: Bearer token authorization.
- `AuthController.java`: `POST /api/v1/auth/login` and `POST /api/v1/auth/register`.
- Passwords are automatically hidden and excluded from all Response DTOs.

### 2. Interactive Swagger UI / OpenAPI 3

- Live interactive Swagger UI at: `http://localhost:8080/swagger-ui.html`
- `@Tag` and `@Operation` auto-documented on all endpoints.

### 3. Flyway Database Migrations

- Generates `src/main/resources/db/migration/V1__initial_schema.sql` with exact DDL, foreign keys, and indexes.

### 4. API Security & Role-Based Access Control (RBAC)

Zenith integrates with Spring Security 6 to generate method-level `@PreAuthorize` security checks across standard CRUD and custom endpoints.

#### Default Security Convention

- **Read operations** (`GET /`, `GET /{id}`): Public (`@PreAuthorize("permitAll()")`).
- **Write operations** (`POST`, `PUT`, `DELETE`): Authenticated (`@PreAuthorize("isAuthenticated()")`).

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

// 4. Custom DTO Return Route with Action Logic & Auto-Mapping
post "/{id}/pay" -> OrderSummaryResponse {
    auth: true
    guard: status == OrderStatus.PENDING, "Only pending orders can be marked as paid"
    action: {
        status = OrderStatus.PAID
        create AuditLog {
            action = "ORDER_PAYMENT_PROCESSED",
            details = "Order " + entity.orderNumber + " marked PAID by " + currentUser.email
        }
        notify(entity.customer.email, "Your payment for order " + entity.orderNumber + " succeeded!")
    }
}

// 5. Declarative Role Guard + Domain Business Rule
post "/{id}/publish" -> ProductResponse {
    guard: role in [ADMIN, PUBLISHER], "Must be a publisher or admin"
    guard: status == ProductStatus.DRAFT, "Only draft products can be published"
    action: {
        status = ProductStatus.PUBLISHED
    }
}

// 6. User-Scoped Route (Filters automatically for currentUser)
get "/my-products" -> List<ProductResponse> {
    user: true
}
```

### 5. Declarative Logic & Domain Actions

Zenith supports complete domain business logic: instantiate secondary entities, update related models, record audit trails, notify owners, and resolve authenticated users:

```zen
post "/{id}/complete" -> Void {
    auth: true
    guard: status == TaskStatus.IN_PROGRESS, "Task must be in progress to complete"
    action: {
        // 1. Mutate primary entity
        status = TaskStatus.DONE
        completedAt = DateTime.now

        // 2. Update secondary related model (Parent Project)
        update Project {
            where: id == entity.project.id
            completedTasks = entity.project.completedTasks + 1
            status = ProjectStatus.ACTIVE
        }

        // 3. Secondary entity instantiation
        create AuditLog {
            action = "TASK_COMPLETED",
            details = "Task " + entity.title + " completed by " + currentUser.email
        }

        // 4. Notification hook
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

- **Generic REST API**: `POST /api/v1/files/upload`, `GET /api/v1/files/{fileKey}`, `DELETE /api/v1/files/{fileKey}`.
- **Entity-Bound Endpoints**: `POST /api/v1/customers/{id}/avatar`, `GET ...`, `DELETE ...` (generated only when `endpoints` and `crud` exist on the model).
- **Zero-code cloud migration**: Switch between local MinIO and AWS S3 via environment variables (`STORAGE_PROVIDER=s3`, `AWS_REGION`, etc.).

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

### 9. Custom Data Transfer Objects (DTO) & Endpoint Auto-Mapping

Define custom request and response payloads decoupled from JPA database models with strict typing and optionality:

```zen
dto AssignTaskRequest {
    assigneeId: UUID
    comment: String?
}

dto OrderSummaryResponse {
    id: UUID
    orderNumber: String
    totalAmount: Decimal
    status: OrderStatus
    customerId: UUID?       // Automatically mapped from entity.customer.id
}
```

#### Custom DTO Return Types on Endpoints & Auto-Mapping Example

When an endpoint declares a custom DTO return type (e.g. `post "/{id}/pay" -> OrderSummaryResponse` or `get "/summaries" -> List<OrderSummaryResponse>`), Zenith provides two flexible paradigms:

##### Option A: Explicit `return DTO { ... }` in the Action Block

Allow explicit DTO return construction inside the `action` block for full control over computed fields, aggregations, and authenticated user context:

```zen
post "/{id}/pay" -> OrderSummaryResponse {
    auth: true
    guard: status == OrderStatus.PENDING, "Only pending orders can be marked as paid"
    action: {
        status = OrderStatus.PAID
        return OrderSummaryResponse {
            orderNumber = entity.orderNumber,
            totalAmount = entity.totalAmount,
            itemCount = entity.items.size(),
            processedBy = currentUser.email
        }
    }
}
```

Transpiles directly to explicit record instantiation in the service layer:

```java
entity.setStatus(OrderStatus.PAID);
entity = repository.save(entity);

return new OrderSummaryResponse(
    entity.getOrderNumber(),
    entity.getTotalAmount(),
    entity.getItems().size(),
    currentUsername
);
```

##### Option B: Automatic Coincident Field & Relation Mapping (Zero-Boilerplate)

When no explicit return statement is written in the action block, Zenith automatically compares the target DTO with the model and generates a dedicated mapper method:

- **Coincident Field Matching**: Attributes with matching names and compatible types (`id`, `orderNumber`, `totalAmount`, `status`) are copied directly from the entity.
- **Relational Foreign Key Resolution**: If a DTO property is named `{relation}Id` (e.g. `customerId: UUID`) and the entity declares a relation named `customer`, Zenith extracts the ID safely (`entity.getCustomer() != null ? entity.getCustomer().getId() : null`).
- **Unmatched / Computed Fields**: Attributes not present in the model receive safe typed defaults (`0`, `""`, `null`) or explicit query expressions.
- **Service & Controller Generation**: Service implementations invoke `orderMapper.toOrderSummaryResponse(entity)` and controllers return `ResponseEntity<OrderSummaryResponse>`.

```zen
dto OrderSummaryResponse {
    id: UUID
    orderNumber: String
    totalAmount: Decimal
    status: OrderStatus
    customerId: UUID?       // Automatically mapped from entity.customer.id
    receiptUrl: String?     // Unmatched field: receives default (null)
}

model Order {
    id: UUID @id @auto
    orderNumber: String(length = 20) @unique
    totalAmount: Decimal(precision = 12, scale = 2)
    status: OrderStatus = PENDING

    relations {
        customer: Customer @relation(manyToOne)
    }

    endpoints {
        // Custom endpoint returning OrderSummaryResponse
        post "/{id}/pay" -> OrderSummaryResponse {
            auth: true
            guard: status == OrderStatus.PENDING, "Only pending orders can be marked as paid"
            action: {
                status = OrderStatus.PAID
                notify(entity.customer.email, "Your payment for order " + entity.orderNumber + " succeeded!")
            }
        }
    }
}
```

Transpiles to auto-generated mapper and service invocation:

```java
@Component
public class OrderMapper {
    public OrderSummaryResponse toOrderSummaryResponse(Order entity) {
        if (entity == null) {
            return null;
        }
        return new OrderSummaryResponse(
            entity.getId(),                                                       // Direct field match
            entity.getOrderNumber(),                                              // Direct field match
            entity.getTotalAmount(),                                              // Direct field match
            entity.getStatus(),                                                   // Direct field match
            entity.getCustomer() != null ? entity.getCustomer().getId() : null,  // Relational FK resolved
            null                                                                  // Safe default for unmatched field
        );
    }
}
```

### 10. Background Workers & Scheduled Jobs (`job`, `@cron`)

Define production-grade scheduled maintenance routines, cleanup jobs, and batch queues:

```zen
job ArchiveOldTasks {
    cron: "0 0 2 * * ?"
    description: "Nightly maintenance archiving tasks completed over 30 days ago"
    action: {
        update Task {
            where: status == DONE and updatedAt < daysAgo(30)
            status = ARCHIVED
        }
    }
}
```

Zenith generates a `@Component` job with `@EnableScheduling`, `@EnableAsync`, `ThreadPoolTaskScheduler`, and transactional bulk streaming queries.

#### Logic Transpiler: Time Expressions & Bulk Stream Filters

The Zenith transpiler automatically converts high-level temporal functions and bulk criteria into type-safe, null-safe Spring Boot 3 stream filters and repository batch updates:

- **Time Expressions**:
  - `now()` &rarr; `java.time.LocalDateTime.now()`
  - `daysAgo(n)` &rarr; `java.time.LocalDateTime.now().minusDays(n)`
  - `hoursAgo(n)` &rarr; `java.time.LocalDateTime.now().minusHours(n)`
  - `minutesAgo(n)` &rarr; `java.time.LocalDateTime.now().minusMinutes(n)`
- **Bulk Filter Pipeline**: When `where:` contains collection criteria (e.g. `status == DONE and updatedAt < daysAgo(30)`), the transpiler generates:
  ```java
  taskRepository.findAll().stream()
      .filter(target -> (target.getStatus() != null && "DONE".equalsIgnoreCase(target.getStatus().name()))
                     && (target.getUpdatedAt() != null && target.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(30))))
      .forEach(target -> {
          target.setStatus(TaskStatus.ARCHIVED);
          taskRepository.save(target);
      });
  ```
- **Null-Safe Comparisons**: Temporal comparisons (`<` and `>`) automatically compile into `.isBefore(...)` and `.isAfter(...)` with explicit null guards.

### 11. Automated Unit & Integration Tests Generation

Zenith completely eliminates manual test boilerplate by synthesizing complete **JUnit 5**, **Spring Boot Test**, and **MockMvc** test suites directly from your schema AST:

#### What Zenith Generates Under `src/test/`

| Component                  | Target Location                                        | Technology                                   | What Is Verified                                                                                                                                                                |
| :------------------------- | :----------------------------------------------------- | :------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Test Profile Config**    | `src/test/resources/application.yml`                   | Spring Boot Test Profile                     | Standalone in-memory H2 database (`create-drop`), disabled Flyway, dummy secrets. Enables `mvn test` to pass anywhere without external infrastructure.                          |
| **Context Bootstrap Test** | `src/test/java/.../*ApplicationTests.java`             | `@SpringBootTest`, `@ActiveProfiles("test")` | Verifies full Spring application context startup, bean wiring, and configuration integrity.                                                                                     |
| **Auth Security Test**     | `src/test/java/.../controller/AuthControllerTest.java` | `MockMvc`, `@AutoConfigureMockMvc`           | Asserts `/api/auth/login` and `/api/auth/register` validation checks and invalid credential handling.                                                                           |
| **Controller Test Suites** | `src/test/java/.../controller/*ControllerTest.java`    | `MockMvc`, `@WithMockUser`                   | Asserts unauthenticated access (`401`), role permission guards (`403`), bad payload validation (`400`), non-existent entities (`404`), and happy path operations (`200`/`201`). |
| **Repository Slice Tests** | `src/test/java/.../repository/*RepositoryTest.java`    | `@DataJpaTest`                               | Verifies JPA entity mappings, database dialect compatibility, foreign key cascading, and repository beans under H2.                                                             |

#### Example: Generated MockMvc Controller Suite

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/orders without token returns 401 Unauthorized")
    void testGetAll_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/orders returns 200 OK with PageResponse")
    void testGetAll_Success() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/pay without token returns 401 Unauthorized")
    void testPay_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/orders/00000000-0000-0000-0000-000000000000/pay")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/orders/{id}/pay on non-existent entity returns 404 Not Found")
    void testPay_NotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders/00000000-0000-0000-0000-000000000000/pay")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
```

---

---

## 🌐 Full-Stack Code Generation (Phase 5)

Zenith bridges the gap between backend and frontend by generating complete client SDKs and container infrastructure directly from your `.zen` definition:

### 1. TypeScript & React Query Client SDK (`sdk/typescript/`)

Zenith automatically generates an end-to-end typed client for your frontend:

- **Strict TypeScript Interfaces** (`src/types.ts`): Model interfaces, Create/Update request payloads, enums, pagination models, and auth responses.
- **Pure Fetch HTTP Client** (`src/client.ts`): Zero-dependency API client with built-in JWT bearer token injection:

  ```typescript
  import { createClient } from "./sdk/typescript/src/client";

  const api = createClient({
    baseUrl: "http://localhost:8080",
    getToken: () => localStorage.getItem("token"),
  });

  // Fully typed autocompletion:
  const products = await api.products.list({
    page: 0,
    size: 10,
    search: "shirt",
  });
  const newProduct = await api.products.create({
    name: "T-Shirt",
    price: 29.99,
  });
  ```

- **React Query Hooks** (`src/react-query.ts`): Instant integration with `@tanstack/react-query` v4 & v5:

  ```tsx
  import {
    useProductList,
    useCreateProduct,
  } from "./sdk/typescript/src/react-query";

  function ProductCatalog() {
    const { data, isLoading } = useProductList(api, { page: 0, size: 20 });
    const createProduct = useCreateProduct(api);

    if (isLoading) return <div>Loading products...</div>;
    return (
      <ul>
        {data?.content.map((p) => (
          <li key={p.id}>
            {p.name} - ${p.price}
          </li>
        ))}
      </ul>
    );
  }
  ```

### 2. Instant Docker & Production Containerization

Zenith generates multi-stage container deployment out of the box:

- **`Dockerfile`**: Multi-stage build using `maven:3.9-eclipse-temurin-17` builder and an unprivileged alpine runtime container (`eclipse-temurin:17-jre-alpine`).
- **`docker-compose.yml`**: Pairs your Spring Boot service with the configured database (e.g. PostgreSQL with auto healthcheck, persistent volumes, and credentials).
- **`.dockerignore`**: Pre-configured to keep container builds fast and lightweight.

Run your entire stack with one command:

```bash
docker-compose up --build
```

---

## 🧪 Testing the Generated Project

### 1. Run the Automated Test Suite (JUnit 5 + MockMvc)

```cmd
cd generated\shopsphere
mvn test
```

All auto-generated controller security assertions, role guards, validation checks, and repository slices run automatically against the isolated H2 test profile.

### 2. Run the Application

```cmd
cd generated\shopsphere
mvn spring-boot:run
```

Available endpoints:

- `Swagger UI`: `http://localhost:8080/swagger-ui.html`
- `Auth Login`: `POST http://localhost:8080/api/v1/auth/login`
- `Auth Register`: `POST http://localhost:8080/api/v1/auth/register`
- `Products`: `GET/POST http://localhost:8080/api/v1/products`
- `H2 Console`: `http://localhost:8080/h2-console`

---

## 🗺️ Roadmap & Feature Status

All 7 foundational milestones (**Custom Request Bodies**, **Database Seeding**, **Multi-File Modular Imports**, **Advanced Actions & Notifications**, **File Uploads & Object Storage**, **Automated Test Generation**, and **Background Jobs & Cron**) are **✅ Fully Implemented**. For historical architecture breakdowns, see **[ROADMAP.md](ROADMAP.md)**.

---

## 🏢 Enterprise Battle-Testing & Next-Gen Roadmap (The Final 5–10%)

To bridge the final 5–10% gap for mission-critical microservices, distributed event streaming, and custom enterprise integration logic, consult the dedicated **[ENTERPRISE_ROADMAP.md](ENTERPRISE_ROADMAP.md)**:

1. **Extension Points & Custom Code Preservation ("Escape Hatches")**:
   - **Generation Gap Pattern**: Base abstract services (`AbstractOrderService`) regenerated on every schema update, paired with safe subclass extension services (`OrderService`) generated only once.
   - **`@custom` Endpoint Annotation**: Delegate specific complex endpoints directly to custom handwritten Spring Beans.
   - **Protected Directory Guarantees**: Any code in `src/main/java/.../custom/` or matching `*Custom*.java` is strictly preserved.
2. **Event-Driven Messaging (Kafka & RabbitMQ)**:
   - Declarative domain event contracts: `event OrderPaidEvent { ... }`
   - Native event dispatch in endpoint actions: `publish OrderPaidEvent { ... }`
   - Auto-configured `spring-kafka` / RabbitMQ templates and JSON topic serializers.
3. **Automated CI/CD Workflow Generation**:
   - Turnkey `.github/workflows/ci.yml` and `.gitlab-ci.yml` definitions generated alongside the project.
   - Automatically runs `mvn test` and builds cached multi-stage Docker images on every commit.

👉 **Full Architectural Specification**: **[ENTERPRISE_ROADMAP.md](ENTERPRISE_ROADMAP.md)**
