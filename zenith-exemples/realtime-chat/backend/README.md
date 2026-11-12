# ZenithChat

> Generated automatically by **Zenith DSL** on top of Spring Boot 3 & Java 17.

## Quick Start

### 1. Build & Run
```bash
mvn spring-boot:run
```

Server will start on port `8081`.

### 2. Interactive Swagger UI (OpenAPI 3)

- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8081/v3/api-docs`

### 3. Authentication & JWT Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Authenticate identity/password and receive JWT Bearer token |
| `POST` | `/api/v1/auth/register` | Register new account and receive JWT Bearer token |

Use the token in the `Authorization: Bearer <token>` header for protected endpoints.

### 4. Available Resource Endpoints

#### ChatRoom Resource (`/api/v1/chat_rooms`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/chat_rooms` | List ChatRooms (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/chat_rooms/{id}` | Fetch ChatRoom by ID |
| `POST` | `/api/v1/chat_rooms` | Create new ChatRoom |
| `PUT` | `/api/v1/chat_rooms/{id}` | Update existing ChatRoom |
| `DELETE` | `/api/v1/chat_rooms/{id}` | Delete ChatRoom |

#### ChatMessage Resource (`/api/v1/chat_messages`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/chat_messages` | List ChatMessages (supports `?page=0&size=20&sort=id&direction=asc&search=...`) |
| `GET` | `/api/v1/chat_messages/{id}` | Fetch ChatMessage by ID |
| `POST` | `/api/v1/chat_messages` | Create new ChatMessage |
| `PUT` | `/api/v1/chat_messages/{id}` | Update existing ChatMessage |
| `DELETE` | `/api/v1/chat_messages/{id}` | Delete ChatMessage |
| `POST` | `/api/v1/chat_messages/{id}/send` | Sends a chat message and broadcasts real-time frame via WebSockets |
| `POST` | `/api/v1/chat_messages/{id}/direct` | Sends a direct peer-to-peer message to a targeted user via WebSocket queue |

### 5. Database, Migrations & H2 Console

- **In-memory H2 DB Console**: `http://localhost:8081/h2-console`
- **JDBC URL**: `jdbc:h2:mem:appdb`
- **User**: `sa` | **Password**: (empty)

### 6. Automated Unit & Integration Tests

Zenith auto-generates comprehensive JUnit 5 & MockMvc integration tests for controllers, security rules, RBAC guards, and repositories:

```bash
mvn test
```
Tests run completely isolated against an in-memory H2 database using `src/test/resources/application.yml` without requiring external infrastructure.
