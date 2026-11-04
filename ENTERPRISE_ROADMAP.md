# Zenith DSL — Enterprise Battle-Testing & Extension Roadmap

> **Strategic Architecture Guide for the Final 5–10% Enterprise Readiness Requirements**  
> Covers **Custom Code Preservation ("Escape Hatches")**, **Event-Driven Messaging (Kafka / RabbitMQ)**, and **Automated CI/CD Pipelines**.

---

## Executive Summary

Zenith DSL currently generates **90–95% of a full-scale enterprise application**:
- Data modeling, JPA relationships, and Flyway migrations
- Spring Security 6, JWT filters, and Role-Based Access Control (RBAC)
- Declarative multi-entity actions, custom DTO mappings, and guards
- S3/MinIO file uploads and background cron workers
- Full-stack TypeScript & React Query client SDK
- Automated JUnit 5 & MockMvc integration test suites

To bridge the final **5–10% gap** for large-scale enterprise adoption and mission-critical distributed architectures, this document outlines the concrete specifications, syntax, and transpilation targets for the next architectural frontier:

```text
                            ┌────────────────────────────────────────┐
                            │        Zenith Schema AST (.zen)        │
                            └───────────────────┬────────────────────┘
                                                │
         ┌──────────────────────────────────────┼──────────────────────────────────────┐
         ▼                                      ▼                                      ▼
┌─────────────────────────────────┐   ┌─────────────────────────────────┐   ┌─────────────────────────────────┐
│  1. Custom Code Escape Hatches  │   │   2. Event-Driven Messaging     │   │   3. Automated CI/CD Pipelines  │
│  Base Services & @custom Hooks  │   │  publish Event & Kafka/RabbitMQ │   │   .github/workflows & GitLab CI │
└────────────────┬────────────────┘   └────────────────┬────────────────┘   └────────────────┬────────────────┘
                 │                                     │                                     │
                 ▼                                     ▼                                     ▼
┌─────────────────────────────────┐   ┌─────────────────────────────────┐   ┌─────────────────────────────────┐
│   Protected Custom Java Code    │   │  Kafka / RabbitMQ Topic Streams │   │  Automated Test & Docker Builds │
└─────────────────────────────────┘   └─────────────────────────────────┘   └─────────────────────────────────┘
```

---

## 1. Extension Points & Custom Code Preservation ("Escape Hatches")

### Objective
Allow developers to integrate legacy SOAP endpoints, custom cryptographic hardware (HSM), third-party partner SDKs, or bespoke business logic without having their handwritten code overwritten during `zenith build` or `zenith watch` re-compilations.

### Technical Architecture & Patterns

#### Pattern A: Base + Implementation Service Generation (Generation Gap Pattern)
Instead of generating a monolithic `OrderService.java` that gets overwritten on every schema change, Zenith splits the service layer:
1. **`AbstractOrderService.java`** (Auto-generated & always overwritten): Contains all Zenith-managed CRUD logic, transaction boundaries, guards, audit logging, and notification dispatches.
2. **`OrderService.java`** (Generated **only if missing**): Extends `AbstractOrderService`. Developers can safely override methods or add custom `@Autowired` dependencies and business functions.

```java
// src/main/java/com/app/service/base/AbstractOrderService.java (ALWAYS REGENERATED)
public abstract class AbstractOrderService {
    @Transactional
    public OrderResponse create(CreateOrderRequest request) { ... }
}

// src/main/java/com/app/service/OrderService.java (GENERATED ONCE - SAFE TO EDIT)
@Service
public class OrderService extends AbstractOrderService {
    
    @Autowired
    private LegacySapErpClient sapClient; // Custom enterprise bean

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        // Custom pre-processing hook
        sapClient.verifyCreditLimit(request.customerNumber());
        
        // Execute Zenith standard transactional creation
        OrderResponse response = super.create(request);
        
        // Custom post-processing hook
        sapClient.syncOrder(response.id());
        return response;
    }
}
```

#### Pattern B: The `@custom` Endpoint Hook
For endpoints that cannot be expressed purely in the DSL, mark the endpoint with `@custom`:

```zenith
model PaymentTransaction {
    id: UUID @id @auto
    amount: Decimal
    status: PaymentStatus

    endpoints {
        post "/{id}/process-3ds" -> PaymentResponse {
            auth: true
            @custom // Delegates implementation to handwritten Spring Bean
        }
    }
}
```

**Transpiled Output:**
Zenith emits a clean interface:
```java
public interface PaymentTransactionCustomHandler {
    PaymentResponse process3ds(UUID id, Principal currentUser);
}
```
And injects it into the controller:
```java
@PostMapping("/{id}/process-3ds")
public ResponseEntity<PaymentResponse> process3ds(@PathVariable UUID id, Principal principal) {
    return ResponseEntity.ok(customHandler.process3ds(id, principal));
}
```

