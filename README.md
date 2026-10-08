# orthanc-scan-consumer

Spring Boot app (port 3001) that consumes scans published by orthanc-scan-producer from the RabbitMQ queue
bound to the `orthanc.scans` exchange, stores them, and applies them to their lots. It also serves the lot API
the producer calls before publishing (`GET /api/lots/{lotId}`), and publishes lot events for other applications
(see [Lot events for other applications](#lot-events-for-other-applications)).

## Configuration files

`src/main/resources/application.properties` holds the defaults, which are set up for a fully local run
(RabbitMQ on `localhost:5672` as `guest`). Two more files, which you create yourself in `src/main/resources/`,
override them:

| File                             | Holds                                     | Loaded                             |
|----------------------------------|-------------------------------------------|------------------------------------|
| `application-secrets.properties` | Your RabbitMQ username and password       | Always, if it exists               |
| `application-local.properties`   | Local overrides: broker host, queue       | When the `local` profile is active |

Both files are git-ignored and left out of the built jar, so they never get committed or shipped. Don't
rename them, or both protections stop applying. If a property is set in more than one place, the order is:
`application-local.properties`, then `application-secrets.properties`, then `application.properties`.
Keep credentials in the secrets file only.

### application-secrets.properties

Needed when connecting to the test broker. RabbitMQ's `guest` user can only log in from localhost:

```properties
spring.rabbitmq.username=<your-dev-user>
spring.rabbitmq.password=<your-dev-password>
```

### Running with the local profile

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

In an IDE, set **Active profiles** to `local` in the run configuration.

## Local development

orthanc-scan-producer's README describes three local modes. In mode 1 the test environment consumer processes
your scans, so you don't run this app. The other two:

### Mode 2: test environment RabbitMQ, your own queue

Create `application-secrets.properties` (above) and `application-local.properties`, using the **same** queue
and routing key as in your producer's `application-local.properties`:

```properties
spring.rabbitmq.host=<test-rabbitmq-host>
app.scans.queue=orthanc.scans.<your-name>
app.scans.routing-key=scan.created.<your-name>
app.scans.dead-letter-exchange=orthanc.scans.dlx.<your-name>
app.scans.dead-letter-queue=orthanc.scans.dlq.<your-name>
```

Then run with the `local` profile.

- **Never point a local consumer at `orthanc.scans` on the test broker.** It would compete with the test
  environment consumer and take some of its messages.
- The routing key must differ from `scan.created`, not just the queue name. Otherwise your queue also receives
  copies of everyone's test scans.
- Your RabbitMQ user needs configure, write and read permission on your queue and dead-letter queue and
  exchange. They're durable, so delete them in the RabbitMQ management UI when you no longer need them.

### Mode 3: everything local

No extra files are needed. If you have files from mode 2, comment out their lines (`#`) rather than renaming
them, since a renamed file is no longer git-ignored. If you delete them instead, run `./mvnw clean` too, or
the copies in `target/classes` keep being loaded.

Start RabbitMQ in Docker:

```bash
docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

Then start this app, then orthanc-scan-producer:

```bash
./mvnw spring-boot:run
```

The management UI is at http://localhost:15672 (guest / guest).

## Failed scans

A scan that throws while being processed is retried twice more (1s, then 2s later), each attempt in its own
transaction that's rolled back on failure. If it still fails, or the message can't be read as a scan at all,
it goes to the dead-letter queue `orthanc.scans.dlq` (through the `orthanc.scans.dlx` exchange) with the
exception message, stack trace and time. Nothing is lost and the scans queue keeps moving.

### Viewing and retrying

Open http://localhost:3001/dead-letters. It lists the failed scans, oldest first, with the lot, user, time and
reason (stack trace and raw message under each row). Once the cause is fixed:

- **Retry** sends a scan back to `orthanc.scans`; **Retry all** sends them all. Scans already stored are
  skipped, so retrying is always safe. A scan that fails again comes back to the list with the new reason.
- **Discard** (click twice) removes a scan for good, e.g. a message that will never be readable. Its body is
  logged at WARN.

The same is available as an API: `GET /api/dead-letters`, `POST /api/dead-letters/{id}/retry`,
`POST /api/dead-letters/retry`, `DELETE /api/dead-letters/{id}`. The id is the scan's `clientId`, or `sha-…`
for a message that can't be read as a scan. Each call looks at the oldest 500 messages; with more than that in
the queue, work through them in batches.

As a fallback, the RabbitMQ management UI shows the same queue: **Get messages** with *Nack message requeue
true* to look without removing. Its **Move messages** needs the shovel plugin
(`docker exec rabbitmq rabbitmq-plugins enable rabbitmq_shovel rabbitmq_shovel_management`). If you publish a
scan by hand, set the property `content_type` to `application/json`, or the consumer can't read it.

### Upgrading an existing broker

The scans queue now carries dead-letter arguments, and RabbitMQ refuses to redeclare an existing queue with
different arguments (`PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'`). Once, before starting
the new version, let the queue drain, then delete `orthanc.scans` in the management UI (Queues → orthanc.scans
→ Delete). Both apps redeclare it on startup. Messages still in the queue when it's deleted are lost. The
producer declares the same queue and must be updated at the same time.

## Lot events for other applications

Every committed change to a lot is published to the topic exchange **`orthanc.lots`**, for any number of
other applications. This app declares only the exchange: each subscriber declares and binds its own queue,
so adding a subscriber needs no change here.

### Event types

The routing key is the event type:

| Routing key              | When                                                                         |
|--------------------------|------------------------------------------------------------------------------|
| `lot.moved`              | A scan moved the lot to another stage or WIP location                        |
| `lot.scanned`            | A scan was applied but the lot stayed where it was                           |
| `lot.location-corrected` | The records had the lot at another stage than it was scanned at; it was moved there first (followed by the scan's own event) |
| `lot.scan-rejected`      | A scan wasn't applied (lot on hold, or not active)                           |
| `lot.status-changed`     | The lot's status changed                                                     |
| `lot.hold-changed`       | The lot was placed on hold or released                                       |

Bind with `lot.#` for everything, or with the specific keys you need.

### Message

JSON, with AMQP properties `messageId` (= `eventId`), `type` (= routing key), `content_type`
`application/json` and `timestamp`. Fields that don't apply to an event type are `null`.

```json
{
  "eventId": "6f1c…", "type": "lot.moved", "schemaVersion": 1, "sequence": 1042,
  "occurredAt": "2026-10-08T13:45:00Z", "lotId": "L1",
  "lot":  { "currentStage": "wafer-prep", "wipLocation": "WAFER-PREP-001", "status": "active", "onHold": false },
  "from": { "stage": "intake", "wipLocation": null },
  "to":   { "stage": "wafer-prep", "wipLocation": "WAFER-PREP-001" },
  "scan": { "clientId": "c1", "userName": "op1", "scanStage": "intake", "note": "" },
  "discrepancy": null, "rejectionReason": null, "previousStatus": null
}
```

- `lot` is the lot's state right after the event, so most subscribers never need to call back.
- `discrepancy` (`OFF_ROUTE`, `LOCATION_CORRECTED`, `LOCATION_MISMATCH_UNCONFIRMED`) is set when a move or
  correction was flagged for review; `rejectionReason` (e.g. `LOT_ON_HOLD`) on `lot.scan-rejected`;
  `previousStatus` on `lot.status-changed`. Statuses are spelled as in the lot API (`active`, `complete`, ...).
- New fields and new enum values may be added; ignore ones you don't know. A breaking change bumps
  `schemaVersion`.

### Delivery guarantees

- **Nothing committed is lost.** Events are written to the `lot_outbox_events` table in the same transaction as
  the lot change, then published from there with broker confirms. If RabbitMQ is down they wait and go out in
  order once it's back. Published rows are kept for 7 days (`app.lots.outbox.retention`).
- **At least once.** An event can occasionally arrive twice: de-duplicate on `eventId`.
- **In order** as published. If your consumer can process out of order (several consumers on one queue,
  retries), compare `sequence`: it only increases, so ignore an event for a lot whose `sequence` is lower than
  the last one you applied.
- A queue only receives events published after it was bound. To start from the current state, load
  `GET /api/lots` first, then apply events.
- The relay assumes a single instance of this app. Running several would need row locking in `OutboxRelay`.

### Subscribing (Spring Boot example)

```java
@Configuration
class LotEventsSubscription {

    @Bean
    Queue lotEventsQueue() {
        return QueueBuilder.durable("orthanc.lots.my-app").build();   // one queue per subscribing app
    }

    @Bean
    Binding lotEventsBinding(Queue lotEventsQueue) {
        return BindingBuilder.bind(lotEventsQueue).to(new TopicExchange("orthanc.lots")).with("lot.#");
    }
}

@RabbitListener(queues = "orthanc.lots.my-app")
void onLotEvent(LotEvent event) { … }   // your own record with the fields you need, plus a JSON message converter
```

Use your own queue name, and give it a dead-letter queue as this app does for scans, so an event your app
can't process isn't lost. To see events while developing, bind a scratch queue to `orthanc.lots` with `lot.#`
in the management UI and use **Get messages**.
