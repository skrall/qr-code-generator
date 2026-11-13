package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.skrall.qrcode.link.LinkProperties;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkService;
import org.skrall.qrcode.model.CreateLinkRequest;
import org.skrall.qrcode.model.LinkPageResponse;
import org.skrall.qrcode.model.ShortLinkResponse;
import org.skrall.qrcode.model.UpdateLinkRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stores destinations and hands back the short links that redirect to them.
 */
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final ShortLinkService service;
    private final LinkProperties properties;

    public LinkController(ShortLinkService service, LinkProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Create a short link", description = """
            Stores the destination and returns a link that redirects to it. The destination must
            be an absolute http or https URL; other schemes are rejected so that a code cannot
            become a script or a data URL. The short URL is composed from the configured public
            base URL, so the caller does not have to know how this service is deployed.
            """)
    @ApiResponse(responseCode = "201", description = "The stored link")
    @ApiResponse(responseCode = "400", description = "The destination was rejected")
    public ResponseEntity<ShortLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        ShortLink link = service.create(request.url(), request.expiresAt());
        // The short URL is the address of the thing that was created; there is no single-link
        // GET endpoint for /api/links/{code} to point at.
        return ResponseEntity.created(URI.create(properties.shortUrl(link.getCode())))
                .body(ShortLinkResponse.from(link, properties));
    }

    @GetMapping
    @Operation(summary = "List short links", description = """
            Newest first. `q` matches the destination or the code, case-insensitively. Deleted
            links are not listed.
            """)
    public LinkPageResponse list(
            @Parameter(description = "Text to match against the destination or code")
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<ShortLink> links = service.list(query, page, size);
        return new LinkPageResponse(
                links.map(link -> ShortLinkResponse.from(link, properties)).getContent(),
                links.getTotalElements(),
                links.getNumber(),
                links.getSize());
    }

    @PutMapping("/{code}")
    @Operation(summary = "Edit a short link", description = """
            Changes the destination and expiry without changing the code. A code that has
            already been printed therefore starts pointing at the new destination. Deleted
            links cannot be edited; an expired link can be revived with a future expiry.
            """)
    @ApiResponse(responseCode = "200", description = "The updated link")
    @ApiResponse(responseCode = "400", description = "The destination or expiry was rejected")
    @ApiResponse(responseCode = "404", description = "No live link has that code")
    public ResponseEntity<ShortLinkResponse> update(
            @PathVariable String code,
            @Valid @RequestBody UpdateLinkRequest request) {

        return service.update(code, request.url(), request.expiresAt())
                .map(link -> ResponseEntity.ok(ShortLinkResponse.from(link, properties)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete a short link", description = """
            Soft deletes the link. The code then answers 410 Gone rather than 404, because a
            printed code that stops working is not the same as an address that never existed.
            """)
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No live link has that code")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        return service.delete(code)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