#### Pattern C: Protected Directory Convention (`src/main/java/.../custom/`)
`ProjectGenerator.writeToDirectory` explicitly guarantees:
- Any file located within `src/main/java/<package>/custom/` or ending with `*Custom*.java` is **strictly immutable and never deleted or overwritten** by the compiler.

---

## 2. Event-Driven Messaging (Kafka & RabbitMQ)

### Objective
Enable asynchronous, decoupled microservice architectures by allowing Zenith action blocks to publish domain events to message brokers like Apache Kafka, RabbitMQ, or AWS SNS/SQS.

### Proposed Zenith Syntax

```zenith
config {
    app: "NexusCommerce"
    messaging: kafka // Options: kafka | rabbitmq | none (default: none)
}

// Declarative Domain Event Contract
event OrderPaidEvent {
    orderId: UUID
    orderNumber: String
    totalAmount: Decimal
    customerEmail: String
    paidAt: DateTime
}

event CustomerRegisteredEvent {
    customerId: UUID
    email: String
    fullName: String
}

model Order {
    id: UUID @id @auto
    orderNumber: String @unique
    totalAmount: Decimal
    status: OrderStatus = PENDING

    relations {
        customer: UserEntity @relation(manyToOne)
    }

    endpoints {
        post "/{id}/pay" -> OrderSummaryResponse {
            auth: true
            guard: status == OrderStatus.PENDING, "Only pending orders can be marked as paid"
            action: {
                status = OrderStatus.PAID

                // Publish strongly-typed event to message broker
                publish OrderPaidEvent {
                    orderId = entity.id,
                    orderNumber = entity.orderNumber,
                    totalAmount = entity.totalAmount,
                    customerEmail = entity.customer.email,
                    paidAt = now()
                }

                create AuditLog {
                    action = "ORDER_PAID",
                    details = "Order " + entity.orderNumber + " paid."
                }
            }
        }
    }
}
```

### Generated Spring Boot 3 Output

1. **`pom.xml` Dependency**:
   ```xml
   <dependency>
       <groupId>org.springframework.kafka</groupId>
       <artifactId>spring-kafka</artifactId>
   </dependency>
   ```

2. **Event Record (`src/main/java/com/app/event/OrderPaidEvent.java`)**:
   ```java
   package com.app.event;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;
   import java.util.UUID;

   public record OrderPaidEvent(
       UUID orderId,
       String orderNumber,
       BigDecimal totalAmount,
       String customerEmail,
       LocalDateTime paidAt
   ) {}
   ```

3. **Event Producer Bean (`src/main/java/com/app/event/EventPublisher.java`)**:
   ```java
   @Component
   public class EventPublisher {
       private final KafkaTemplate<String, Object> kafkaTemplate;

       public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
           this.kafkaTemplate = kafkaTemplate;
       }

       public void publishOrderPaid(OrderPaidEvent event) {
           kafkaTemplate.send("orders.order-paid", event.orderId().toString(), event);
       }
   }
   ```

4. **Service Integration in Action Block**:
   ```java
   eventPublisher.publishOrderPaid(new OrderPaidEvent(
       entity.getId(),
       entity.getOrderNumber(),
       entity.getTotalAmount(),
       entity.getCustomer().getEmail(),
       LocalDateTime.now()
   ));
   ```

---

## 3. Automated CI/CD Workflow Generation

### Objective
Provide instant, turnkey DevOps pipelines for GitHub Actions and GitLab CI, ensuring that every commit runs tests, validates schemas, builds containers, and prepares production artifacts without manual pipeline setup.

### Configuration in `config { ... }`

```zenith
config {
    app: "NexusCommerce"
    port: 8080
    db: postgres
    cicd: github // Options: github | gitlab | all
}
```

### Generated Pipeline Definitions

#### A. GitHub Actions (`.github/workflows/ci.yml`)
```yaml
name: Zenith Continuous Integration

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    name: Run Automated Tests
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Run Zenith Test Suites (MockMvc & JPA)
        run: mvn clean test --batch-mode

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: target/surefire-reports/

  build-docker:
    name: Build & Verify Docker Image
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build Container Image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: false
          tags: nexus-commerce:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

#### B. GitLab CI (`.gitlab-ci.yml`)
```yaml
image: maven:3.9-eclipse-temurin-17

stages:
  - test
  - build

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"

cache:
  paths:
    - .m2/repository

