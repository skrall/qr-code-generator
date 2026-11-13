package org.skrall.qrcode.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A card around the code: a title above, a caption below, a border and a print bleed.
 *
 * <p>Lengths are percentages of the code's side ({@link QrStyle#size()}), so a card keeps its
 * proportions at every size and means the same in PNG and SVG. The frame adds to the image rather
 * than shrinking the code: {@code size} is still the code's side, and the image grows around it.
 *
 * <p>From the outside in: {@code bleed}, which is printed but meant to be trimmed off; then
 * {@code margin} between the trim line and the border; the border itself; and {@code padding}
 * between the border and the code. The title and caption sit inside the padding, centred on the
 * code and wrapped to its width.
 *
 * @param title              bold text above the code; blank for none
 * @param caption            text below the code; blank for none
 * @param background         the card colour, default the style's background
 * @param textColor          the title and caption colour, default the foreground
 * @param borderColor        the border colour, default the text colour
 * @param titleSizePercent   the title's font size, default 9
 * @param captionSizePercent the caption's font size, default 5
 * @param borderPercent      the border's width, 0 for none, default 2
 * @param radiusPercent      the border's corner radius, default 4
 * @param paddingPercent     the space between the border and the code, default 6
 * @param marginPercent      the space between the trim line and the border, default 0
 * @param bleedPercent       card colour beyond the trim line, for printing, default 0
 */
public record QrFrame(
        @Size(max = 60, message = "style.frame.title must be at most 60 characters")
        String title,

        @Size(max = 120, message = "style.frame.caption must be at most 120 characters")
        String caption,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.frame.background must be a hex colour such as #ffffff")
        String background,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.frame.textColor must be a hex colour such as #1b2430")
        String textColor,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.frame.borderColor must be a hex colour such as #1b2430")
        String borderColor,

        @Min(value = 3, message = "style.frame.titleSizePercent must be at least 3")
        @Max(value = 20, message = "style.frame.titleSizePercent must be at most 20")
        Integer titleSizePercent,

        @Min(value = 2, message = "style.frame.captionSizePercent must be at least 2")
        @Max(value = 15, message = "style.frame.captionSizePercent must be at most 15")
        Integer captionSizePercent,

        @Min(value = 0, message = "style.frame.borderPercent must not be negative")
        @Max(value = 10, message = "style.frame.borderPercent must be at most 10")
        Integer borderPercent,

        @Min(value = 0, message = "style.frame.radiusPercent must not be negative")
        @Max(value = 25, message = "style.frame.radiusPercent must be at most 25")
        Integer radiusPercent,

        @Min(value = 0, message = "style.frame.paddingPercent must not be negative")
        @Max(value = 30, message = "style.frame.paddingPercent must be at most 30")
        Integer paddingPercent,

        @Min(value = 0, message = "style.frame.marginPercent must not be negative")
        @Max(value = 20, message = "style.frame.marginPercent must be at most 20")
        Integer marginPercent,

        @Min(value = 0, message = "style.frame.bleedPercent must not be negative")
        @Max(value = 20, message = "style.frame.bleedPercent must be at most 20")
        Integer bleedPercent) {
}
