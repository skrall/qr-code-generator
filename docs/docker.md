# Docker

The image is built with [Cloud Native Buildpacks](https://buildpacks.io), so there is no
Dockerfile. `docker-compose.yml` runs it with short links in H2 or Postgres:

```bash
./mvnw spring-boot:build-image   # produces qr-code-generator:0.1.0-SNAPSHOT
cp .env.example .env             # then set QR_CODE_PASSWORD, and POSTGRES_PASSWORD for postgres
docker compose up                # runs the profile COMPOSE_PROFILES names in .env
```

| Profile    | Database                                   | Notes                                     |
| ---------- | ------------------------------------------ | ----------------------------------------- |
| `h2`       | the app's built-in H2, as a file on a volume | runs as root to write the volume        |
| `postgres` | `postgres:18`, on the compose network only | the app waits for it to report healthy    |

`.env` sets `COMPOSE_PROFILES`, so switching to Postgres is a one-line change there;
`docker compose --profile postgres up` does the same for a single run. Run one profile at a time,
because both publish the same port. Flyway creates the schema on first start either way.

Only the app is published, and only on `127.0.0.1` (port `APP_PORT`, 8080 by default); Postgres
is never reachable from the host. Data lives in a named volume per profile and survives
`down`/`up`; `docker compose --profile <name> down -v` deletes it. `QR_CODE_PASSWORD` is required,
because the built-in default is published in the README. To serve phones on your network,
publish the port beyond loopback and set `APP_PUBLIC_BASE_URL` to an address they can reach.

The app container has no healthcheck: the run image is distroless, with no shell or `curl` to
probe with. Poll `http://127.0.0.1:8080/actuator/health` from the host instead.

## Native image

A GraalVM native image starts in well under a second and runs with either profile:

```bash
./mvnw -Pnative spring-boot:build-image -Dspring-boot.build-image.imageName=qr-code-generator-native:1.0.0
QR_CODE_IMAGE=qr-code-generator-native:1.0.0 docker compose up
```

The native build uses Paketo's base run image rather than the distroless "tiny" one: framed
codes set their text with Java2D, which in a native image finds fonts only through fontconfig,
and "tiny" has none. The image is about 160 MB larger as a result. The JVM image stays on "tiny".
