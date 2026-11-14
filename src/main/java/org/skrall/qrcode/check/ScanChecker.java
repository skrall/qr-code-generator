package org.skrall.qrcode.check;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.ScanCheckResponse;
import org.skrall.qrcode.model.ScanTrial;
import org.skrall.qrcode.model.ScanVerdict;
import org.skrall.qrcode.render.LogoImage;
import org.skrall.qrcode.render.RenderOptions;
import org.springframework.stereotype.Component;

/**
 * Reads a rendered code back, as drawn and as a phone camera might see it.
 *
 * <p>A styled code that decodes once at full resolution can still fail on a phone: the camera
 * sees it small, a little out of focus, and gives up quickly. So the check makes three attempts
 * with a quick reader (no {@code TRY_HARDER}): the image as rendered, the image shrunk to a few
 * pixels per module, and the image shrunk less but blurred. A code that only decodes with extra
 * effort, or only in some of those, is marginal rather than a pass.
 *
 * <p>The hints come from the style, not from the reader: ZXing says whether it decoded, never
 * why not. They are offered only when the verdict is not a pass.
 */
@Component
public class ScanChecker {

    /** Pixels per module in the "small" attempt: a code a few centimetres wide across a room. */
    static final double SMALL_MODULE_PIXELS = 3.0;

    /** Pixels per module in the "blurred" attempt, before the blur is applied. */
    static final double BLURRED_MODULE_PIXELS = 4.0;

    /** Luminance distance, on the renderer's 0–255 scale, below which contrast is called low. */
    private static final double LOW_CONTRAST = 128;

    public ScanCheckResponse check(
            BufferedImage rendered, String data, BitMatrix matrix, RenderOptions options) {
        int modules = matrix.getWidth();
        int version = (modules - 17) / 4;
        double modulePixels = (double) options.size() / (modules + options.margin() * 2);
        boolean inverted = inverted(options);

        BufferedImage flat = flatten(rendered, options.background());
        boolean quick = decodes(flat, data, false, inverted);
        boolean hard = quick || decodes(flat, data, true, inverted);

        BufferedImage small = scale(flat, SMALL_MODULE_PIXELS / modulePixels);
        BufferedImage blurred = blur(scale(flat, BLURRED_MODULE_PIXELS / modulePixels));

        List<ScanTrial> trials = List.of(
                new ScanTrial("rendered", "As rendered", quick),
                new ScanTrial("small", "Small in the frame", decodes(small, data, false, inverted)),
                new ScanTrial("blurred", "Slightly out of focus", decodes(blurred, data, false, inverted)));

        boolean any = hard || trials.stream().anyMatch(ScanTrial::decoded);
        boolean all = quick && trials.stream().allMatch(ScanTrial::decoded);
        ScanVerdict verdict;
        if (!any) {
            verdict = ScanVerdict.FAIL;
        } else if (!all || inverted || shortQuietZone(options)) {
            // Two style choices are capped even when every trial reads them. The trials look for
            // an inverted code because they are told to, but plenty of scanner apps never do. And
            // a thin quiet zone reads fine on the blank canvas here, but not once the image sits
            // on a busy page.
            verdict = ScanVerdict.MARGINAL;
        } else {
            verdict = ScanVerdict.PASS;
        }

        List<String> hints = verdict == ScanVerdict.PASS
                ? List.of()
                : hints(options, modules, version, modulePixels, inverted);
        return new ScanCheckResponse(verdict, version, modules, trials, hints);
    }

