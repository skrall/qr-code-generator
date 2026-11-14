# API

The API renders an image; the caller supplies the payload string. That keeps the server
independent of the difference between a URL, a vCard and a WiFi network — the UI composes those,
and so can any other client.

```bash
curl -X POST 'http://localhost:8080/api/qr-codes?format=png' \
  --user operator:change-me-on-deploy \
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

## Style

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
| `transparentBackground` | `true` leaves the background unpainted; see below | `false` |

## Transparent background

Set `transparentBackground` to `true` to paint only the code: the quiet zone, the light modules and
the gap inside each corner pattern are left transparent, so the code can be placed on existing
artwork. A PNG has alpha 0 there; an SVG has no background rect and masks the corner gaps out.

```json
{
  "data": "https://example.com",
  "style": {
    "foreground": "#111827",
    "background": "#f4f1e8",
    "transparentBackground": true
  }
}
```

`background` keeps its meaning: it is the colour of the surface the code is designed to sit on,
and every check uses it. A gradient must still contrast with it, finder artwork is still adjusted
against it, and the [scan check](#scan-check) flattens the image over it before reading, so a
light-on-dark design is judged on the dark it was made for. Placing the code on a surface far
from that colour, or on busy artwork without a clear quiet zone, can make it unreadable; the
check cannot know where it will end up.

How it combines with the other options:

- **Corner patterns.** The gap between the ring and the centre is transparent for every shape.
  With `CUSTOM` artwork the gap is cut out of the artwork, so the 1:1:3:1:1 structure is kept.
- **Logo.** The plate is a deliberate patch behind the logo and keeps its own `background`
  colour, so the logo stays legible on any artwork.
- **Background image.** Rejected with a 400: the image would fill the background that is meant
  to be transparent.
- **Frame.** The card and bleed are not painted either, only the border and the text, so the
  framed code works as a sticker on artwork. `frame.background` is ignored; the frame's quiet
  zone check treats the card as `background`, the surface it will sit on.

## Logo

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

## Finder artwork

Set `finderShape` to `CUSTOM` and send `finderArtwork` to fill the three finder patterns with an
image. The standard ring and centre stay in place and the one-module gap is left in the background
colour, or transparent with `transparentBackground`, so the code keeps the 1:1:3:1:1 structure a
reader looks for; only the colour inside the dark rings comes from the image. `finderColor` is the
fallback for transparent pixels.

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
least 96/255 away from the background colour, on the same side as the modules: darkened when the
modules are darker than the background, lightened when they are lighter (an inverted code). That
keeps the rings readable even when the image is mostly light. `CUSTOM` without artwork falls back
to a square eye.

## Background image

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
the washed PNG, so both formats show the same pixels. A background image cannot be combined with
`transparentBackground`; the request is rejected with a 400.

On an [inverted code](#inverted-codes) the image is darkened instead, and further: it may reach
only 15% of the way from the background toward the darkest ink. Readers binarize before they
invert and treat a flat area as paper, so a washed image only 96/255 below light modules reads as
module colour once inverted and fills the quiet zone.

## Gradient

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

## Inverted codes

Light modules on a dark background are an ordinary style: set `foreground` lighter than
`background`. Nothing else changes. A gradient's stops must then both be lighter than the
background, finder artwork is lightened rather than darkened, and a background image is washed
toward the dark background.

The scan check reads inverted codes (it asks ZXing for `ALSO_INVERTED`) but caps them at
`MARGINAL` with a hint, because many scanner apps only look for dark modules on a light
background. Phone camera apps generally read them.

The UI's **Invert colours** action builds one from the current style, and undoes it on a second
click. It swaps `foreground` and `background`, and reflects the corner colour and gradient stops
in brightness across the midpoint of the two, keeping their hue: each keeps its distance from the
background, so a style that rendered before still renders. A corner colour equal to the
foreground follows it, and one that would end up too close to the new background becomes the new
foreground. A gradient that cannot be reflected validly is turned off, with a notice. The logo
plate, the frame and any images are left alone.

## Frame

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
| `background`         | `#rrggbb`, the card and the bleed; ignored with `transparentBackground` | style `background` |
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

## Scan check

`POST /api/qr-codes/check` takes the same document as the render endpoint and answers whether the
result is likely to scan. It renders the PNG, flattens it over the `background` colour (which only matters when the
background is transparent), then reads it with ZXing's quick reader three ways:

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
shapes, a background image), most likely cause first, and only when the verdict is not a pass. A
transparent background adds a last hint naming the colour the verdict assumes.

## Errors

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

## Short links

`POST /api/links` stores a destination and returns the code that redirects to it.

```bash
curl -X POST http://localhost:8080/api/links \
  --user operator:change-me-on-deploy \
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
  "lastHitAt": null,
  "goneUrl": null,
  "goneMessage": null
}
```

