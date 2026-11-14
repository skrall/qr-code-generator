# Architecture

```
src/main/java/org/skrall/qrcode/
├── model/          request DTOs and enums, with the validation constraints
├── service/        encoding: payload → ZXing BitMatrix
├── check/          reads a rendered code back for the scan check
├── render/         matrix → PNG (Java2D) or SVG
├── link/           short link entity, repository and redirect resolution
├── design/         saved designs and who may see and change them
├── security/       the four sign-in modes, role mapping and the proxy allowlist
├── nativeimage/    runtime hints a GraalVM native image needs (see AGENTS.md)
└── web/            the controllers, the error advice and the format converter

src/main/resources/db/migration/
├── V1__short_link.sql   short links
└── V2__design.sql       saved designs

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
