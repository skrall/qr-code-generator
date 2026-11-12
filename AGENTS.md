# AGENTS.md

Guidance for AI agents working in this repository.

## Commit messages

Keep them terse. One-line subject, and a body only when it earns its place.

## Running the build

- Prefix Maven with `mise exec --`: the global `java` is not the project's Java 25.
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
