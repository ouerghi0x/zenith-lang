# ShopSphere

> Generated automatically by **Zenith DSL** on top of Spring Boot 3 & Java 17.

## Quick Start

### 1. Build & Run
```bash
mvn spring-boot:run
```

Server will start on port `8080`.

### 2. Interactive Swagger UI (OpenAPI 3)

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`

### 3. Authentication & JWT Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Authenticate identity/password and receive JWT Bearer token |
| `POST` | `/api/v1/auth/register` | Register new account and receive JWT Bearer token |

Use the token in the `Authorization: Bearer <token>` header for protected endpoints.

### 4. Available Resource Endpoints

#### Category Resource (`/api/v1/categories`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/categories` | List Categorys (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/categories/{id}` | Fetch Category by ID |
| `POST` | `/api/v1/categories` | Create new Category |
| `PUT` | `/api/v1/categories/{id}` | Update existing Category |
| `DELETE` | `/api/v1/categories/{id}` | Delete Category |

#### Product Resource (`/api/v1/products`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/products` | List Products (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/products/{id}` | Fetch Product by ID |
| `POST` | `/api/v1/products` | Create new Product |
| `PUT` | `/api/v1/products/{id}` | Update existing Product |
| `DELETE` | `/api/v1/products/{id}` | Delete Product |
| `POST` | `/api/v1/products/{id}/archive` | Archives a product from the catalog |

#### Customer Resource (`/api/v1/customers`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/customers` | List Customers (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/customers/{id}` | Fetch Customer by ID |
| `POST` | `/api/v1/customers` | Create new Customer |
| `PUT` | `/api/v1/customers/{id}` | Update existing Customer |
| `DELETE` | `/api/v1/customers/{id}` | Delete Customer |

#### Order Resource (`/api/v1/orders`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/orders` | List Orders (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/orders/{id}` | Fetch Order by ID |
| `POST` | `/api/v1/orders` | Create new Order |
| `PUT` | `/api/v1/orders/{id}` | Update existing Order |
| `DELETE` | `/api/v1/orders/{id}` | Delete Order |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancels an order and releases reserved inventory |
| `POST` | `/api/v1/orders/{id}/ship` | Marks an order as shipped |

### 5. Database, Migrations & H2 Console

- **Flyway Migration DDL**: Located at `src/main/resources/db/migration/V1__initial_schema.sql`
- **In-memory H2 DB Console**: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:appdb`
- **User**: `sa` | **Password**: (empty)
