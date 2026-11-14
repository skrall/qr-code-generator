# Development

Requires JDK 25 or newer. The wrapper downloads Maven and the build downloads Node into
`ui/node`, so neither has to be installed.

```bash
./mvnw spring-boot:run
```

The project pins Java 25 in `mise.toml` for anyone using [mise](https://mise.jdx.dev/): with mise
activated, `./mvnw` picks that JDK up automatically. Without it, the wrapper uses whatever `java`
is on your `PATH`. The build targets Java 25 bytecode either way, so a newer JDK works too.

Then open <http://localhost:8080> and sign in as `operator` with the password `change-me-on-deploy`.
Set your own before the app is reachable by anyone else:

```bash
QR_CODE_PASSWORD='something-long' ./mvnw spring-boot:run
```

The same process serves the UI, the API and the OpenAPI document at `/v3/api-docs`. See
[Authentication](authentication.md) for the other sign-in modes, or `APP_SECURITY_MODE=none` to turn
sign-in off on a machine nobody else can reach.

Short links live in `./data/qr-code-generator.mv.db`, created on first use. A link is only useful
if a phone can reach it, so when the service is not running on the scanner's own machine, give it
an address the phone can resolve:

```bash
APP_PUBLIC_BASE_URL=http://192.168.1.20:8080 SERVER_ADDRESS=0.0.0.0 ./mvnw spring-boot:run
```

To work on the UI with hot reload, run the backend and the Vite dev server side by side. Vite
proxies `/api` and `/v3` to the backend, so the browser still sees one origin:

```bash
# terminal 1
./mvnw spring-boot:run

# terminal 2
export PATH="$PWD/ui/node/node:$PATH"
cd ui && npm run dev
```

The dev server is at <http://localhost:5173>. Point it at a different backend with
`QR_CODE_API=http://localhost:9090 npm run dev`.

## Database

Short links have to survive a restart, so they are stored in H2 by default: a single file under
`./data`. A deployment with PostgreSQL only has to point the datasource at it:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/qr
SPRING_DATASOURCE_USERNAME=qr
SPRING_DATASOURCE_PASSWORD=secret
```

Flyway creates the schema at startup and Hibernate validates its mapping against it.


## Build and test

```bash
./mvnw verify              # Java tests, then the UI install, build and tests
./mvnw -Dskip.ui=true test # Java only
```

`verify` produces a runnable jar in `target/` with the UI inside it:

```bash
java -jar target/qr-code-generator-0.1.0-SNAPSHOT.jar
```