`expiresAt` is optional; omit it for a link that never expires. Only absolute `http` and `https`
destinations are accepted, and credentials in the URL are rejected. `GET /api/links?q=…&page=0&size=25`
lists links newest first, matching the destination or the code.

`PUT /api/links/{code}` changes the destination and expiry without changing the code, so a code
that has already been printed can be repointed at a new destination. An expired link can be
revived by giving it a future expiry or by clearing it. `DELETE /api/links/{code}` soft deletes
one, after which its code answers `410 Gone` rather than `404`.

`goneUrl` and `goneMessage` (both optional, on create and edit) say what a scan gets once the link
has expired or been deleted:

- `goneUrl` — redirect there instead of showing a page. Checked exactly like `url`: absolute
  `http`/`https` only, at most 2048 characters, no credentials, non-ASCII percent-encoded.
- `goneMessage` — plain text, at most 280 characters, shown on the page instead of the default
  wording. Line breaks are kept; other control characters are rejected. Markup is not
  interpreted: the text is HTML-escaped when shown.

Blank values mean "none". An edit replaces both, like the expiry: leaving one out clears it. Only
an OPERATOR may set them, as with every other change to a link.

`GET /r/{code}` is the public redirect. It answers `302 Found` with `Cache-Control: no-store`, so
every scan reaches the server and the hit count stays honest. Otherwise:

| Code | Answer |
|---|---|
| Live | `302` to the destination, counted as a hit |
| Expired or deleted, with a `goneUrl` | `302` to the `goneUrl`, not counted |
| Expired or deleted, with a `goneMessage` | `410` page showing the message |
| Expired or deleted, neither set, `app.links.gone-url` set | `302` to that URL, not counted |
| Expired or deleted, otherwise | `410` page with the default wording |
| Unknown | `404` page |

`app.links.gone-url` (`APP_LINKS_GONE_URL`) is an optional site-wide fallback, validated like a
destination at startup. It never applies to an unknown code. The `404` and `410` pages are small
self-contained HTML (inline CSS, light and dark, sized for a phone) that never show the old
destination. Both carry `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
`X-Robots-Tag: noindex`, `X-Content-Type-Options: nosniff` and a
`Content-Security-Policy` of `default-src 'none'` that allows only the page's own stylesheet by
hash: no scripts, images, frames or forms. A fallback redirect has the same headers as a live one.

The short URL is composed from `app.public-base-url`, which defaults to `http://localhost:8080`.
Set `APP_PUBLIC_BASE_URL` to the address a scanner can reach — a LAN address, or the public name
behind a reverse proxy — before generating codes.


## Saved designs

A design is a named style, images included, saved to reuse. It is private to whoever saved it
unless shared, in which case everyone who can sign in sees it.

| Request | Does |
| --- | --- |
| `GET /api/designs` | The caller's designs and every shared one, by name, without their styles |
| `GET /api/designs/{id}` | One design with its style |
| `POST /api/designs` | Saves `{"name", "shared", "style"}`; answers 201 |
| `PUT /api/designs/{id}` | Renames, shares or unshares, and replaces the style when `style` is sent |
| `DELETE /api/designs/{id}` | Deletes it; answers 204 |

```bash
curl -X POST http://localhost:8080/api/designs \
  --user operator:change-me-on-deploy \
  -H 'Content-Type: application/json' \
  -d '{"name":"Brand blue","shared":true,"style":{"moduleShape":"ROUNDED","finderColor":"#3158c4"}}'
```

- **Who sees what.** Your own designs and every shared one. Someone else's private design
  answers 404, for operators too, so its existence is not disclosed.
- **Who changes what.** A design's owner, and operators for shared designs. Anyone else gets a
  403. Each listed design says `mine` and `editable`, so a client can show the right actions.
- **Checked on save.** The style goes through the same checks as a render (image limits,
  gradient contrast, frame text), so a design that could not render is refused with a 400.
- **Light lists.** A style with images can run to megabytes, so the list leaves styles out, and
  an update without `style` keeps the saved one instead of requiring the images to be resent.

Any signed-in user may save designs: the operator role governs short links, not styles. The UI
also exports a design as a `.qr-design.json` file and imports one back; that happens in the
browser and needs no endpoint.

