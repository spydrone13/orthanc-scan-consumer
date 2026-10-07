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
```

Then run with the `local` profile.

- **Never point a local consumer at `orthanc.scans` on the test broker.** It would compete with the test
  environment consumer and take some of its messages.
- The routing key must differ from `scan.created`, not just the queue name. Otherwise your queue also receives
  copies of everyone's test scans.
- Your RabbitMQ user needs configure, write and read permission on your queue. It's durable, so delete it in
  the RabbitMQ management UI when you no longer need it.

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