test:
  stage: test
  script:
    - mvn clean test
  artifacts:
    when: always
    reports:
      junit: target/surefire-reports/*.xml

docker-build:
  stage: build
  image: docker:24.0.5
  services:
    - docker:24.0.5-dind
  script:
    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA .
```

---

## 4. Future Architectural Explorations & Backlog

<details>
<summary><b>Click to expand: Under-Consideration Features (Performance, Security, Real-Time & Infra)</b></summary>
<br>

A streamlined scratchpad of candidate features under consideration for future enterprise iterations:

### 1. Database Indexes (`@index`)
- **Need**: Optimize query performance on frequently searched or filtered columns.
- **Concept**: Single or composite indexes declared on fields:
  ```zenith
  model Order {
      orderNumber: String @unique @index
      status: OrderStatus @index
      createdAt: DateTime
      // Composite index for common dashboard queries:
      @index([status, createdAt])
  }
  ```
- **Target**: Emits `CREATE INDEX idx_orders_status_created ...` in Flyway migrations and `@Index` in JPA.

### 2. Caching Layer (Redis / Spring Cache)
- **Need**: Sub-millisecond reads for high-frequency catalog and reference data.
- **Concept**: Declarative cache eviction on writes and caching on reads:
  ```zenith
  model Product {
      endpoints {
          crud {
              cache: "15m" // Emits @Cacheable on list/get, @CacheEvict on create/update/delete
          }
      }
  }
  ```
- **Target**: Auto-configured Spring Cache with Redis (`spring-boot-starter-data-redis`) or in-memory Caffeine.

### 3. API Rate Limiting (`@rateLimit`)
- **Need**: Protect sensitive endpoints (login, registration, payment checkout) from brute force or DDoS.
- **Concept**: Declarative token-bucket rules:
  ```zenith
  post "/auth/login" {
      rateLimit: "10/minute" // 10 requests per minute per IP
  }
  ```
- **Target**: Bucket4j filter or Redis-backed distributed rate limiter.

### 4. Real-Time Communication & WebSockets (`socket`)
- **Need**: Instant live push notifications to frontend dashboards without polling.
- **Concept**: Broadcast domain events directly to connected clients:
  ```zenith
  post "/{id}/pay" {
      action: {
          broadcast "/topic/orders" { orderId = entity.id, status = "PAID" }
      }
  }
  ```
- **Target**: Spring WebSocket with STOMP, paired with auto-generated TypeScript WebSocket hooks.

### 5. Nginx Reverse Proxy & Load Balancer
- **Need**: Production-ready edge routing, SSL termination, and horizontal container scaling.
- **Concept**: Auto-generated `nginx/nginx.conf` included in `docker-compose.yml`:
  - Upstream round-robin load balancing across multiple Spring Boot container replicas
  - Gzip compression, request buffering, and custom security headers (HSTS, CSP)

### 6. Observability, Health & Telemetry
- **Need**: Out-of-the-box production monitoring for DevOps teams.
- **Concept**: `config { metrics: prometheus }`
- **Target**: Pre-configured Spring Boot Actuator (`/actuator/health`, `/actuator/prometheus`) and a turnkey Grafana dashboard JSON file.

### 7. Soft Deletes & Entity Auditing (`@softDelete`, `@audited`)
- **Need**: Compliance and accidental data loss prevention.
- **Concept**:
  - `@softDelete`: Injects `deletedAt: DateTime?`, overrides DELETE to mark `deletedAt = now()`, and filters out deleted rows automatically via `@Where(clause = "deleted_at IS NULL")`.
  - `@audited`: Automatically tracks `createdBy` and `lastModifiedBy` from the current JWT principal.

### 8. API Versioning
- **Need**: Support backward-compatible mobile and third-party integrations across schema changes.
- **Concept**: Header-based (`X-API-Version: 2`) or URI-based (`/api/v2`) routing configurations.

</details>

---

## Summary Comparison: Evolution to Version 2.0

| Domain | Zenith Today (v1.0) | Enterprise Battle-Tested (v2.0) |
| :--- | :--- | :--- |
| **Custom Code** | Standard Spring Beans | Base-class generation gap + `@custom` endpoint hooks |
| **Architecture** | Synchronous REST & S3 | REST + Asynchronous Event-Driven Messaging (Kafka/RabbitMQ) |
| **CI/CD** | Manual workflow creation | Auto-generated GitHub Actions & GitLab CI pipelines |
| **Testing** | Automated MockMvc & JPA Slices | Automated Unit, Slices, and CI Test Matrix verification |
| **Deployment** | Dockerfile & docker-compose | Docker + Production Kubernetes Helm / Manifest stubs |

