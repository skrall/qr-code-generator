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

