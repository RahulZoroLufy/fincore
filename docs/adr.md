# ADR-0001: Use PostgreSQL as the Authoritative Write Store for Financial State

- Status: Accepted
- Date: 2026-09-14
- Decision makers: FinCore engineering
- Related: Customer, Account, Transaction, Ledger, Outbox, Idempotency

## Context

FinCore handles financial state: customers, accounts, balances, transactions, ledger entries, outbox events and idempotency records. These require strong consistency, transactional guarantees, constraints, joins, auditability and predictable failure behavior.

The platform also uses MongoDB, Elasticsearch and Redis, but those are intended for read models, search, caching and projections. They must not become the source of truth for money movement.

## Decision

We will use PostgreSQL as the authoritative write-side store for all financial aggregates and write-side operational data:

- Customer Service: customers, profiles, KYC status
- Account Service: accounts, balances, currency, account lifecycle
- Transaction Service: transactions, transaction state machine, idempotency keys
- Ledger Service: ledger transactions, ledger entries, double-entry invariants
- Outbox: outbox_events table in the same PostgreSQL database as the aggregate
- Idempotency: idempotency records in PostgreSQL, with Redis acceleration later if justified

Each service owns its PostgreSQL schema/database. Services do not share tables directly.

We will use Flyway for schema migrations, database constraints for invariants, and optimistic locking/version columns where concurrent updates are possible.

## Alternatives Considered

1. MongoDB as primary write store
    - Good flexible schema and horizontal scaling.
    - Weaker multi-document transactional guarantees and fewer relational invariants for financial correctness.
    - Rejected for authoritative financial state.

2. Event store only
    - Strong audit trail and event sourcing purity.
    - Higher operational and query complexity.
    - Too much complexity for the first version of FinCore.

3. Distributed SQL / Cassandra
    - Strong scale characteristics.
    - Higher operational burden and different consistency trade-offs.
    - Not justified for initial portfolio scope.

4. One shared database for all services
    - Simple early on.
    - Breaks service ownership and creates coupling.
    - Rejected.

## Consequences

Positive:

- ACID transactions for money movement and state changes.
- Strong constraints: foreign keys, unique indexes, check constraints.
- Mature SQL, indexing, EXPLAIN ANALYZE and migration tooling.
- Outbox pattern works naturally because business state and outbox event commit atomically.
- Easy Testcontainers-based integration testing.

Negative / Trade-offs:

- Write-side scaling requires indexing, partitioning, connection-pool tuning and possibly sharding later.
- Cross-service consistency is not a single ACID transaction; we must use Saga/event-driven coordination.
- Read models become eventually consistent.
- More migrations and schema discipline required.

## Failure Modes

- PostgreSQL unavailable: write APIs fail fast; outbox publishing pauses; read models may serve stale data.
- Connection pool exhaustion: timeouts, circuit breakers, bulkheads and metrics must expose this.
- Hot account rows: optimistic locking retries; later we may partition or redesign if needed.
- DB commit succeeds but Kafka publish fails: Outbox pattern prevents dual-write data loss.
- Duplicate client requests: Idempotency-Key handling prevents duplicate business effects.

## Follow-up Decisions

- ADR for Kafka as event backbone.
- ADR for Avro + Schema Registry.
- ADR for Outbox pattern.
- ADR for CQRS read models in MongoDB/Elasticsearch/Redis.