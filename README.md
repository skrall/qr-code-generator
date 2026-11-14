# QR Code Generator

A QR code generator that renders the code itself, so the modules, the three corner patterns and
an embedded logo can all be shaped. It is a Spring Boot service with a Vue UI, and both are served
by the same process.

- **Shaped modules** — squares, rounded squares, dots, four-point stars or connected liquid
  modules.
- **Shaped corner patterns** — square, rounded, circular, leaf, diamond or your own artwork,
  drawn separately so the outer ring, the gap and the centre stay concentric.
- **Logos** — drop in a PNG, JPEG or GIF and it is centred on a light plate. The plate's size,
  padding, roundness and colour are adjustable.
- **Gradients** — linear or radial, across the modules and optionally the corner patterns.
  Colours that would fade into the background are rejected rather than rendered.
- **Frames** — put the code on a card with a title, a caption, a border and print bleed. Text is
  set in a bundled font, so a PNG and an SVG of the same card match.
- **Background images** — drop in a PNG, JPEG or GIF behind the code. It is washed toward the
  background colour so the modules keep their contrast.
- **Every common payload** — text, URL, email, phone, SMS, WiFi, vCard, iCalendar event, geo and
  bitcoin.
- **Short links** — store a destination and encode a code that redirects to it, with an optional
  expiry, a searchable list and hit counts.
- **Sign-in** — HTTP Basic by default, or an identity provider (Okta, Entra ID), or a trusted
  reverse proxy, with an operator role for changing short links. The redirects stay public.
- **PNG and SVG** — PNG for anything that needs a bitmap, SVG for print and for embedding the
  original logo without resampling.
- **Scan check** — the preview renders the code again and reads it back, as drawn, shrunk and
  blurred, and says whether phones should read it, with hints when they might not.
- **Scannable** — the tests decode the rendered output with ZXing, including a rounded code with
  a logo and a dotted code with circular corner patterns.

## Quick start

Requires JDK 25 or newer; the wrapper downloads Maven, and the build downloads Node.

```bash
QR_CODE_PASSWORD='something-long' ./mvnw spring-boot:run
```

Open <http://localhost:8080> and sign in as `operator` with that password (the default, if you
leave it unset, is `change-me-on-deploy`). The same process serves the UI, the API and the
OpenAPI document at `/v3/api-docs`.

Or run it in Docker, with short links in H2 or Postgres:

```bash
./mvnw spring-boot:build-image
cp .env.example .env    # then set QR_CODE_PASSWORD
docker compose up
```

## Documentation

| Document | What it covers |
| --- | --- |
| [Development](docs/development.md) | Running from source, LAN addresses for phones, UI hot reload, the database, building and testing |
| [Docker](docs/docker.md) | Docker Compose with H2 or Postgres, and the GraalVM native image |
| [API](docs/api.md) | Rendering requests and every style option, the scan check, errors, short links and other endpoints |
| [Authentication](docs/authentication.md) | Basic, OIDC (Okta, Entra ID), trusted-header and no sign-in; who may do what |
| [Architecture](docs/architecture.md) | How the renderers, frames, gradients and images fit together |

Working on the code with an AI agent? See [AGENTS.md](AGENTS.md).
