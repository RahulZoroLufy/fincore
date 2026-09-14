# ADR-0003: Use Avro with Schema Registry for Event Serialization

- Status: Accepted
- Date: 2026-09-14
- Decision makers: FinCore engineering
- Related: ADR-0002 (Kafka as event backbone), Transaction, Fraud, Ledger, Notification, Query

## Context

ADR-0002 established Kafka as the event backbone. We now need a serialization format and a schema management strategy for the events flowing through Kafka.

Requirements:

- Compact on-the-wire representation for high-throughput financial events
- Explicit, versioned schemas for every event type
- Safe schema evolution without breaking consumers
- Strong typing and generated Java classes for producer and consumer code
- Compatibility enforcement at publish time
- Good fit for financial data where field meaning and types must be unambiguous

Candidates: JSON (with or without schema), Avro, Protobuf, Thrift.

## Decision

We will use **Apache Avro** as the serialization format and a **Confluent-compatible Schema Registry** for schema storage and compatibility enforcement.

Key design choices:

1. **Avro for all Kafka event payloads.** JSON is only used for HTTP APIs, not for the event backbone.

2. **Schema Registry is mandatory.** Producers must register schemas before publishing. The registry enforces compatibility so that consumers on old schemas are not broken.

3. **Default compatibility mode: BACKWARD.** New schema versions may add fields with defaults and remove optional fields. Consumers using the new schema can read data written with the old schema.

4. **Subject naming: `<topic>-value`.** One subject per topic for the value payload. Keys will be simple strings (e.g., `accountId`), so `TopicNameStrategy` is sufficient.

5. **Schemas live in a dedicated Maven module: `fincore-contracts`.**
    - Contains all `.avsc` files.
    - Uses `avro-maven-plugin` to generate Java classes at build time.
    - Is a dependency of every service that produces or consumes events.
    - No duplicate schema definitions across services.

6. **Event envelope.** Every event uses the common envelope from ADR-0002. The envelope is one Avro record; the payload is another Avro record embedded as a field. Example top-level schema:

   ```json
   {
     "type": "record",
     "name": "EventEnvelope",
     "namespace": "com.fincore.contracts.common",
     "fields": [
       { "name": "eventId",       "type": "string" },
       { "name": "eventType",     "type": "string" },
       { "name": "eventVersion",  "type": "int" },
       { "name": "timestamp",     "type": { "type": "long", "logicalType": "timestamp-millis" } },
       { "name": "correlationId", "type": ["null", "string"], "default": null },
       { "name": "causationId",   "type": ["null", "string"], "default": null },
       { "name": "payload",       "type": "bytes" }
     ]
   }