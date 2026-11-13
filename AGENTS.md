# AGENTS.md

Guidance for AI agents working in this repository.

## Commit messages

Keep them terse. One-line subject, and a body only when it earns its place.

## Running the build

- `./mvnw` uses the `java` on `PATH`. `mise.toml` pins Java 25, but only `mise exec --` applies
  that pin; a non-interactive shell usually has not activated mise. The build targets Java 25
  bytecode and passes on the system JDK 27 as well, so either works.
- The UI build pins Node `v26.13.0` (Maven downloads it to `ui/node/node`). Use that binary
  rather than a system Node: `export PATH="$PWD/ui/node/node:$PATH"`.
- When changing `node.version`, `rm -rf ui/node` first. Leaving the old download in place mixes
  two Node trees and `npm ci` dies with `Class extends value undefined is not a constructor or null`.
- Changing a dependency in `ui/package.json` also needs the lockfile regenerated
  (`ui/node/node/npm install`), or `npm ci` refuses to run.
- `npm run build` runs `vue-tsc --noEmit` then `vite build`; typecheck first.
- `./mvnw verify` runs the UI install, build and tests as well, so the Java build alone is not a
  shortcut past the UI tests. Use `-Dskip.ui=true` for Java only.
- `QrCodeServiceTest` decodes the rendered PNG with ZXing. Keep those round-trip tests: a shape
  change that makes the code unscannable is a regression even if the image still renders.

## Rendering

- `PngQrRenderer` and `SvgQrRenderer` implement the same geometry. Change both together.
- The quiet zone is applied in module units by the renderers, not by ZXing (`MARGIN=0`).
- The three finder patterns are excluded from the module pass and drawn as whole shapes, so
  their rings stay concentric.
- `LIQUID` modules are a rounded square plus full-height/full-width bridges to dark neighbours on
  the right and below. Finder cells count as light for those bridges so the connection never
  reaches into an eye drawn afterwards. Keep the geometry identical in both renderers.
- A `CUSTOM` finder fills the standard square ring and centre with the artwork and leaves the
  one-module gap in the background colour, so the 1:1:3:1:1 structure survives. Artwork luminance
  is clamped against the background before drawing; the SVG renderer embeds that adjusted PNG,
  not the original data URL, so both formats show the same pixels.
- A background image is cropped square, flattened over the background colour and washed until
  every pixel contrasts with every ink drawn over it (foreground, finder colour and the adjusted
  finder artwork, so the artwork is decoded first) before the modules are drawn. Keep that order: the
  image goes down first, then modules and eyes, so the finder gaps and logo plate stay solid. The
  SVG renderer embeds the washed PNG, not the original upload.
- A gradient's line and circle come from `Gradient`, which both renderers read; don't compute
  gradient geometry inside a renderer. It spans the canvas in user space, not each module. Its
  check is on the two stops only, which is exact because both formats interpolate in sRGB and
  luminance is linear in sRGB. Adding stops or a linear-light interpolation would break that.
- A frame's layout and text outlines come from `Frame`; both renderers fill those shapes and
  translate the code into place. Don't set frame text with SVG `<text>` or a system font: the
  formats would stop matching. Keep frame lengths whole pixels so the code's grid is unchanged.
- Frame text is set in the bundled Inter (`src/main/resources/fonts`, OFL). Text it cannot draw is
  rejected, not drawn as boxes. Swapping the font changes which text is accepted.

## Scan check

- `ScanChecker` reads `PngQrRenderer.draw`'s pixels; there is no separate SVG check because the
  geometry is shared. A renderer change that alters what is drawn changes the verdicts too.
- The trials use the quick reader, no `TRY_HARDER`; that is what makes them discriminating.
  `TRY_HARDER` only separates `FAIL` from `MARGINAL` on the as-rendered image.
- The verdict thresholds (3 and 4 pixels per module, two 3×3 box blurs) were calibrated against
  every module and corner shape, logos of 15–35% at each level, grey modules and thin margins.
  `ScanCheckerTest` pins both sides of those boundaries; rerun a sweep before changing them.
- `ALSO_INVERTED` only works through `MultiFormatReader`, not `QRCodeReader`.
- Hints come from the style, not the reader, and are only sent when the verdict is not a pass.
- The UI runs the check after the render succeeds, not beside it, so a rejected style is
  reported once and the image is never held up by the check.

## Security

- Modelled on mailoverlord (`~/Work/java/mailoverlord`, `config/SecurityConfig.java`). Keep the
  two aligned when changing either; the role mappers, header filter and proxy matcher are ports.
- The general suite runs under `app.security.mode=none` (set in the test
  `application.properties`). Each mode has its own `*SecurityTest` that turns it back on.
- All four chains share `SecurityConfig.authorize`. A new public path goes there, once.
- `/r/**` must stay public in every mode: printed codes are scanned by phones that never sign in.
- OPERATOR is enforced with `@PreAuthorize` on the link controller's create, update and delete.
  Method security is off under `none`, so those annotations are inert there.
- OIDC uses `csrf.spa()`, not a bare `CookieCsrfTokenRepository`: the default request handler
  defers the cookie and expects a masked token, so an SPA echoing the cookie would always get
  403. `OidcSecurityTest.theSpaCsrfRoundTripIsAccepted` does the real round trip and fails
  without `spa()`.
- Every UI request goes through `send` in `ui/src/api/client.ts`, which adds the CSRF header and
  turns a 401 into a navigation (Basic, OIDC) or a shown message (header mode).

## Docker

- The image comes from `./mvnw spring-boot:build-image` (buildpacks); there is no Dockerfile.
  `BPE_SERVER_ADDRESS=0.0.0.0` in `pom.xml` is what makes the container reachable; it does not
  show in `docker image inspect`'s env because buildpacks apply it at launch.
- `docker-compose.yml` has an `h2` and a `postgres` profile, after mailoverlord's. `.env` is
  git-ignored; `.env.example` is the template and must list every variable the file reads.
- To try the compose file without touching a real `.env` or its volumes, use another project
  name and port: `COMPOSE_PROFILES=postgres APP_PORT=18080 QR_CODE_PASSWORD=x POSTGRES_PASSWORD=y
  docker compose -p qrtest up -d`, then `docker compose -p qrtest down -v`.

## Short links

- Flyway owns the schema. Add a new `V{n}__*.sql` under `src/main/resources/db/migration` rather
  than editing an applied one. `ddl-auto` is `validate`, so a mapping that drifts fails at startup.
- Tests use an in-memory H2 through `src/test/resources/application.properties`; the file-backed
  default would otherwise write `data/` into the checkout.
- `data/` holds the H2 database and is ignored. Delete it to start with an empty link table.
- A code that was deleted or has expired answers `410 Gone`, not `404`. Keep the soft delete:
  a printed code that stops working is not the same as an address that never existed.
- Editing a link changes its destination and expiry but never its code. The code is what a
  printed QR image carries, so replacing it would defeat the point of editing.

## UI

- Never name a prop `style` (or `class`). Vue clones a reactive object under those keys into a
  plain one, so a child would mutate a detached copy: clicks appear to do nothing and no error
  is raised. `StylePanel` takes `qrStyle` for this reason, and `StylePanel.spec.ts` guards it.
- Creating a short link is an explicit action, not part of the debounced preview. Wiring it into
  the preview would store a destination on every keystroke. The preview does render a draft of
  the destination while typing; downloads stay disabled until the short URL exists.
- The generator stays mounted with `v-show` while the links view is open, so the form state and
  the preview cannot disagree when switching back.