The UI's share links need no endpoint either. A style without images travels in the link's
fragment (`#share=v1.…`), which is never sent to the server. A style with images is shared as
`#design=<id>`, which the opening browser loads with `GET /api/designs/{id}`, so the visibility
rules above apply: a private design opens only for its owner. See
[the architecture notes](architecture.md#undo-and-share-links) for the format.

## Batch generation

`POST /api/qr-codes/batch` renders every row of a CSV with one style and answers a ZIP. It is
multipart, because the options carry a style that can run to megabytes of images, and a browser
can send the chosen file as it is:

| Part | Content type | Holds |
| --- | --- | --- |
| `csv` | any (`text/csv`) | The CSV, UTF-8, at most 1 MB |
| `options` | `application/json` | Optional: `{"format", "style"}` or `{"format", "designId"}` |

```bash
curl -X POST http://localhost:8080/api/qr-codes/batch \
  --user operator:change-me-on-deploy \
  -F 'csv=@codes.csv;type=text/csv' \
  -F 'options={"format":"both","style":{"size":640,"moduleShape":"ROUNDED"}};type=application/json' \
  --output qr-codes.zip
```

- **`format`** is `png` (the default), `svg` or `both`, in any case. `both` writes a PNG and an
  SVG per row.
- **`style`** is the same document the render API takes; absent, the code is plain
  black-on-white. **`designId`** takes the style from a saved design instead. A design you
  cannot see answers 404, just as `GET /api/designs/{id}` does; sending both is a 400.
- The `options` part must be sent as `application/json` (curl's `;type=application/json`);
  without it the server answers 415.
- A style with images carries data URLs, and `data:image/png;base64,…` contains a `;`, which
  curl's `-F name=value` reads as a parameter separator, cutting the JSON short (a 400, "Failed
  to read request"). Put such options in a file and send `-F 'options=@options.json;type=application/json'`.

### The CSV

```csv
data,filename
https://example.com,example
"WIFI:T:WPA;S:Cafe;P:espresso;;",wifi
"BEGIN:VCARD
VERSION:3.0
FN:Ann Example
END:VCARD",ann
```

- The first row is the header and must name a `data` column: the payload, encoded exactly as
  written, as `data` is in the render API. A `filename` column is optional. Names are matched
  without regard to case or surrounding spaces; other columns are ignored.
- RFC 4180: comma-separated, records ending in CRLF or LF. A field in double quotes may hold
  commas, line breaks and quotes written twice (`""`). Text after a closing quote, or a quote
  never closed, is a 400 naming the line. A stray quote inside an unquoted field is kept as text.
- UTF-8, with or without a byte order mark; bytes that are not valid UTF-8 are a 400. Excel's
  "CSV UTF-8" works.
- Empty lines are skipped. Rows are numbered from 1, the first after the header, not counting
  them.

### Limits

At most 500 rows and 1 MB of CSV; more is a 400 that says which. The style is checked once, before
anything is rendered, so an invalid one is a 400 like a single render's. The multipart request as
a whole is capped at 12 MB (10 MB per part) to leave room for a style's images; past that the
server answers 413.

### File names

Each row's entry is its `filename`, made safe, plus `.png` or `.svg`:

- Letters, digits, `.`, `-` and `_` are kept; anything else, `/` and `\` included, becomes `_`.
  Runs of dots become one, so `..` never survives, and leading or trailing dots, dashes and
  underscores are dropped. A trailing `.png` or `.svg` is dropped, as the extension is added
  per format. Names are cut to 80 characters, and Windows device names (`CON`, `NUL`, `COM1`…)
  get a `_` appended.
- A missing or unusable name becomes `row-<n>`.
- Names are unique without regard to case: a repeat becomes `name-2`, `name-3` and so on. A
  skipped row keeps its name, so the names do not depend on which rows rendered.

### The archive and `report.csv`

The response is `application/zip` with `Content-Disposition: attachment; filename="qr-codes.zip"`.
It is written while the codes render rather than built first, so the first bytes arrive early and
only one row's images are held in memory at a time. PNG entries are stored (they are compressed
already) and SVG entries deflated.

A row that cannot be rendered — empty `data`, more than 8000 characters, too long for the
error-correction level, too long for a frame's quiet zone — is skipped rather than failing the
batch. The archive's last entry, `report.csv`, lists every row:

```csv
row,filename,status,message
1,example,ok,
2,row-2,skipped,data must not be blank
```

`filename` is the entry name without its extension; `status` is `ok` or `skipped`, and a
skipped row's `message` is the text a single render of it would answer with.

Any signed-in user may run a batch, viewers included, as with a single render. Under OIDC the POST
needs the CSRF token like any other.

## Other endpoints

| Path                | Purpose                        |
| ------------------- | ------------------------------ |
| `/api/links`        | Create, list, edit and delete short links |
| `/api/designs`      | Save, list, load, change and delete designs |
| `/api/me`           | The signed-in user and whether they hold the operator role |
| `/r/{code}`         | Redirect for a short link      |
| `/actuator/health`  | Liveness and readiness         |
| `/v3/api-docs`      | The OpenAPI document           |

The server binds `127.0.0.1:8080` by default. Override `SERVER_ADDRESS` or `SERVER_PORT` to change
that.
