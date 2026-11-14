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
- `transparentBackground` skips every background fill, the frame's card included, and clears each
  eye's gap (PNG: `AlphaComposite.Clear`; SVG: a mask over the outer shape) rather than painting
  it. `background` stays the declared surface: contrast checks use it, and `ScanChecker` flattens
  over it, not over white. A background image is rejected with it; the logo plate stays opaque.
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
- `BP_JVM_VERSION` is pinned to `${java.version}`. Unpinned, the buildpack reads the version from
  the jar manifest, i.e. whichever JDK ran Maven, and a JDK 27 shell asks for a native toolchain
  that does not exist (`unable to find dependency for native-image-svm 27`).
- The distroless run image has no fontconfig, so the JDK refuses to load any font
  ("Fontconfig head is null"). `FontConfigFallback` writes a `fontconfig.properties` and points
  `sun.awt.fontconfig` at it before `FrameFonts` loads Inter. It only acts on Linux without
  `/etc/fonts/fonts.conf`. A native image ignores that property, see below.

## Native image

- Build: `./mvnw -Pnative spring-boot:build-image`. Run it with `QR_CODE_IMAGE=<tag>` and the
  compose file. The `native` profile in `pom.xml` merges with Spring Boot's and switches the run
  image to Paketo's base image, which has fontconfig and DejaVu: a native image only finds fonts
  through libfontconfig, so on "tiny" every framed code fails.
- What the image needs beyond Spring's AOT lives in `org.skrall.qrcode.nativeimage` (one
  registrar per library, each naming the failure it fixes) and in
  `src/main/resources/META-INF/native-image/org.skrall/qr-code-generator-java2d` (Java2D JNI,
  ImageIO and the HarfBuzz FFM calls, as JSON). Do not write into
  `META-INF/native-image/org.skrall/qr-code-generator`: Spring's AOT output owns that path.
- Resource patterns passed to `registerPattern` are image-builder globs: no `classpath*:` prefix.
  A prefixed pattern matches nothing at build time. `ClassPathScanner` patterns are the opposite:
  they are Spring resolver locations and need `classpath*:` to see every jar.
- `ClassPathScanner` skips `package-info`/`module-info`; a hint naming one fails AOT processing.
- A class name with `$` inside it (`Messages_$bundle`, `JpaLogger_$logger`) is parsed as nested by
  Spring's Class-based hint predicates, which then report a miss for a hint that is present. Test
  those by binary name.
- Each native build takes a few minutes and surfaces one startup failure at a time. To find the
  gaps in one pass, run the native-profile jar on a GraalVM JDK under the tracing agent and
  exercise every endpoint, then diff against what the build already has:
  `docker run ... ghcr.io/graalvm/jdk-community:25 java -agentlib:native-image-agent=config-merge-dir=/out -Dspring.aot.enabled=true -jar app.jar`.
  That container's JDK needs `microdnf install freetype` for fonts, and should have no
  `/etc/fonts` so the fallback path is traced. The agent misses arrays created only on paths a
  native image takes (Hikari's `ConcurrentBag$IConcurrentBagEntry[]`), so smoke-test the image too.
- Logs worth knowing: Flyway's "Unable to scan location: /db/migration (unsupported protocol:
  resource)" is harmless, as Spring Boot's resource provider applies the migrations. Hikari reports a
  failed first connection as a bare NPE in `throwPoolInitializationException`; set
  `LOGGING_LEVEL_COM_ZAXXER_HIKARI=DEBUG` to see the real error.

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

