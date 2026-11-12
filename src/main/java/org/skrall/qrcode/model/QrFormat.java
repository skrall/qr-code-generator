package org.skrall.qrcode.model;

/**
 * The two output formats.
 */
public enum QrFormat {
    PNG("image/png", "png"),
    SVG("image/svg+xml", "svg");

    private final String mediaType;
    private final String extension;

    QrFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    /**
     * Parses a format name without regard to case.
     *
     * <p>Spring's default enum conversion is case-sensitive, so {@code ?format=png} would be a
     * 400 against an enum called {@code PNG}. The converter registered in {@code WebConfig}
     * routes through here instead.
     *
     * @throws IllegalArgumentException when the name is not a known format
     */
    public static QrFormat parse(String value) {
        if (value != null) {
            String name = value.trim();
            for (QrFormat format : values()) {
                if (format.name().equalsIgnoreCase(name)) {
                    return format;
                }
            }
        }
        throw new IllegalArgumentException("format must be one of png or svg");
    }
}
