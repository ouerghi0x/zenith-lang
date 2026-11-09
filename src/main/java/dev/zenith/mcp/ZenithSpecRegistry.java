package dev.zenith.mcp;

import java.util.*;

/**
 * Encapsulates the Zenith Language Specification, Supported Features, MCP Resources,
 * Example Snippets, and AI Prompts as specified in Task 25 (Spec 24).
 */
public class ZenithSpecRegistry {

    public static final String COMPILER_VERSION = "1.0.0";

    public static class FeatureInfo {
        public final String id;
        public final String name;
        public final String category;
        public final String description;
        public final String syntaxExample;

        public FeatureInfo(String id, String name, String category, String description, String syntaxExample) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.syntaxExample = syntaxExample;
        }
    }

    public static List<FeatureInfo> getFeatures() {
        List<FeatureInfo> list = new ArrayList<>();
        list.add(new FeatureInfo("config", "Global Configuration", "Core",
                "Defines application metadata, database dialect (postgres|mysql|h2), port, JWT auth, storage, rateLimit, and mail settings.",
                "config {\n  app: \"MyApp\"\n  package: \"com.app\"\n  port: 8080\n  database: postgres\n  auth: jwt\n}"));

        list.add(new FeatureInfo("models", "Domain Models & Primitives", "Core",
                "Defines entities mapped to Spring Data JPA and database tables with nullability (Type?), default values, and decorators.",
                "model User {\n  id: UUID @id @auto\n  email: String @email @unique\n  active: Boolean = true\n}"));

        list.add(new FeatureInfo("enums", "Enums & Security Roles", "Core",
                "Strongly typed enum definitions with @role support for Spring Security authorization.",
                "enum Role {\n  ADMIN @role\n  USER @role\n}"));

        list.add(new FeatureInfo("relationships", "Entity Relationships", "Domain",
                "Declares @hasMany, @belongsTo, @hasOne, and @manyToMany relationships with cascade rules and foreign keys.",
                "model Author {\n  id: Long @id @auto\n  posts: Post[] @hasMany\n}\n\nmodel Post {\n  id: Long @id @auto\n  author: Author @belongsTo\n}"));

        list.add(new FeatureInfo("crud", "Standard CRUD Endpoints", "API",
                "Declarative CRUD operations with granular pagination, sorting, filters, and role access.",
                "crud {\n  pageable: true\n  filter: [status]\n  sort: [createdAt]\n  roles: { list: [\"public\"], create: [ADMIN] }\n}"));

        list.add(new FeatureInfo("custom_endpoints", "Custom Routes & Bodies", "API",
                "Custom HTTP routes (get/post/put/patch/delete) with path variables, typed request bodies, return DTOs, and action blocks.",
                "post \"/{id}/publish\" -> PostSummaryDto {\n  roles: [ADMIN]\n  action: { ... }\n}"));

        list.add(new FeatureInfo("actions", "Domain Actions & Variables", "Business Logic",
                "Type-safe actions with 'var x = create Model { ... }' or 'var x = update Model(...)', transactional demarcation, and side effects.",
                "action: {\n  var log = create AuditLog { action = \"PUBLISH\" }\n  entity.status = Status.ACTIVE\n}"));

        list.add(new FeatureInfo("indexes", "Database Indexes", "Performance",
                "Declarative compound and unique database indexes using @index(...) or @uniqueIndex(...).",
                "model Audit {\n  id: Long @id @auto\n  tenantId: UUID\n  createdAt: DateTime\n  @index([tenantId, createdAt])\n}"));

        list.add(new FeatureInfo("rate_limit", "API Rate Limiting", "Security",
                "Token-bucket rate limiting via @rateLimit(\"10/minute\") or crud rateLimit: \"100/minute\" powered by Bucket4j.",
                "@rateLimit(\"5/minute\")\npost \"/pay\" -> Void { action: { } }"));

        list.add(new FeatureInfo("mail", "Transactional Emails", "Services",
                "Asynchronous email dispatch contracts with Thymeleaf templates and 'send MailName(args...)' in action blocks.",
                "mail OrderNotice(o: Order) {\n  to: o.customer.email\n  subject: \"Order #\" + o.id\n  template: \"emails/order.html\"\n}"));

        list.add(new FeatureInfo("jobs", "Scheduled Workers", "Worker",
                "Cron-scheduled or fixed-interval background tasks (@cron or @interval) with Spring @Scheduled execution.",
                "job CleanupJob {\n  schedule: @cron(\"0 0 * * * *\")\n  action: { ... }\n}"));

        list.add(new FeatureInfo("storage", "Object Storage (S3 / MinIO)", "Services",
                "Multipart file/image upload decorators (@file / @image) with size, MIME constraints, and streaming endpoints.",
                "avatar: String? @image(maxSize = \"5MB\", types = [\"image/jpeg\", \"image/png\"])"));

        list.add(new FeatureInfo("seeds", "Declarative Seeding", "Database",
                "Type-safe initial database seeds with foreign-key references and automatic password hashing.",
                "seed User {\n  email: \"admin@domain.com\"\n  password: \"Admin123!\"\n}"));

        return list;
    }

    public static String getFullSpecification() {
        return "# Zenith DSL Language Specification (v1.0.0)\n\n"
                + "Zenith is a domain-specific language that compiles directly to production-grade Spring Boot 3 & Java 17 applications.\n\n"
                + "## 1. Top-Level Declarations\n"
                + "A `.zen` file or multi-file project contains the following top-level declarations:\n"
                + "- `config { ... }`: Global settings (database, auth, port, basePath, storage, rateLimit, mail).\n"
                + "- `model Name { ... }`: Database entities and their REST endpoints.\n"
                + "- `enum Name { ... }`: Enumerations with optional `@role` annotation.\n"
                + "- `dto Name { ... }`: Data transfer objects for request/response payloads.\n"
                + "- `mail Name(params) { ... }`: Asynchronous transactional email contracts.\n"
                + "- `job Name { ... }`: Scheduled cron workers.\n"
                + "- `seed ModelName { ... }`: Initial database seed records.\n"
                + "- `import \"./path.zen\"`: Modular file imports.\n\n"
                + "## 2. Primitive Types\n"
                + "- `String`: Text column (VARCHAR / TEXT). Decorators: `@email`, `@unique`, `@password`, `@min(n)`, `@max(n)`, `@regex(\"...\")`.\n"
                + "- `Int`, `Integer`: 32-bit signed integer.\n"
                + "- `Long`: 64-bit signed integer. Ideal with `@id @auto`.\n"
                + "- `Double`, `Float`: IEEE floating point numbers.\n"
                + "- `Decimal(precision, scale)`: Fixed-precision decimal (BigDecimal).\n"
                + "- `Boolean`: Boolean true/false.\n"
                + "- `DateTime`, `Date`: Temporal types (Instant / LocalDate).\n"
                + "- `UUID`: Universally unique identifier. Ideal with `@id @auto`.\n"
                + "- `Json`: JSON column type (PostgreSQL jsonb / JSON string).\n"
                + "- `Type?`: Optional (nullable) field. Non-null by default.\n"
                + "- `Type = defaultValue`: Default field value.\n\n"
                + "## 3. Decorators & Annotations\n"
                + "- `@id`: Primary key marker.\n"
                + "- `@auto`: Database auto-increment / UUID generation strategy.\n"
                + "- `@unique`: Unique column constraint.\n"
                + "- `@uniqueIndex([...])`: Compound unique database index.\n"
                + "- `@index([...])`: Compound or single database index.\n"
                + "- `@email`: Validates email format via Jakarta Validation.\n"
                + "- `@password`: BCrypt hashed field, omitted from standard JSON response DTOs.\n"
                + "- `@role`: Declares security role in enums.\n"
                + "- `@file(...)`: S3/MinIO file upload handler with maxSize and allowed types.\n"
                + "- `@image(...)`: S3/MinIO image upload handler with image MIME validations.\n"
                + "- `@rateLimit(\"cap/unit\")`: Bucket4j token-bucket limit.\n\n"
                + "## 4. Endpoints & Actions\n"
                + "- `crud { ... }`: Generates GET, GET /{id}, POST, PUT /{id}, DELETE /{id}.\n"
                + "- Custom routes: `get \"/path\" -> ReturnType { ... }` or `post \"/path\" (body: Dto) -> ReturnType { ... }`.\n"
                + "- Role access: `roles: [ADMIN, STAFF]` or `auth: false` for public endpoints.\n"
                + "- `var x = create TargetModel { field = val }`: Action variable binding with Spring Data persistence.\n"
                + "- `var x = update TargetModel(id) { field = val }`: Updates and re-saves target entity.\n"
                + "- `send MailContract(args...)`: Dispatches asynchronous transactional email.\n";
    }

    public static Map<String, String> getExamples() {
        Map<String, String> examples = new LinkedHashMap<>();

        examples.put("basic-crud", "config {\n"
                + "    app: \"TaskManager\"\n"
                + "    package: \"com.example.tasks\"\n"
                + "    port: 8080\n"
                + "    database: postgres\n"
                + "}\n\n"
                + "enum TaskStatus {\n"
                + "    TODO\n"
                + "    IN_PROGRESS\n"
                + "    DONE\n"
                + "}\n\n"
                + "model Task {\n"
                + "    id: Long @id @auto\n"
                + "    title: String\n"
                + "    description: String?\n"
                + "    status: TaskStatus = TODO\n"
                + "    createdAt: DateTime\n\n"
                + "    endpoints {\n"
                + "        crud {\n"
                + "            pageable: true\n"
                + "            filter: [status]\n"
                + "            sort: [createdAt]\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        examples.put("authentication", "config {\n"
                + "    app: \"AuthService\"\n"
                + "    package: \"com.example.auth\"\n"
                + "    database: postgres\n"
                + "    auth: jwt\n"
                + "    jwtSecret: \"dev-jwt-secret-key-32-chars-long!\"\n"
                + "    jwtExpiration: 24h\n"
                + "}\n\n"
                + "enum Role {\n"
                + "    ADMIN @role\n"
                + "    CUSTOMER @role\n"
                + "}\n\n"
                + "model User {\n"
                + "    id: UUID @id @auto\n"
                + "    email: String @email @unique\n"
                + "    password: String @password\n"
                + "    role: Role = CUSTOMER\n\n"
                + "    endpoints {\n"
                + "        crud {\n"
                + "            roles: {\n"
                + "                list: [ADMIN]\n"
                + "                delete: [ADMIN]\n"
                + "                create: [\"public\"]\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        examples.put("relationships", "config {\n"
                + "    app: \"BlogSystem\"\n"
                + "    package: \"com.example.blog\"\n"
                + "    database: postgres\n"
                + "}\n\n"
                + "model Author {\n"
                + "    id: Long @id @auto\n"
                + "    name: String\n"
                + "    articles: Article[] @hasMany\n\n"
                + "    endpoints {\n"
                + "        crud {\n"
                + "            pageable: true\n"
                + "        }\n"
                + "    }\n"
                + "}\n\n"
                + "model Article {\n"
                + "    id: Long @id @auto\n"
                + "    title: String\n"
                + "    slug: String @unique\n"
                + "    author: Author @belongsTo\n"
                + "    tags: Tag[] @manyToMany\n\n"
                + "    endpoints {\n"
                + "        crud {\n"
                + "            pageable: true\n"
                + "        }\n"
                + "    }\n"
                + "}\n\n"
                + "model Tag {\n"
                + "    id: Long @id @auto\n"
                + "    name: String @unique\n"
                + "    articles: Article[] @manyToMany\n"
                + "}\n");

        examples.put("rate-limiting", "config {\n"
                + "    app: \"PaymentGateway\"\n"
                + "    package: \"com.example.pay\"\n"
                + "    database: postgres\n"
                + "    rateLimit: \"500/minute\"\n"
                + "}\n\n"
                + "model Payment {\n"
                + "    id: UUID @id @auto\n"
                + "    amount: Decimal(10, 2)\n\n"
                + "    endpoints {\n"
                + "        @rateLimit(\"5/minute\")\n"
                + "        post \"/process\" -> Void {\n"
                + "            action: { }\n"
                + "        }\n"
                + "        crud {\n"
                + "            rateLimit: \"100/minute\"\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        examples.put("emails", "config {\n"
                + "    app: \"OrderStore\"\n"
                + "    package: \"com.example.store\"\n"
                + "    database: postgres\n"
                + "    mail: console\n"
                + "    mailFrom: \"noreply@store.com\"\n"
                + "}\n\n"
                + "mail OrderPlaced(order: Order) {\n"
                + "    to: order.customerEmail\n"
                + "    subject: \"Order #\" + order.id + \" Received\"\n"
                + "    template: \"emails/order.html\"\n"
                + "}\n\n"
                + "model Order {\n"
                + "    id: Long @id @auto\n"
                + "    customerEmail: String @email\n"
                + "    total: Decimal(10, 2)\n\n"
                + "    endpoints {\n"
                + "        post \"/{id}/confirm\" -> Void {\n"
                + "            action: {\n"
                + "                send OrderPlaced(order = entity)\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        return examples;
    }

    public static Map<String, String> getResources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("zenith://specification/syntax", getFullSpecification());
        map.put("zenith://specification/types",
                "# Zenith Data Types\n\n- String\n- Int / Integer\n- Long\n- Double / Float\n- Decimal(p, s)\n- Boolean\n- DateTime\n- Date\n- UUID\n- Json\n- Type? (Optional/Nullable)\n- Type = defaultValue\n");
        map.put("zenith://specification/annotations",
                "# Supported Annotations & Decorators\n\n- @id: Primary key\n- @auto: Auto-generation strategy\n- @unique: Unique column\n- @email: Valid email constraint\n- @password: BCrypt hashed attribute\n- @role: Enum security role\n- @index([...]): Compound database index\n- @uniqueIndex([...]): Compound unique database index\n- @hasMany, @belongsTo, @hasOne, @manyToMany: Relationship navigation\n- @file(maxSize=\"...\", types=[...], path=\"...\"): S3/MinIO file upload\n- @image(maxSize=\"...\", types=[...], path=\"...\"): S3/MinIO image upload\n- @rateLimit(\"req/unit\"): Token-bucket rate limiting\n- @cron(\"cron-expr\"): Scheduled cron worker\n");
        map.put("zenith://specification/configuration",
                "# Zenith Global Configuration\n\nDeclared with `config { ... }` at project root:\n- app: String\n- package: String\n- basePath: String (e.g. \"/api/v1\")\n- port: Integer (default 8080)\n- database: postgres | mysql | h2\n- auth: jwt | none\n- jwtSecret: String (passphrase or 64-hex)\n- jwtExpiration: Duration (e.g. 24h, 30m, 86400000)\n- storage: minio | s3\n- storageBucket: String\n- flyway: Boolean\n- swagger: Boolean\n- rateLimit: String (e.g. \"500/minute\")\n- mail: smtp | sendgrid | ses | console\n- mailFrom: String\n");
        map.put("zenith://specification/models",
                "# Zenith Models & Fields\n\nModels declare database entities mapped to Spring Data JPA repositories.\nFields are non-null by default. Use '?' for nullable attributes.\nEnd with an 'endpoints { ... }' block to expose REST APIs.\n");
        map.put("zenith://specification/relationships",
                "# Entity Relationships\n\n- @belongsTo: Foreign key relationship pointing to parent\n- @hasMany: One-to-many relationship collection\n- @hasOne: One-to-one relationship\n- @manyToMany: Join table many-to-many relationship\n");
        map.put("zenith://specification/api",
                "# API & Endpoint Declarations\n\nInside `model M { endpoints { ... } }`:\n- `crud { pageable: true, filter: [...], sort: [...], roles: { ... } }`\n- `get|post|put|patch|delete \"/path\" (params) -> ReturnType { roles: [...], action: { ... } }`\n");
        map.put("zenith://specification/authentication",
                "# Authentication & Role-Based Access Control\n\n- Set `auth: jwt` in `config`.\n- Mark roles with `@role` on an enum.\n- In custom endpoints: `roles: [ROLE1, ROLE2]` or scalar `roles: ROLE1`.\n- For anonymous endpoints: `auth: false`.\n- Anti-patterns rejected by compiler: `roles: public` -> use `auth: false`; `roles: authenticated` -> use `auth: true`.\n");

        for (Map.Entry<String, String> entry : getExamples().entrySet()) {
            map.put("zenith://examples/" + entry.getKey(), entry.getValue());
        }
        return map;
    }

    public static Map<String, String> getPrompts() {
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("create_crud_project",
                "Guide the AI coding agent to create a complete Zenith CRUD application.\n"
                + "Steps:\n"
                + "1. Define a `config { app: ..., package: ..., database: postgres }` block.\n"
                + "2. Declare enums with `@role` if authorization is required.\n"
                + "3. Create domain `model` declarations with `@id @auto` primary keys.\n"
                + "4. Add an `endpoints { crud { pageable: true } }` block to expose REST APIs.\n"
                + "5. Validate using `zenith_validate` tool before building.");

        prompts.put("create_authentication_system",
                "Guide the agent to generate an end-to-end JWT authenticated system in Zenith.\n"
                + "Steps:\n"
                + "1. Set `auth: jwt`, `jwtSecret: \"...\"`, and `jwtExpiration: 24h` in `config`.\n"
                + "2. Declare a `Role` enum with `@role` annotations.\n"
                + "3. Create a `User` model with `email: String @email @unique` and `password: String @password`.\n"
                + "4. Restrict endpoints using `roles: [ADMIN]` or allow public registration with `auth: false`.");

        prompts.put("debug_zenith_project",
                "Guide the agent through diagnosing and fixing compiler errors.\n"
                + "Steps:\n"
                + "1. Run `zenith_validate` to obtain structured diagnostics.\n"
                + "2. Pass the diagnostic error message to `zenith_explain_error`.\n"
                + "3. Follow the suggested correction and update the `.zen` source.\n"
                + "4. Re-validate to ensure zero diagnostic errors.");

        prompts.put("extend_existing_project",
                "Guide the agent to safely extend an existing Zenith project without breaking existing schema.\n"
                + "Steps:\n"
                + "1. Inspect current project structure via `zenith_get_project_info`.\n"
                + "2. Add modular `.zen` file or extend existing models.\n"
                + "3. Verify relationship foreign keys (`@belongsTo` / `@hasMany`).\n"
                + "4. Run `zenith_validate` and `zenith_build`.");

        return prompts;
    }
}
