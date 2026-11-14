# Java2D metadata for the native image

`reachability-metadata.json` lets the GraalVM native image run Java2D, the font manager and
ImageIO: the JNI callbacks AWT's native libraries make into Java (fields such as
`ImageRepresentation.numSrcLUT`), the reflection ImageIO and the fonts use, and the `foreign`
section: JDK 25 shapes text with HarfBuzz through the Foreign Function & Memory API
(`sun.font.HBShaper`), and a native image needs every downcall and upcall signature listed.
Without it the image starts, but every logo, background image, finder artwork, frame and scan
check fails. JPEG decoding is native too (`libjavajpeg` calls back into `JPEGImageReader`), so
JPEG uploads need their own entries; PNG and GIF decoding are pure Java.

It was recorded with the GraalVM tracing agent while the app rendered every style and ran the
scan check, with PNG, JPEG (baseline, progressive, grayscale) and GIF uploads in every image
slot, then filtered to `java.awt`, `sun.awt`, `sun.font`, `sun.java2d`, `javax.imageio`,
`com.sun.imageio` and JNI entries, with the agent's `foreign` section taken whole.

Frames also need fontconfig and a system font at runtime, which is why the `native` Maven
profile builds on the Paketo base run image rather than "tiny"; see `pom.xml`. It lives apart from
`META-INF/native-image/org.skrall/qr-code-generator`, which Spring's AOT processing writes.

To regenerate after a JDK upgrade, see "Native image" in `AGENTS.md`, and upload every image
format the app accepts while recording: a run with PNGs only misses the JPEG callbacks, and
`Java2dMetadataTest` fails if they are absent.
