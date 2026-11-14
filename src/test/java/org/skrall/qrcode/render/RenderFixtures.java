package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.skrall.qrcode.model.QrCodeRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Styles that between them reach every branch of the drawing code: each module and finder shape,
 * both gradients, the transparent background, custom finder artwork, a background image, PNG and
 * JPEG logos and a frame with text, border and bleed.
 */
final class RenderFixtures {

    static final String DATA = "https://example.com/menu?table=12";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RenderFixtures() {
    }

    /** Every fixture by name, in a stable order. */
    static Map<String, QrCodeRequest> all() {
        Map<String, QrCodeRequest> styles = new LinkedHashMap<>();
        String[] modules = {"SQUARE", "ROUNDED", "DOTS", "STAR", "LIQUID"};
        String[] finders = {"SQUARE", "ROUNDED", "CIRCLE", "LEAF", "DIAMOND"};
        for (int i = 0; i < modules.length; i++) {
            styles.put("shape-" + modules[i] + "-" + finders[i], request("""
                    {"size":300,"moduleShape":"%s","finderShape":"%s","finderColor":"#1d4ed8"}"""
                    .formatted(modules[i], finders[i])));
        }
        styles.put("linear-gradient", request("""
                {"size":320,"moduleShape":"LIQUID","finderShape":"ROUNDED",
                 "gradient":{"type":"LINEAR","start":"#1e3a8a","end":"#7c2d12","angle":135,"finders":true}}"""));
        styles.put("radial-gradient", request("""
                {"size":320,"moduleShape":"DOTS",
                 "gradient":{"type":"RADIAL","start":"#14532d","end":"#1e1b4b","finders":false}}"""));
        styles.put("transparent", request("""
                {"size":300,"transparentBackground":true,"finderShape":"CIRCLE","moduleShape":"ROUNDED"}"""));
        styles.put("transparent-gradient", request("""
                {"size":300,"transparentBackground":true,"finderShape":"LEAF",
                 "gradient":{"type":"LINEAR","start":"#1e3a8a","end":"#4c1d95","angle":90,"finders":true}}"""));
        styles.put("custom-finder", request("""
                {"size":300,"finderShape":"CUSTOM","finderArtwork":{"dataUrl":"%s"}}"""
                .formatted(artwork())));
        styles.put("custom-finder-transparent", request("""
                {"size":300,"finderShape":"CUSTOM","transparentBackground":true,
                 "finderArtwork":{"dataUrl":"%s"}}""".formatted(artwork())));
        styles.put("background-image", request("""
                {"size":300,"errorCorrection":"HIGH","backgroundImage":{"dataUrl":"%s"}}"""
                .formatted(backgroundImage())));
        styles.put("logo-png", request("""
                {"size":300,"errorCorrection":"HIGH","logo":{"dataUrl":"%s","sizePercent":22}}"""
                .formatted(pngLogo())));
        styles.put("logo-jpeg-with-background", request("""
                {"size":300,"errorCorrection":"HIGH","logo":{"dataUrl":"%s","background":"#fde68a"},
                 "backgroundImage":{"dataUrl":"%s"}}""".formatted(jpegLogo(), backgroundImage())));
        styles.put("frame-bleed", request("""
                {"size":300,"frame":{"title":"Scan me","caption":"Menu, hours and bookings",
                 "background":"#fef3c7","borderPercent":2,"marginPercent":3,"bleedPercent":6}}"""));
        styles.put("frame-transparent", request("""
                {"size":300,"transparentBackground":true,
                 "frame":{"title":"Hello","borderPercent":3,"bleedPercent":4}}"""));
        return styles;
    }

    static QrCodeRequest request(String style) {
        return JSON.readValue("{\"data\":\"" + DATA + "\",\"style\":" + style + "}", QrCodeRequest.class);
    }

    /** A two-colour artwork, so a misplaced clip shows. */
    static String artwork() {
        BufferedImage image = new BufferedImage(70, 70, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x7f1d1d), 70, 70, new Color(0x1e3a8a)));
        g.fillRect(0, 0, 70, 70);
        g.dispose();
        return dataUrl(image, "png", "image/png");
    }

    /** A soft, busy picture: the wash has something to do. */
    static String backgroundImage() {
        BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int r = (int) (128 + 100 * Math.sin(x / 9.0));
                int g = (int) (128 + 100 * Math.cos(y / 7.0));
                int b = (x * 3 + y * 2) & 0xff;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return dataUrl(image, "png", "image/png");
    }

    /** A round logo on a transparent background. */
    static String pngLogo() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xdc2626));
        g.fillOval(4, 4, 56, 56);
        g.setColor(Color.WHITE);
        g.fillRect(28, 14, 8, 36);
        g.dispose();
        return dataUrl(image, "png", "image/png");
    }

    static String jpegLogo() {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x065f46));
        g.fillRect(0, 0, 48, 48);
        g.setColor(new Color(0xfacc15));
        g.fillRect(12, 12, 24, 24);
        g.dispose();
        return dataUrl(image, "jpeg", "image/jpeg");
    }

    static String dataUrl(BufferedImage image, String format, String mediaType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
