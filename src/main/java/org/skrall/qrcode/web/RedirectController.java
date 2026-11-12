package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of a short link: a scan lands here and is sent on to the stored destination.
 *
 * <p>302 rather than 301 on purpose. A permanent redirect is cached by the browser, which would
 * make a link impossible to retarget or revoke — the two things a dynamic code exists for.
 * {@code no-store} keeps every scan reaching the server, so hit counts and expiry stay honest.
 */
@RestController
public class RedirectController {

    private final ShortLinkService service;

    public RedirectController(ShortLinkService service) {
        this.service = service;
    }

    @GetMapping("/r/{code}")
    @Operation(summary = "Follow a short link", description = """
            Redirects to the stored destination. An unknown code is 404; an expired or deleted
            one is 410 Gone.
            """)
    @ApiResponse(responseCode = "302", description = "Redirect to the destination")
    @ApiResponse(responseCode = "404", description = "No link has that code")
    @ApiResponse(responseCode = "410", description = "The link expired or was deleted")
    public ResponseEntity<String> redirect(@PathVariable String code) {
        ShortLinkService.Resolution resolution = service.resolve(code);

        return switch (resolution.status()) {
            case FOUND -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(resolution.targetUrl()))
                    .cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer")
                    .header("X-Robots-Tag", "noindex")
                    .build();
            case NOT_FOUND -> page(HttpStatus.NOT_FOUND,
                    "This link does not exist.",
                    "Check the address, or ask whoever gave you the code for a new one.");
            case GONE -> page(HttpStatus.GONE,
                    "This link has expired or been removed.",
                    "The code was valid once, but it no longer points anywhere.");
        };
    }

    /**
     * A small HTML page rather than a problem document: the reader here is a person holding a
     * phone, not a client parsing JSON.
     */
    private static ResponseEntity<String> page(HttpStatus status, String title, String detail) {
        String body = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%1$s</title>
                </head>
                <body style="margin:0;display:grid;place-items:center;min-height:100vh;font:16px/1.6 system-ui,sans-serif;background:#f1f4f9;color:#131a24">
                  <main style="max-width:32rem;padding:2rem;text-align:center">
                    <h1 style="font-size:1.35rem;margin:0 0 .5rem">%1$s</h1>
                    <p style="margin:0;color:#5c6a7d">%2$s</p>
                  </main>
                </body>
                </html>
                """.formatted(title, detail);

        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
