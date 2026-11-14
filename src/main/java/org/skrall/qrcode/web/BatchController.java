package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.skrall.qrcode.batch.BatchService;
import org.skrall.qrcode.batch.InvalidBatchException;
import org.skrall.qrcode.model.BatchOptions;
import org.skrall.qrcode.security.CallerResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Renders every row of a CSV with one style and answers a ZIP.
 *
 * <p>Multipart rather than a {@code text/csv} body: the options carry a style, which with images
 * runs to megabytes and has no place in a query string, and a browser can send the chosen file
 * as it is. The CSV and the style are checked before the response starts, so those failures are
 * still problem documents; the archive is then written as it renders.
 */
@RestController
@RequestMapping("/api/qr-codes")
public class BatchController {

    public static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final BatchService service;
    private final CallerResolver callers;

    public BatchController(BatchService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Render a CSV of payloads into a ZIP", description = """
            Multipart: a `csv` file with a header row naming a `data` column and optionally a
            `filename` column, and an optional `options` JSON part:
            `{"format": "png" | "svg" | "both", "style": {...}}` or `{"format": ..., "designId": 7}`.
            Up to 500 rows and 1 MB. Rows that cannot be rendered are skipped and listed in the
            archive's report.csv.
            """)
    @ApiResponse(responseCode = "200", description = "The ZIP: one image per row and format, then report.csv",
            content = @Content(mediaType = "application/zip"))
    @ApiResponse(responseCode = "400", description = "The CSV, the limits or the style were rejected",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "404", description = "No design with that id is visible to the caller",
            content = @Content(mediaType = "application/problem+json"))
    public ResponseEntity<StreamingResponseBody> batch(
            @Parameter(description = "The CSV, UTF-8") @RequestPart("csv") MultipartFile csv,
            @Valid @RequestPart(name = "options", required = false) BatchOptions options)
            throws IOException {

        if (csv.getSize() > BatchService.MAX_CSV_BYTES) {
            throw new InvalidBatchException("The CSV must be at most 1 MB.");
        }
        BatchService.Batch batch = service.prepare(csv.getBytes(), options, callers.current());
        StreamingResponseBody body = batch::writeTo;
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("qr-codes.zip").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
