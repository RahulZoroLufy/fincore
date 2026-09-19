# ADR-0002: Use Apache Kafka as the Event Backbone

- Status: Accepted
- Date: 2026-09-14
- Decision makers: FinCore engineering
- Related: Transaction, Fraud, Ledger, Notification, Query, Outbox, Saga, CQRS

## Context

FinCore is composed of independently deployable services (Customer, Account, Transaction, Ledger, Fraud, Notification, Query). These services must react to state changes in each other without tight synchronous coupling.

The core transaction flow is:

POST transaction -> Transaction Service -> validation + idempotency -> PostgreSQL transaction + Outbox event -> Kafka -> Fraud + Ledger + Notification -> Fraud decision -> transaction state update -> CQRS read model -> MongoDB/Elasticsearch/Redis.

We need an event backbone that supports:

- Durable, ordered, replayable event delivery
- Multiple independent consumers (fan-out)
- Partitioned parallelism for horizontal scaling
- Backpressure handling via consumer lag visibility
- Retention and replay for read-model rebuilds and DLQ recovery
- Schema evolution with a registry
- Idempotent consumers and at-least-once delivery semantics

## Decision

We will use **Apache Kafka** as the asynchronous event backbone for FinCore.

Key design choices:

1. **Event-driven communication between services.** Services publish and consume domain events instead of calling each other synchronously for state propagation.

2. **Event envelope.** Every event uses a common envelope:
    - `eventId` (UUID, unique per event)
    - `eventType` (e.g., `TransactionCreated`)
    - `eventVersion` (schema version, integer)
    - `timestamp` (event creation time, UTC)
    - `correlationId` (traces a business flow across services)
    - `causationId` (the eventId that caused this event)
    - `payload` (Avro-encoded domain data)

3. **Partition key.** For transaction-related topics, the partition key is `accountId` (or `transactionId` when strict per-transaction ordering is required). This guarantees ordering per account/transaction while allowing parallelism across keys.

4. **Consumer groups.** Each service uses its own consumer group so it independently consumes the full stream:
    - `fraud-service`
    - `ledger-service`
    - `notification-service`
    - `query-service`

5. **Delivery semantics.** At-least-once delivery. Consumers must be idempotent. Exactly-once business effect is achieved through Idempotency-Key (API level) and outbox + dedup on the consumer side.

6. **Topics.** Initial topics:
    - `transaction.created`
    - `transaction.updated`
    - `transaction.cancelled`
    - `fraud.decision`
    - `ledger.posted`
    - `notification.sent`
    - `*.retry` and `*.dlq` per consumer group where needed

7. **Outbox pattern.** Events are never published directly from business code. They are written to an `outbox_events` table in the same PostgreSQL transaction as the aggregate change, then a publisher reads and forwards them to Kafka. This avoids the dual-write problem.

8. **Schema Registry.** Kafka is paired with a Schema Registry (Confluent-compatible). We will use Avro (see ADR-0003) with `BACKWARD` compatibility by default.

9. **Retention.** Default retention is 7 days for regular topics. Compacted topics may be used later for state stores (e.g., latest account state) if justified.

## Alternatives Considered

1. **Synchronous REST between services**
    - Simple at first.
    - Creates runtime coupling, cascading failures and hard-to-trace latency.
    - Rejected for cross-service state propagation.

2. **RabbitMQ**
    - Mature, good routing.
    - Lower throughput ceiling and weaker replay/retention model for large-scale event streams.
    - Rejected as the backbone; still a valid choice for task queues.

3. **AWS SNS/SQS, Google Pub/Sub, Azure Service Bus**
    - Managed and operationally simple.
    - Cloud-vendor lock-in; less control over partition/offset semantics.
    - Rejected for a self-contained portfolio project.

4. **Pulsar**
    - Strong multi-tenancy and tiered storage.
    - Smaller ecosystem for Spring/Kafka tooling; less common in interviews.
    - Rejected in favor of Kafka's ubiquity and maturity.

5. **No event backbone; use DB polling**
    - Simplest possible.
    - Poor latency, poor fan-out, no replay model.
    - Rejected.

## Consequences

Positive:

- Decoupled services; producers do not know consumers.
- Natural fan-out: Fraud, Ledger, Notification and Query consume the same event independently.
- Replayable events enable read-model rebuild and DLQ recovery.
- Partitioned parallelism scales horizontally.
- Kafka is industry-standard for interviews and system design discussions.
- Pairs cleanly with Outbox, Saga (choreography) and CQRS.

Negative / Trade-offs:

- Operational complexity: brokers, partitions, replication, Schema Registry, monitoring.
- Eventual consistency between write side and read side.
- At-least-once delivery requires idempotent consumers everywhere.
- Ordering is only guaranteed per partition key, not globally.
- Local dev requires Docker Compose with Kafka + Schema Registry (or Redpanda as a drop-in).

## Failure Modes

- **Kafka unavailable:** outbox publisher pauses; business writes continue; backlog accumulates in `outbox_events`.
- **Consumer crash:** offsets not committed; consumer restarts and reprocesses; idempotency prevents duplicate side effects.
- **Poison message:** routed to retry topic, then DLQ; DLQ inspector and replay flow required.
- **Slow consumer:** consumer lag grows; alerting via Prometheus + Kafka exporter; scale consumers or partitions.
- **Schema break:** Schema Registry rejects incompatible schema; CI catches it before deploy.
- **Partition skew / hot key:** one account dominates a partition; monitor and rebalance keys if needed.
- **Duplicate events:** dedup by `eventId` in consumer-side processed-events store.

## Follow-up Decisions

- ADR-0003: Avro + Schema Registry.
- ADR for Outbox pattern implementation.
- ADR for Saga (choreography vs orchestration).
- ADR for CQRS read models and rebuild strategy.