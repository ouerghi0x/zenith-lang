# Native Kafka and RabbitMQ streams

> **Testing status:** Kafka and RabbitMQ streaming are not yet fully tested end to end. Treat these declarations and generated integrations as experimental, and verify broker connectivity, delivery, retries, and dead-letter behavior in your own environment before relying on them.

Zenith stream declarations describe typed broker destinations. When a project declares at least one stream, Zenith generates event records, a typed `StreamBridge` publisher for each stream, Spring Cloud Stream configuration, the selected binder dependency, and an AsyncAPI contract at `docs/asyncapi.yaml`.

## Kafka example

```zen
config {
    app: "CareService"
    package: "com.example.care"
    broker: kafka
    outbox: true
    flyway: false
}

event PatientDischargedEvent {
    patientId: UUID
    dischargedAt: DateTime
    reason: String?
}

stream PatientDischargedStream {
    topic: "healthcare.patients.discharged.v1"
    payload: PatientDischargedEvent
    partitions: 6
}
```

Set `outbox: true` to connect streaming publication to the database transaction. When an endpoint action publishes an event, Spring Modulith records the pending publication in its JDBC publication registry as part of that transaction. After commit, the relay sends the event to Kafka or RabbitMQ. This makes pending publications recoverable after a send interruption; it does not prevent duplicate delivery, so consumers should be idempotent.

When enabling the outbox, set `flyway: false` as shown. The database must still have the Spring Modulith publication registry tables before the application starts. Create them with your migration tool or database setup.

The broker may be set once in `config` or on each `stream`. Supported values are `kafka` and `rabbitmq`. A stream destination is a Kafka topic or Rabbit destination, depending on the selected binder. Only one broker type may be used in a service. Partitions are supported only with Kafka and must be positive.

Set `KAFKA_BOOTSTRAP_SERVERS` for the Kafka broker location. The generated local default is `localhost:9092`; credentials and production endpoints should come from environment variables or an external secret provider.

When a broker is configured, the generated `docker-compose.yml` includes the local broker services. Kafka projects receive ZooKeeper and Kafka with host port `9092`; the generated API container connects to `kafka:29092` on the Compose network. Start the local stack with `docker compose up --build`.

## RabbitMQ example

```zen
config {
    app: "NotificationService"
    package: "com.example.notifications"
    broker: rabbitmq
}

event AppointmentReminderEvent {
    appointmentId: UUID
    recipient: String
    scheduledAt: DateTime
}

stream AppointmentReminderStream {
    topic: "appointments.reminders.v1"
    payload: AppointmentReminderEvent
}
```

Configure RabbitMQ with `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`. The generated configuration uses local guest credentials only as a development convenience. Production deployments should provide externally managed credentials and TLS settings.

RabbitMQ projects receive the `rabbitmq:3-management` service with AMQP on port `5672` and the management UI on port `15672`. Compose uses `zenith` / `zenith_local` as local development defaults; set `RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD` in the environment to override them.

## Generated publisher

For the first example Zenith generates `PatientDischargedEvent` and `PatientDischargedStreamPublisher` in the configured package’s `event` subpackage. Application Java code can inject the publisher and call it with the typed record:

```java
publisher.publish(new PatientDischargedEvent(
    null, null, null, patientId, Instant.now(), null
));
```

The event record fills missing metadata with a UUID event ID, event type such as `PatientDischargedEvent.v1`, and occurrence timestamp. Preserve the event ID if an event is retried; consumers can use it for deduplication. A successful `StreamBridge.send` return is the adapter’s send result and is not a general guarantee of replicated broker durability.

## Publish from a transactional endpoint

The `publish StreamName(field = expression, ...)` statement is supported in model endpoint action blocks. Zenith checks the stream, payload field names, required fields, and common literal type mismatches.

```zen
event PatientDischargedEvent {
    patientId: UUID
    dischargedAt: DateTime
}

stream PatientDischargedStream {
    topic: "healthcare.patients.discharged.v1"
    payload: PatientDischargedEvent
    broker: kafka
}

model Patient {
    id: UUID @id @auto
    status: String

    endpoints {
        post "/{id}/discharge" -> Void {
            action {
                status = "DISCHARGED"
                publish PatientDischargedStream(patientId = id, dischargedAt = now())
            }
        }
    }
}
```

The action publishes a local typed application event. With `outbox: true`, Spring Modulith records its pending publication in the same database transaction as the order update, then the generated relay sends it after commit. This connects the database change and the intent to publish, so an interrupted send can be retried from the registry. The broker send is still a separate operation and may result in duplicate delivery; downstream consumers should deduplicate by `eventId`. Without outbox, the after-commit listener is best-effort and has no durable local recovery record. With `flyway: false`, create the Modulith publication registry tables through your own migration or database setup before startup. Stream publication in background job actions is currently rejected.

## Event contract rules

- Events are immutable wire contracts, not JPA entities. Use scalar and enum fields; collection fields and field annotations are rejected.
- Stream payloads must refer to a declared event. Models and DTOs are not accepted as stream payloads.
- Destination names must be non-empty and use letters, digits, dots, underscores, or hyphens.
- `docs/asyncapi.yaml` describes the event schemas and destinations. It does not register schemas with a runtime Schema Registry.
- Existing projects without stream declarations do not receive Spring Cloud Stream dependencies or broker configuration.

## Current implementation boundary

The compiler accepts `event`, `stream`, and `subscribe` declarations and generates event records, publisher/relay classes, typed Spring Cloud Function consumer beans, binder settings, retry/dead-letter settings, and AsyncAPI output. Endpoint `publish` actions relay after commit; `outbox: true` enables the Spring Modulith publication registry. Automatic broker topology provisioning is not provided. These generated integrations have not yet been fully tested end to end against Kafka and RabbitMQ, including failure and recovery scenarios. Without outbox, the after-commit relay has no durable local recovery record.

The [`kafka-outbox-orders` example](../zenith-exemples/kafka-outbox-orders/README.md) shows the complete Kafka syntax, including consumer group, retry/backoff, dead-letter destination, and transactional publishing. For RabbitMQ, set `broker: rabbitmq` and define a stream with a destination; omit Kafka-only `partitions`.

See the [implementation decisions](../private/specs/39-native-streaming-bus-kafka-rabbitmq-implementation.md) for the release scope and compatibility choices.
