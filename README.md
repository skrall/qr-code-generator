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
- **PNG and SVG** — PNG for anything that needs a bitmap, SVG for print and for embedding the
  original logo without resampling.
- **Scan check** — the preview renders the code again and reads it back, as drawn, shrunk and
  blurred, and says whether phones should read it, with hints when they might not.
- **Scannable** — the tests decode the rendered output with ZXing, including a rounded code with
  a logo and a dotted code with circular corner patterns.

## Running it

Requires JDK 25 or newer. The wrapper downloads Maven and the build downloads Node into
`ui/node`, so neither has to be installed.

```bash
./mvnw spring-boot:run
```

The project pins Java 25 in `mise.toml` for anyone using [mise](https://mise.jdx.dev/): with mise
activated, `./mvnw` picks that JDK up automatically. Without it, the wrapper uses whatever `java`
is on your `PATH`. The build targets Java 25 bytecode either way, so a newer JDK works too.

Then open <http://localhost:8080>. The same process serves the UI, the API and the OpenAPI
document at `/v3/api-docs`.

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

### Build and test

```bash
./mvnw verify              # Java tests, then the UI install, build and tests
./mvnw -Dskip.ui=true test # Java only
```

`verify` produces a runnable jar in `target/` with the UI inside it:

```bash
java -jar target/qr-code-generator-0.1.0-SNAPSHOT.jar
```

## Using the API

The API renders an image; the caller supplies the payload string. That keeps the server
independent of the difference between a URL, a vCard and a WiFi network — the UI composes those,
and so can any other client.

```bash
curl -X POST 'http://localhost:8080/api/qr-codes?format=png' \
  -H 'Content-Type: application/json' \
  -d '{
    "data": "https://example.com",
    "style": {
      "size": 640,
      "margin": 4,
      "errorCorrection": "QUARTILE",
      "foreground": "#111827",
      "background": "#ffffff",
      "moduleShape": "ROUNDED",
      "finderShape": "ROUNDED",
      "finderColor": "#3158c4"
    }
  }' --output qr.png
```

`format` is `png` (the default) or `svg`, in either case. The response is the image with
`Content-Type: image/png` or `image/svg+xml`.

### Style

Every field is optional. An absent style, or an absent field, falls back to a plain
black-on-white code.

| Field             | Values                        | Default      |
| ----------------- | ----------------------------- | ------------ |
| `size`            | 128–4096, in pixels           | 512          |
| `margin`          | 0–16, in modules              | 4            |
| `errorCorrection` | `LOW`, `MEDIUM`, `QUARTILE`, `HIGH` | `MEDIUM` |
| `foreground`      | `#rrggbb`                     | `#000000`    |
| `gradient`        | see below; replaces `foreground` for the modules | none |
| `background`      | `#rrggbb`                     | `#ffffff`    |
| `backgroundImage` | an image data URL              | none         |
| `moduleShape`     | `SQUARE`, `ROUNDED`, `DOTS`, `STAR`, `LIQUID` | `SQUARE` |
| `finderShape`     | `SQUARE`, `ROUNDED`, `CIRCLE`, `LEAF`, `DIAMOND`, `CUSTOM` | `SQUARE` |
| `finderColor`     | `#rrggbb`                     | `foreground` |
| `finderArtwork`   | an image data URL, used when `finderShape` is `CUSTOM` | none |
| `frame`           | see below; a card around the code              | none |

### Logo

A logo travels as a base64 data URL inside the request. Nothing is stored, so a request carries
everything needed to reproduce its output.

```json
{
  "data": "https://example.com",
  "style": {
    "errorCorrection": "HIGH",
    "logo": {
      "dataUrl": "data:image/png;base64,iVBORw0KGgo...",
      "sizePercent": 20,
      "paddingPercent": 8,
      "radiusPercent": 25,
      "background": "#ffffff"
    }
  }
}
```

| Field            | Values                | Default   |
| ---------------- | --------------------- | --------- |
| `dataUrl`        | `data:image/…;base64,…`, at most 3 MB of text | required |
| `sizePercent`    | 5–35, of the image width | 20     |
| `paddingPercent` | 0–30, of the logo width | 8        |
| `radiusPercent`  | 0–50, of the plate width | 25      |
| `background`     | `#rrggbb`             | `#ffffff` |

PNG, JPEG and GIF are accepted, up to 2 MB decoded, 4096 px on either side and 16 megapixels in
total. A logo hides part of the code, so `HIGH` error correction is the sensible companion; the UI
offers to switch when one is added.

### Finder artwork

Set `finderShape` to `CUSTOM` and send `finderArtwork` to fill the three finder patterns with an
image. The standard ring and centre stay in place and the one-module gap is left in the background
colour, so the code keeps the 1:1:3:1:1 structure a reader looks for; only the colour inside the
dark rings comes from the image. `finderColor` is the fallback for transparent pixels.

```json
{
  "data": "https://example.com",
  "style": {
    "finderShape": "CUSTOM",
    "finderArtwork": {
      "dataUrl": "data:image/png;base64,iVBORw0KGgo..."
    }
  }
}
```

The same PNG, JPEG and GIF limits as a logo apply. Artwork is adjusted so its luminance stays at
least 96/255 away from the background colour, which keeps the rings readable even when the image
is mostly light. `CUSTOM` without artwork falls back to a square eye.

### Background image

Send `backgroundImage` to draw an image behind the whole code. The image is cropped to a square
and stretched to the output size; the modules and the quiet zone are drawn over it.

```json
{
  "data": "https://example.com",
  "style": {
    "backgroundImage": {
      "dataUrl": "data:image/png;base64,iVBORw0KGgo..."
    }
  }
}
```

The same PNG, JPEG and GIF limits as a logo apply. Because an arbitrary image can be darker than
the modules, every pixel is blended toward the `background` colour until it is at least 96/255
away from the luminance of everything drawn on top: the foreground (or both gradient stops), the
`finderColor` and any finder artwork. A dark photograph therefore becomes a pale wash, and a
bright one keeps most of its colour. A light finder colour or light artwork raises the bar, so
less of the image shows. Transparent pixels become the background colour. The SVG renderer embeds
the washed PNG, so both formats show the same pixels.

### Gradient

Send `gradient` to colour the modules with a two-colour gradient instead of `foreground`. It
spans the whole image, so the colour flows across the code rather than repeating in each module.

```json
{
  "data": "https://example.com",
  "style": {
    "gradient": {
      "type": "LINEAR",
      "start": "#7c3aed",
      "end": "#0f766e",
      "angle": 135,
      "finders": true
    }
  }
}
```

| Field     | Values                                         | Default  |
| --------- | ---------------------------------------------- | -------- |
| `type`    | `LINEAR`, `RADIAL`                             | `LINEAR` |
| `start`   | `#rrggbb`, the start of the line or the centre | required |
| `end`     | `#rrggbb`, the end of the line or the corners  | required |
| `angle`   | 0–360, as in CSS: 0 points up, 90 right        | 180      |
| `finders` | whether the corner patterns use it too         | `false`  |

A linear gradient runs through the centre at `angle`, just long enough that the furthest corners
get the pure stop colours, as a CSS `linear-gradient` does. A radial one runs from the centre to
the corners. With `finders` false the corner patterns keep `finderColor`; a `CUSTOM` finder always
uses its artwork.

A gradient that fades into the background would erase whichever corner of the code it fades
into, so the request is rejected with a 400 when:

- either stop is less than 96/255 luminance away from `background`, or
- one stop is darker than the background and the other lighter, so the blend passes through it.

Checking the two stops is enough. Both formats blend them linearly in sRGB, and luminance is a
linear function of the sRGB channels, so every colour along the gradient lies between the stops.

### Frame

Send `frame` to put the code on a card. `size` is still the code's side: the frame makes the image
larger rather than the code smaller.

```json
{
  "data": "https://example.com/menu",
  "style": {
    "size": 600,
    "frame": {
      "title": "Scan me",
      "caption": "Menu, hours and reservations",
      "borderPercent": 2,
      "bleedPercent": 5
    }
  }
}
```

| Field                | Values                                        | Default            |
| -------------------- | --------------------------------------------- | ------------------ |
| `title`              | up to 60 characters, bold, above the code     | none               |
| `caption`            | up to 120 characters, below the code          | none               |
| `background`         | `#rrggbb`, the card and the bleed             | style `background` |
| `textColor`          | `#rrggbb`                                     | the module colour  |
| `borderColor`        | `#rrggbb`                                     | `textColor`        |
| `titleSizePercent`   | 3–20                                          | 9                  |
| `captionSizePercent` | 2–15                                          | 5                  |
| `borderPercent`      | 0–10, 0 for no border                         | 2                  |
| `radiusPercent`      | 0–25, the border's corner radius              | 4                  |
| `paddingPercent`     | 0–30, between the border and the code         | 6                  |
| `marginPercent`      | 0–20, between the trim line and the border    | 0                  |
| `bleedPercent`       | 0–20, card colour beyond the trim line        | 0                  |

Every length is a percentage of the code's side, so a card keeps its proportions at any `size`.
From the outside in: bleed, margin, border, padding, then the code, following CSS's box model with
bleed outside it. For print, bleed is the part a printer trims off. As an example, a 50 mm code
with a 3 mm bleed needs `bleedPercent` 6. Give the border some `marginPercent` too, so an uneven
cut does not thin one side of it.

Text wraps to the code's width and is centred on it; a newline starts a new line. It is set in
Inter (Bold for the title, Regular for the caption), which ships with the application under the
SIL Open Font License. Both formats draw the same glyph outlines, so the SVG has no `<text>` and
depends on no installed font. Its `<title>` and `<desc>` carry the text for screen readers.

A frame is rejected with a 400 when:

- the text has a character Inter cannot draw, such as an emoji or CJK, rather than drawing an
  empty box;
- it leaves less than 2 modules of clear space around the code. The code's `margin` always
  counts; the frame's padding counts too when the card contrasts with the modules the way the
  code's background does. A dark card next to a light code is an edge in its own right, so then
  only `margin` counts;
- the framed image would be more than 6144 pixels on a side, or more than 1.5 times the pixels of
  the largest unframed code.

The QR specification asks for 4 modules of quiet zone, which the default `margin` gives. The
frame check's 2 is a floor: with a heavy border against the code, ZXing fails to read a clean
render at 0 modules and reads it at 1, and a camera needs some headroom over that.

### Scan check

`POST /api/qr-codes/check` takes the same document as the render endpoint and answers whether the
result is likely to scan. It renders the PNG, then reads it with ZXing's quick reader three ways:

| Trial      | Simulates                                                       |
| ---------- | --------------------------------------------------------------- |
| `rendered` | the image as drawn                                              |
| `small`    | the code far from the camera: shrunk to 3 pixels per module     |
| `blurred`  | a camera slightly out of focus: 4 pixels per module, then blurred |

```json
{
  "verdict": "MARGINAL",
  "version": 4,
  "modules": 33,
  "trials": [
    { "name": "rendered", "description": "As rendered", "decoded": true },
    { "name": "small", "description": "Small in the frame", "decoded": false },
    { "name": "blurred", "description": "Slightly out of focus", "decoded": false }
  ],
  "hints": ["Readers find the code by its three corner patterns. Try square or rounded corner patterns."]
}
```

`PASS` means every trial read the payload. `FAIL` means none did, even with ZXing's slower
`TRY_HARDER` mode. Anything in between is `MARGINAL`, and so are two styles that read fine here
but not everywhere: light modules on a dark background, which many scanner apps never look for,
and an unframed code with less than 2 modules of quiet zone, which only reads because the image
here sits on a blank canvas. The SVG has the same geometry as the PNG, so the verdict covers both.

ZXing reports whether it decoded, never why it didn't. The hints come from the style instead (logo
coverage against the error-correction level, contrast, quiet zone, density, module and corner
shapes, a background image), most likely cause first, and only when the verdict is not a pass.

