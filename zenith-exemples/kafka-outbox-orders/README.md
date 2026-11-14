# Kafka + transactional outbox + consumer example

This example publishes an `OrderConfirmedEvent` through the Spring Modulith JDBC publication registry and consumes it with a typed Spring Cloud Stream handler. It demonstrates Kafka consumer groups, bounded retry/backoff, and a dead-letter topic.

> **Testing status:** This Kafka/outbox/consumer example and the Kafka/RabbitMQ streaming integrations are not yet fully tested end to end. Use it to explore the current syntax and generated output; verify broker delivery, retries, and dead-letter behavior before depending on them.

The schema's core streaming syntax is:

```zen
config {
    outbox: true
    broker: kafka
}

event OrderConfirmedEvent {
    orderId: UUID
    orderNumber: String
    confirmedAt: DateTime
}

stream OrderConfirmedStream {
    topic: "commerce.orders.confirmed.v1"
    payload: OrderConfirmedEvent
    partitions: 6
}

subscribe OrderConfirmedAuditConsumer {
    from: OrderConfirmedStream
    group: "order-audit"
    concurrency: 2
    retry {
        maxAttempts: 5
        backoff: 2s
        multiplier: 2
        maxBackoff: 30s
    }
    deadLetter: "commerce.orders.confirmed.dlq.v1"
    action {
        log payload.orderNumber
    }
}
```

For RabbitMQ, change the broker to `rabbitmq`, set a stream destination with `topic`, and omit Kafka's `partitions` property. See [the streaming guide](../../docs/native-streaming.md) for broker configuration and implementation limits.

## Build and run

From the repository root:

```powershell
java -cp target/classes dev.zenith.cli.ZenithCli validate zenith-exemples/kafka-outbox-orders/schema.zen
java -cp target/classes dev.zenith.cli.ZenithCli build zenith-exemples/kafka-outbox-orders/schema.zen -o target/kafka-outbox-orders
Set-Location target/kafka-outbox-orders
docker compose up --build
```

The generated stack starts the API and Kafka. The API connects to `kafka:29092` inside Compose; Kafka is exposed on `localhost:9092`. The example uses H2, and Spring Modulith initializes its JDBC publication registry at startup.

## Exercise the event path

Create an order:

```powershell
$order = Invoke-RestMethod -Method Post -Uri http://localhost:8092/api/v1/orders -ContentType 'application/json' -Body '{"orderNumber":"ORD-1001","customerId":"d2719f1b-8d2c-4d4d-8ef3-9e79db3b86a7","status":"PENDING"}'
$order
```

Confirm it with the returned ID:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8092/api/v1/orders/$($order.id)/confirm"
```

The endpoint updates the order and publishes `OrderConfirmedEvent` in the same application transaction. The generated Modulith listener relays the committed event to `commerce.orders.confirmed.v1`. `OrderConfirmedAuditConsumer` consumes that topic in group `order-audit`, logs the event's `orderNumber`, retries up to five attempts with exponential backoff, and sends exhausted messages to `commerce.orders.confirmed.dlq.v1`.

Delivery can repeat after failures, so consumer work should be idempotent. The outbox makes publication recoverable after the producer transaction commits; it does not combine consumer database changes and Kafka acknowledgement into one transaction.

Generated files to inspect include:

- `src/main/java/com/zenith/examples/kafkaoutbox/event/OrderConfirmedEvent.java`
- `src/main/java/com/zenith/examples/kafkaoutbox/event/OrderConfirmedStreamRelay.java`
- `src/main/java/com/zenith/examples/kafkaoutbox/event/OrderConfirmedAuditConsumer.java`
- `src/main/resources/application.yml`
- `docs/asyncapi.yaml`
