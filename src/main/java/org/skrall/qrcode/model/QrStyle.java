package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * How the code should look. Every field is optional; the server supplies a default for an
 * absent one, so a caller can send {@code {}} and get a plain black-on-white code.
 *
 * @param size            the image's side in pixels, 128 to 4096, default 512
 * @param margin          the quiet zone in modules, 0 to 16, default 4
 * @param errorCorrection the redundancy level, default MEDIUM
 * @param foreground      module colour as {@code #rrggbb}, default black
 * @param gradient        an optional gradient that replaces the foreground colour of the modules
 * @param background      background colour as {@code #rrggbb}, default white
 * @param backgroundImage an optional image drawn behind the code, washed for contrast
 * @param moduleShape     the shape of a data module, default SQUARE
 * @param finderShape     the shape of the three corner patterns, default SQUARE
 * @param finderColor     the corner patterns' colour, default the foreground colour
 * @param finderArtwork   an optional image that fills the corner rings when the shape is CUSTOM
 * @param logo            an optional logo to place in the middle
 * @param frame           an optional card around the code, with a title, caption, border and
 *                        bleed; it makes the image larger rather than the code smaller
 */
public record QrStyle(
        @Min(value = 128, message = "style.size must be at least 128")
        @Max(value = 4096, message = "style.size must be at most 4096")
        Integer size,

        @Min(value = 0, message = "style.margin must not be negative")
        @Max(value = 16, message = "style.margin must be at most 16")
        Integer margin,

        ErrorCorrection errorCorrection,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.foreground must be a hex colour such as #1b2430")
        String foreground,

        @Valid
        QrGradient gradient,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.background must be a hex colour such as #ffffff")
        String background,

        @Valid
        QrBackgroundImage backgroundImage,

        ModuleShape moduleShape,

        FinderShape finderShape,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.finderColor must be a hex colour such as #1b2430")
        String finderColor,

        @Valid
        QrFinderArtwork finderArtwork,

        @Valid
        QrLogo logo,

        @Valid
        QrFrame frame) {

    /** A style with every field absent, used to resolve defaults. */
    public static final QrStyle EMPTY =
            new QrStyle(null, null, null, null, null, null, null, null, null, null, null, null, null);
}