### Errors

A rejected request is an RFC 9457 problem document. Constraint failures also carry an `errors`
array naming each field:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "data must not be blank",
  "instance": "/api/qr-codes",
  "errors": [{ "field": "data", "message": "data must not be blank" }]
}
```

A payload too long for the chosen error-correction level is a 400 with a detail saying so.

### Short links

`POST /api/links` stores a destination and returns the code that redirects to it.

```bash
curl -X POST http://localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/page","expiresAt":"2026-12-01T00:00:00Z"}'
```

```json
{
  "code": "aB3dE5fG7",
  "shortUrl": "http://localhost:8080/r/aB3dE5fG7",
  "targetUrl": "https://example.com/page",
  "createdAt": "2026-11-11T20:00:00Z",
  "expiresAt": "2026-12-01T00:00:00Z",
  "hitCount": 0,
  "lastHitAt": null
}
```

`expiresAt` is optional; omit it for a link that never expires. Only absolute `http` and `https`
destinations are accepted, and credentials in the URL are rejected. `GET /api/links?q=…&page=0&size=25`
lists links newest first, matching the destination or the code.

`PUT /api/links/{code}` changes the destination and expiry without changing the code, so a code
that has already been printed can be repointed at a new destination. An expired link can be
revived by giving it a future expiry or by clearing it. `DELETE /api/links/{code}` soft deletes
one, after which its code answers `410 Gone` rather than `404`.

`GET /r/{code}` is the public redirect. It answers `302 Found` with `Cache-Control: no-store`, so
every scan reaches the server and the hit count stays honest. An unknown code is `404`; an expired
or deleted one is `410`.

The short URL is composed from `app.public-base-url`, which defaults to `http://localhost:8080`.
Set `APP_PUBLIC_BASE_URL` to the address a scanner can reach — a LAN address, or the public name
behind a reverse proxy — before generating codes.

