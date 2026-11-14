package org.skrall.qrcode.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the recorded Java2D metadata against being regenerated from a run that missed a path.
 *
 * <p>The file is recorded with the tracing agent, which only sees code that ran. A recording made
 * with PNG uploads alone shipped without the JPEG decoder's JNI callbacks, and every JPEG logo or
 * background image then failed in the native image with
 * {@code NoSuchMethodError: JPEGImageReader.readInputData([BII)I}. Each entry checked here is one
 * that a native image has already failed without.
 */
class Java2dMetadataTest {

    private static final String RESOURCE =
            "/META-INF/native-image/org.skrall/qr-code-generator-java2d/reachability-metadata.json";

    private final JsonNode metadata = read();

    @Test
    void jpegDecodingCanCallBackIntoJava() {
        assertThat(jniMethods("com.sun.imageio.plugins.jpeg.JPEGImageReader"))
                .contains("readInputData", "skipInputBytes", "setImageData", "acceptPixels");
        assertThat(entry("javax.imageio.plugins.jpeg.JPEGQTable")).isNotNull();
        assertThat(entry("javax.imageio.plugins.jpeg.JPEGHuffmanTable")).isNotNull();
    }

    @Test
    void imageScalingCanReachItsNativeFields() {
        assertThat(jniFields("sun.awt.image.ImageRepresentation")).contains("numSrcLUT");
    }

    @Test
    void textShapingCanCallHarfBuzz() {
        List<String> upcalls = new ArrayList<>();
        metadata.path("foreign").path("directUpcalls")
                .forEach(upcall -> upcalls.add(upcall.path("method").asString()));
        assertThat(upcalls).contains("store_layout_results", "get_glyph_h_advance");
        assertThat(metadata.path("foreign").path("downcalls").size()).isPositive();
    }

    private JsonNode entry(String type) {
        for (JsonNode node : metadata.path("reflection")) {
            if (type.equals(node.path("type").asString())) {
                return node;
            }
        }
        return null;
    }

    private List<String> jniMethods(String type) {
        JsonNode node = entry(type);
        assertThat(node).as(type).isNotNull();
        assertThat(node.path("jniAccessible").asBoolean()).as("%s is JNI accessible", type).isTrue();
        List<String> names = new ArrayList<>();
        node.path("methods").forEach(method -> names.add(method.path("name").asString()));
        return names;
    }

    private List<String> jniFields(String type) {
        JsonNode node = entry(type);
        assertThat(node).as(type).isNotNull();
        List<String> names = new ArrayList<>();
        node.path("fields").forEach(field -> names.add(field.path("name").asString()));
        return names;
    }

    private static JsonNode read() {
        try (InputStream in = Java2dMetadataTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as(RESOURCE).isNotNull();
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
