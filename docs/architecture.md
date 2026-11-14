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
├── components/     content form, style panel, logo panel, preview, short links, share link
├── embed.ts        "Copy as…" snippet builders (alt text, data URLs, inline SVG ids)
├── history.ts      undo/redo history, snapshots and the keyboard shortcut rules
├── editorHistory.ts the history wired to the editor's reactive state and the keyboard
├── share.ts        the share link format: encode, decode and check
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

A transparent background is a style flag, not a colour: the renderers skip every fill of the
background colour. The PNG starts at alpha 0 and is never painted there; the SVG has no background
rect. The one-module gap of each eye, normally painted back in the background colour, is cleared
instead: `PngQrRenderer` fills it with `AlphaComposite.Clear` and `SvgQrRenderer` masks it out of the
outer shape, for the standard eyes and for `CUSTOM` artwork alike. A frame's card and bleed are left
unpainted too. `background` still names the surface the code is meant for, so the gradient check,
the artwork adjustment and the scan check, which flattens the image over that colour before reading
it, all work as before.

## Embed snippets

The preview's "Copy as…" menu (`EmbedMenu.vue`, builders in `ui/src/embed.ts`) fetches the image
with the same render call as a download, so a snippet is byte-for-byte the file a download would
save, not the preview's object URL. It is disabled whenever the downloads are: while the payload is
a short link draft, while rendering, and after an error.

- **`<img>`, data URL and linked `<img>`** use the PNG. `width` and `height` come from the PNG's
  IHDR chunk, since a frame makes the image larger than `size`. The link wrapper is offered only
  for an http(s) payload, a short link included.
- **Alt text** names what the code is for and leaves out anything private: a URL is reduced to its
  host (a query can carry tokens), a WiFi code names the network but never the password, SMS and
  email bodies, IBANs, amounts and free text are left out.
- **Inline SVG** drops the XML declaration, puts the alt text in `<title>`, and prefixes every `id`
  with a random `qrxxxxxx-` along with each `url(#…)` and `href="#…"` that points at one. The
  renderer's fixed ids (`qr-gradient`, `finder-outer`, `finder-gap-0`…) are right for a file, but
  inline every id shares the page, so a second code would paint with the first one's gradient or
  mask. This is done in the browser rather than as a server option: only the pasted copy needs
  it, the download and the API stay unchanged, and the rewrite covers any id a renderer adds later.
  Embedded images are `data:` URLs, so the reference rewrite never touches them.
- **Size.** A snippet over 100 KB, usually from a logo or background image, is still copied, with a
  note suggesting inline SVG or hosting the file.
- **Clipboard.** If the Clipboard API is missing (plain HTTP on a LAN) or refuses, the snippet is
  shown in a read-only, preselected textarea to copy by hand, with the reason in the live region.
- **No Markdown.** A Markdown image would need a data URL, which GitHub and most sanitizing
  renderers strip, or a hosted file, which this tool does not provide.

## Payload types

The server encodes whatever string it is sent; each content type is a builder in
`ui/src/qr/types.ts` that composes that string, with an optional `validate` that reports values
which were entered but cannot be encoded. While a type reports a problem its builder returns
nothing, so no code is rendered, and the form shows the message under the field.

| Type | Payload |
| --- | --- |
| WiFi | `WIFI:T:WPA;S:<ssid>;P:<password>;H:true;;` (ZXing grammar). "WPA3 only" is `T:WPA;R:1`: the WPA3 specification's transition-disable bit, not Android's `T:SAE` |
| vCard | vCard 3.0, CRLF line endings |
| MeCard | `MECARD:N:Last,First;TEL:…;EMAIL:…;URL:…;ADR:…;NOTE:…;;`, with `\ ; , :` backslash-escaped |
| App store | `https://apps.apple.com/app/id<digits>` or `https://play.google.com/store/apps/details?id=<package>` |
| SEPA payment | EPC069-12 "GiroCode": `BCD`, `002`, `1` (UTF-8), `SCT`, BIC, name, IBAN, `EUR<amount>`, purpose, reference or text, note, one per line (LF), at most 331 bytes |

The SEPA type checks the IBAN's and an RF reference's mod-97 check digits and every field length
in the EPC guidelines, since a banking app rejects a code that is off by one line. The guidelines
also recommend error correction level M for these codes.


## Undo and share links

**History.** `ui/src/history.ts` keeps up to 100 snapshots of the editor: the content tab, every
tab's values and the style. A change restarts a 450 ms timer and the step is recorded once the
editor is quiet, so dragging a slider or typing a word is one step; undoing records a pending
change first. A snapshot copies plain objects and shares strings, so an image held as a data URL
is never copied: taking and comparing one costs about 10 µs with two 2.7 MB images, against about
2.6 ms for `JSON.stringify` of the same state. That is cheap enough to compare on every change,
which is how a restore, echoing back through the deep watcher, is recognised as no edit and
records nothing. A new edit after an undo discards the undone steps. Applying a share link calls
`commit` so it is its own step; loading a design, importing a file or inverting colours are
single changes and become one step through the timer.

Ctrl/Cmd+Z undoes and Ctrl/Cmd+Shift+Z or Ctrl+Y redo, except when focus is in a text input, a
textarea, a select or an editable element, whose native undo is left alone. Sliders, checkboxes
and buttons have no native undo, so the shortcut applies there.

**Share links.** The state goes in the URL fragment, which browsers never send, so the server
and its logs never see it:

```
#share=v1.z.<base64url(deflate-raw(JSON))>    CompressionStream
#share=v1.j.<base64url(JSON)>                 browsers without CompressionStream
#design=<id>                                  a saved design, loaded with GET /api/designs/{id}
```

The JSON is `{"style": {…}, "content"?: {"type": "wifi", "values": {…}}}`. The style is the API
style without images; content is only added when "Include content" is ticked, and never holds a
field marked `secret` in `qr/types.ts` (the WiFi password). Images would make the link megabytes
long, so a style with any is shared as a `#design=` link to the saved design on screen, which the
server's visibility rules then govern: someone else's private design answers 404 and the UI says
it was not found or not shared. Links are capped at 8,000 characters both ways, and a link may
inflate to at most 64 KB. Decoding checks the version, every field's type and the content's
type and keys, and ignores images and secrets a crafted link might carry.

On startup, and on a `hashchange`, the fragment is applied, then removed with
`history.replaceState`. From then on the editor and its undo history hold the state; leaving
the fragment would make a reload throw later edits away, and would keep content in the address
bar. A damaged, oversized or newer-version link is reported in the Share card and changes nothing.

## Notes

- The frame's font is Inter, bundled under `src/main/resources/fonts` with its licence.
- No upload storage: a logo, finder artwork or background image lives only in the request that
  carries it. Short links are the one thing stored, in the database, because they have to outlive
  a restart.
- The UI keeps each type's values separately, so switching from a vCard to a WiFi code and back
  does not discard what was typed.
- The preview is debounced and cancels the previous request, so typing does not queue renders.
  The scan check runs after each render, under the same cancellation.
