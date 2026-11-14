package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

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
            one is 410 Gone with a short page (and the link's message, if it has one), or a 302
            to the link's fallback URL, or to `app.links.gone-url`, when one is set.
            """)
    @ApiResponse(responseCode = "302", description = "Redirect to the destination, or to the fallback of an expired or deleted link")
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
                    "This link does not exist",
                    "Check that the code was scanned or typed in full. If it came from a poster or"
                            + " a card, ask whoever made it for a new one.");
            case GONE -> resolution.targetUrl() != null
                    ? fallback(resolution.targetUrl())
                    : page(HttpStatus.GONE,
                            "This link is no longer active",
                            resolution.message() != null
                                    ? resolution.message()
                                    : "It has expired or been removed by whoever set it up. The code"
                                            + " was valid once, but it no longer leads anywhere.");
        };
    }

    /**
     * An expired or deleted link with a fallback. Same headers as a live redirect, so the
     * fallback is not cached and the gone code does not leak through the referrer.
     */
    private static ResponseEntity<String> fallback(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .build();
    }

    /**
     * The page's whole stylesheet. Inline so the page has no other request to make, and pinned by
     * hash in the CSP so nothing else can style or script it.
     */
    private static final String STYLE = """
            :root{color-scheme:light dark;--bg:#f1f4f9;--card:#fff;--text:#131a24;--muted:#5c6a7d;\
            --border:#d9dfe8;--accent:#3158c4;--accent-soft:#e7edfb}\
            @media (prefers-color-scheme:dark){:root{--bg:#0d1219;--card:#151c26;--text:#e7ecf4;\
            --muted:#9aa7b8;--border:#2b3542;--accent:#7ea8ff;--accent-soft:#1e2a41}}\
            *{box-sizing:border-box}\
            body{margin:0;min-height:100vh;min-height:100dvh;display:grid;place-items:center;\
            padding:1rem;background:var(--bg);color:var(--text);\
            font:1rem/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;\
            -webkit-text-size-adjust:100%}\
            main{width:100%;max-width:26rem;padding:2rem 1.5rem;text-align:center;\
            background:var(--card);border:1px solid var(--border);border-radius:14px}\
            .mark{width:3rem;height:3rem;margin:0 auto 1rem;border-radius:50%;display:grid;\
            place-items:center;background:var(--accent-soft);color:var(--accent)}\
            h1{font-size:1.3rem;line-height:1.3;margin:0 0 .6rem}\
            p{margin:0;color:var(--muted);white-space:pre-line;overflow-wrap:anywhere}\
            .status{margin-top:1.5rem;font-size:.8rem}""";

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src '"
            + sha256(STYLE) + "'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    /**
     * A small HTML page rather than a problem document: the reader here is a person holding a
     * phone, not a client parsing JSON.
     *
     * <p>{@code detail} may be a link owner's own message, so everything put into the page is
     * HTML-escaped; the CSP allows no scripts in case that ever slips. Nothing about the old
     * destination is shown.
     */
    static ResponseEntity<String> page(HttpStatus status, String title, String detail) {
        String body = """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>%1$s</title>
                <style>%4$s</style>
                </head>
                <body>
                <main>
                <div class="mark" aria-hidden="true"><svg width="24" height="24" viewBox="0 0 24 24" \
                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" \
                stroke-linejoin="round"><path d="M9 17H7a5 5 0 0 1 0-10h2"/><path d="M15 7h2a5 5 0 0 1 \
                4.5 7.2"/><path d="M8 12h3"/><path d="m3 3 18 18"/></svg></div>
                <h1>%1$s</h1>
                <p>%2$s</p>
                <p class="status">Error %3$d</p>
                </main>
                </body>
                </html>
                """.formatted(escape(title), escape(detail), status.value(), STYLE);

        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    /** Escapes {@code < > & " '} only; the page is UTF-8, so other text stays as it is. */
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256-" + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
