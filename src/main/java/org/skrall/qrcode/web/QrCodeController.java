package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.ScanCheckResponse;
import org.skrall.qrcode.service.QrCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders a QR code from a payload and a style.
 *
 * <p>The response is the image itself rather than JSON carrying base64, so the UI can show it
 * with an object URL and a download is just the same request with a save dialog.
 */
@RestController
@RequestMapping("/api/qr-codes")
public class QrCodeController {

    private final QrCodeService service;

    public QrCodeController(QrCodeService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Render a QR code", description = """
            Encodes the supplied payload and returns it as a PNG, an SVG or a one-page vector PDF.
            The payload is whatever the caller composed: a URL, a vCard, a WiFi string and so on.
            The style is optional and every absent field falls back to a plain black-on-white
            code. `dpi` is the print resolution: it sizes the PDF's page (300 when absent) and is
            recorded in the PNG; an SVG ignores it.
            """)
    @ApiResponse(responseCode = "200", description = "The rendered image",
            content = {
                @Content(mediaType = "image/png"),
                @Content(mediaType = "image/svg+xml"),
                @Content(mediaType = "application/pdf")
            })
    @ApiResponse(responseCode = "400", description = "The payload, the style or the dpi was rejected",
            content = @Content(mediaType = "application/problem+json"))
    public ResponseEntity<byte[]> render(
            @Valid @RequestBody QrCodeRequest request,
            @Parameter(description = "The output format: png, svg or pdf", example = "png")
            @RequestParam(defaultValue = "png") QrFormat format,
            @Parameter(description = "Print resolution, 72 to 1200 pixels per inch. Sizes a PDF's"
                    + " page (default 300) and is written into a PNG's pHYs chunk (none when"
                    + " absent); ignored for SVG.", example = "300")
            @RequestParam(required = false) Integer dpi) {

        byte[] image = service.render(request, format, dpi);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qr-code." + format.extension() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(image);
    }

    @PostMapping(path = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Check that a QR code scans", description = """
            Renders the code exactly as the render endpoint would, then reads it back: as
            rendered, shrunk to a few pixels per module, and blurred. PASS means every attempt
            recovered the payload, MARGINAL that some did, FAIL that none did. Hints suggest style
            changes when the verdict is not a pass.
            """)
    @ApiResponse(responseCode = "200", description = "The verdict, the attempts and any hints")
    @ApiResponse(responseCode = "400", description = "The payload or style was rejected",
            content = @Content(mediaType = "application/problem+json"))
    public ResponseEntity<ScanCheckResponse> check(@Valid @RequestBody QrCodeRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.check(request));
    }
}