    private static List<String> hints(
            RenderOptions options, int modules, int version, double modulePixels, boolean inverted) {
        List<String> hints = new ArrayList<>();

        if (inverted) {
            hints.add("The modules are lighter than the background. Phone cameras usually read "
                    + "that, but many scanner apps do not. Use dark modules on a light background.");
        }

        LogoImage logo = options.logo();
        if (logo != null && options.errorCorrection() != ErrorCorrection.HIGH) {
            hints.add("A logo hides part of the code. Switch to High error correction so the "
                    + "hidden modules can be recovered.");
        }

        double contrast = contrast(options);
        if (contrast < LOW_CONTRAST) {
            hints.add((options.gradient() == null ? "The module" : "The gradient's lighter")
                    + " and background colours are close in brightness. Darken the modules or "
                    + "lighten the background.");
        }

        if (shortQuietZone(options)) {
            hints.add("The quiet zone is " + options.margin() + " module"
                    + (options.margin() == 1 ? "" : "s") + " wide; readers expect 4. Widen the margin.");
        }

        if (logo != null) {
            int covered = (int) Math.round(logoCoverage(options, logo, modules) * 100);
            int recoverable = recoverablePercent(options.errorCorrection());
            if (covered * 2 > recoverable) {
                hints.add("The logo and its plate cover about " + covered + "% of the code, and "
                        + options.errorCorrection().name().toLowerCase() + " error correction "
                        + "recovers at most " + recoverable + "%. Make the logo smaller or reduce "
                        + "its padding.");
            }
        }

        if (version >= 10) {
            hints.add("The content needs a " + modules + "×" + modules + " grid, so each module is "
                    + "small. Shorter content, or a short link, gives a coarser code.");
        }

        if (modulePixels < 4) {
            hints.add(String.format("Each module is only %.1f pixels wide. Increase the size.",
                    modulePixels));
        }

        if (options.moduleShape() == ModuleShape.DOTS || options.moduleShape() == ModuleShape.STAR) {
            hints.add((options.moduleShape() == ModuleShape.DOTS ? "Dots" : "Stars")
                    + " fill less of each module than squares. Try square or rounded modules.");
        }

        if (options.finderShape() != FinderShape.SQUARE && options.finderShape() != FinderShape.ROUNDED) {
            hints.add("Readers find the code by its three corner patterns. Try square or rounded "
                    + "corner patterns.");
        }

        if (options.backgroundImage() != null) {
            hints.add("The background image adds texture behind the modules. Try a plainer image, "
                    + "or none.");
        }

        // When nothing above explains it, more redundancy is still the general-purpose fix.
        if (options.errorCorrection() != ErrorCorrection.HIGH && hints.isEmpty()) {
            hints.add("Raise the error correction level so damaged or blurred modules can be "
                    + "recovered.");
        }

        // Not a cause, so it does not stand in for one above: it says what the verdict assumes.
        if (options.transparentBackground()) {
            hints.add("The background is transparent, so this check reads the code on "
                    + RenderOptions.hex(options.background()) + ", the background colour. Place it on a"
                    + " plain surface close to that colour, with the quiet zone clear of other artwork.");
        }

        return hints;
    }

    /**
     * True when an unframed code has less than two modules of quiet zone. A frame already
     * requires its own clear space around the code.
     */
    private static boolean shortQuietZone(RenderOptions options) {
        return options.frame() == null && options.margin() < 2;
    }

    /** True when the modules are lighter than the background. */
    private static boolean inverted(RenderOptions options) {
        Color ink = options.gradient() == null ? options.foreground() : options.gradient().start();
        return luminance(ink) > luminance(options.background());
    }

    /** The smallest distance between an ink drawn on the modules and the background. */
    private static double contrast(RenderOptions options) {
        double background = luminance(options.background());
        double contrast = Math.abs(luminance(options.foreground()) - background);
        if (options.gradient() != null) {
            contrast = Math.min(
                    Math.abs(luminance(options.gradient().start()) - background),
                    Math.abs(luminance(options.gradient().end()) - background));
        }
        if (options.finderArtwork() == null) {
            contrast = Math.min(contrast, Math.abs(luminance(options.finderColor()) - background));
        }
        return contrast;
    }

    /** The share of the symbol, without its quiet zone, under the logo's plate. */
    private static double logoCoverage(RenderOptions options, LogoImage logo, int modules) {
        double side = options.size() * logo.sizePercent() / 100.0;
        double plate = side * (1 + 2 * logo.paddingPercent() / 100.0);
        double symbol = options.size() * modules / (double) (modules + options.margin() * 2);
        return Math.min(1, (plate * plate) / (symbol * symbol));
    }

    private static int recoverablePercent(ErrorCorrection level) {
        return switch (level) {
            case LOW -> 7;
            case MEDIUM -> 15;
            case QUARTILE -> 25;
            case HIGH -> 30;
        };
    }

    private static double luminance(Color color) {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
    }

    private static boolean decodes(BufferedImage image, String data, boolean tryHarder, boolean inverted) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        if (tryHarder) {
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        }
        if (inverted) {
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        }
        try {
            // MultiFormatReader rather than QRCodeReader: only it honours ALSO_INVERTED.
            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                    hints);
            return data.equals(result.getText());
        } catch (ReaderException e) {
            return false;
        }
    }

    /**
     * Draws the image over the background colour, so transparent pixels read as the surface the
     * code is designed to sit on rather than as black.
     *
     * <p>Only a transparent background leaves such pixels, and its {@code background} is that
     * surface: a light-on-dark design is judged on the dark it was made for, not on white.
     */
    private static BufferedImage flatten(BufferedImage image, Color background) {
        BufferedImage flat =
                new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flat.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flat;
    }

    /**
     * Shrinks the image by averaging, the way a camera sensor bins light. An image that is
     * already at or below the target resolution is returned as it is.
     */
    private static BufferedImage scale(BufferedImage image, double factor) {
        if (factor >= 1) {
            return image;
        }
        int width = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(image.getHeight() * factor));
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(scaled, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /** Two passes of a 3×3 box blur: roughly a Gaussian with a sigma of a quarter module. */
    private static BufferedImage blur(BufferedImage image) {
        float ninth = 1f / 9f;
        ConvolveOp op = new ConvolveOp(
                new Kernel(3, 3, new float[] {ninth, ninth, ninth, ninth, ninth, ninth, ninth, ninth, ninth}),
                ConvolveOp.EDGE_NO_OP, null);
        return op.filter(op.filter(image, null), null);
    }
}