### Database

Short links have to survive a restart, so they are stored in H2 by default: a single file under
`./data`. A deployment with PostgreSQL only has to point the datasource at it:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/qr
SPRING_DATASOURCE_USERNAME=qr
SPRING_DATASOURCE_PASSWORD=secret
```

Flyway creates the schema at startup and Hibernate validates its mapping against it.

### Other endpoints

| Path                | Purpose                        |
| ------------------- | ------------------------------ |
| `/api/links`        | Create, list, edit and delete short links |
| `/r/{code}`         | Redirect for a short link      |
| `/actuator/health`  | Liveness and readiness         |
| `/v3/api-docs`      | The OpenAPI document           |

The server binds `127.0.0.1:8080` by default. Override `SERVER_ADDRESS` or `SERVER_PORT` to change
that.

## How it fits together

```
src/main/java/org/skrall/qrcode/
├── model/          request DTOs and enums, with the validation constraints
├── service/        encoding: payload → ZXing BitMatrix
├── check/          reads a rendered code back for the scan check
├── render/         matrix → PNG (Java2D) or SVG
├── link/           short link entity, repository and redirect resolution
└── web/            the controllers, the error advice and the format converter

src/main/resources/db/migration/
└── V1__short_link.sql   the schema Flyway applies

ui/src/
├── qr/types.ts     the payload builders for every supported content type
├── api/client.ts   the typed API client and the logo file reader
├── components/     content form, style panel, logo panel, preview, short links
└── App.vue         state, debounced preview and downloads
```

ZXing is used only to encode. Its writers can draw squares and nothing else, so rendering is
this project's own code: `PngQrRenderer` with Java2D and `SvgQrRenderer` with string geometry. The
two mirror each other deliberately, which is why they sit side by side.

The quiet zone is applied by the renderers in module units rather than by ZXing, so `margin` means
the same thing at every size. The finder patterns are skipped in the module pass and drawn as
whole shapes afterwards, because a per-module shape cannot keep their three rings concentric.

`LIQUID` modules are a rounded square per dark module plus a bridge to each dark neighbour on the
right and below. The bridge spans the full shared edge, so a run of modules becomes one rounded
bar rather than a chain of circles joined by a narrow neck. Finder cells count as light for those
bridges, which keeps the connection from reaching into an eye that is drawn afterwards.

A `CUSTOM` eye fills the standard square ring and centre with the artwork, then paints the gap
back in the background colour. The artwork is adjusted against that background before it is drawn,
so a light image cannot erase the rings, and the SVG renderer embeds the adjusted PNG rather than
the original data URL. Both formats therefore show the same pixels.

A background image is drawn first, then the modules and the eyes go on top. Its pixels are washed
toward the background colour until they contrast with the modules and the eyes, so a busy or dark
image cannot make the code unscannable. The finder gaps and the logo plate stay solid, which keeps
their structure readable. The SVG renderer embeds the washed image rather than the original upload.

A gradient's geometry, the line or the circle, is computed once in `Gradient`. `PngQrRenderer`
builds a Java2D `LinearGradientPaint` or `RadialGradientPaint` from it and `SvgQrRenderer` writes
the same numbers into a `userSpaceOnUse` gradient element, so the two formats match.

A frame is laid out once, in `Frame`, with every length rounded to whole pixels so the code lands
on the same grid as an unframed one. The text is turned into glyph outlines there, with the
bundled font, line breaking and kerning. Both renderers fill the card, the border ring and those
outlines, then draw the code translated into place. The gradient and the custom-eye clip paths
are in user space, so they move with the code in both formats.

## Notes

- The frame's font is Inter, bundled under `src/main/resources/fonts` with its licence.
- No upload storage: a logo, finder artwork or background image lives only in the request that
  carries it. Short links are the one thing stored, in the database, because they have to outlive
  a restart.
- The UI keeps each type's values separately, so switching from a vCard to a WiFi code and back
  does not discard what was typed.
- The preview is debounced and cancels the previous request, so typing does not queue renders.
  The scan check runs after each render, under the same cancellation.
