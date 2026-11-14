package org.skrall.qrcode.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.service.QrCodeService;

class PngResolutionTest {

    private final QrCodeService service = new QrCodeService(
            new PngQrRenderer(), new SvgQrRenderer(), new PdfQrRenderer(), new ScanChecker());

    @Test
    void writesThePhysChunkRightAfterTheHeader() {
        byte[] png = service.render(RenderFixtures.request("{\"size\":300}"), QrFormat.PNG, 300);

        ByteBuffer buffer = ByteBuffer.wrap(png);
        assertThat(buffer.getInt(33)).as("length").isEqualTo(9);
        assertThat(new String(png, 37, 4, StandardCharsets.US_ASCII)).isEqualTo("pHYs");
        // 300 DPI is 11811 pixels per metre, in both directions, unit metre.
        assertThat(buffer.getInt(41)).isEqualTo(11811);
        assertThat(buffer.getInt(45)).isEqualTo(11811);
        assertThat(png[49]).isEqualTo((byte) 1);
        CRC32 crc = new CRC32();
        crc.update(png, 37, 13);
        assertThat(buffer.getInt(50)).isEqualTo((int) crc.getValue());
    }

    @Test
    void imageReadersSeeTheResolutionAndTheSamePixels() throws IOException {
        byte[] plain = service.render(RenderFixtures.all().get("frame-bleed"), QrFormat.PNG);
        byte[] printed = service.render(RenderFixtures.all().get("frame-bleed"), QrFormat.PNG, 600);

        assertThat(printed.length).isEqualTo(plain.length + 21);
        assertThat(physOf(plain)).isNull();
        IIOMetadataNode phys = physOf(printed);
        assertThat(phys).isNotNull();
        assertThat(phys.getAttribute("pixelsPerUnitXAxis")).isEqualTo("23622");
        assertThat(phys.getAttribute("unitSpecifier")).isEqualTo("meter");

        BufferedImage a = ImageIO.read(new ByteArrayInputStream(plain));
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(printed));
        assertThat(b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()))
                .isEqualTo(a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth()));
    }

    private static IIOMetadataNode physOf(byte[] png) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
            reader.setInput(in);
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(0)
                    .getAsTree("javax_imageio_png_1.0");
            reader.dispose();
            var nodes = root.getElementsByTagName("pHYs");
            return nodes.getLength() == 0 ? null : (IIOMetadataNode) nodes.item(0);
        }
    }
}
