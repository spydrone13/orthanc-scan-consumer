# orthanc-scan-consumer

Spring Boot app (port 3001) that consumes scans published by orthanc-scan-producer from the RabbitMQ queue
bound to the `orthanc.scans` exchange, stores them, and applies them to their lots. It also serves the lot API
the producer calls before publishing (`GET /api/lots/{lotId}`).

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
it's rejected and RabbitMQ moves it to the dead-letter queue `orthanc.scans.dlq` (through the
`orthanc.scans.dlx` exchange). Nothing is lost and the scans queue keeps moving.

In the management UI, open the dead-letter queue and use **Get messages** to see a failed scan; its `x-death`
header records the queue it came from and when. To replay scans once the cause is fixed, use **Move messages**
(shovel plugin) to send them back to `orthanc.scans`. Scans already stored are skipped, so replaying is safe.

### Upgrading an existing broker

The scans queue now carries dead-letter arguments, and RabbitMQ refuses to redeclare an existing queue with
different arguments (`PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'`). Once, before starting
the new version, let the queue drain, then delete `orthanc.scans` in the management UI (Queues → orthanc.scans
→ Delete). Both apps redeclare it on startup. Messages still in the queue when it's deleted are lost. The
producer declares the same queue and must be updated at the same time.
