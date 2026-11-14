package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.skrall.qrcode.design.DesignService;
import org.skrall.qrcode.model.CreateDesignRequest;
import org.skrall.qrcode.model.DesignResponse;
import org.skrall.qrcode.model.DesignSummary;
import org.skrall.qrcode.model.UpdateDesignRequest;
import org.skrall.qrcode.security.CallerResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved designs: named styles, private to their owner unless shared.
 *
 * <p>Any signed-in user may save designs; the VIEWER/OPERATOR split governs short links, not
 * styles. Who may change a design is decided per design in {@link DesignService}.
 */
@RestController
@RequestMapping("/api/designs")
public class DesignController {

    private final DesignService service;
    private final CallerResolver callers;

    public DesignController(DesignService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    @GetMapping
    @Operation(summary = "List designs", description = """
            The caller's own designs and every shared one, by name, without their styles: a
            style with images can run to megabytes. Load one by id to get its style.
            """)
    public List<DesignSummary> list() {
        return service.list(callers.current());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Load a design", description = "The design with its style, images included.")
    @ApiResponse(responseCode = "200", description = "The design")
    @ApiResponse(responseCode = "404", description = "No design with that id is visible to the caller")
    public DesignResponse get(@PathVariable long id) {
        return service.get(id, callers.current());
    }

    @PostMapping
    @Operation(summary = "Save a design", description = """
            Saves a style under a name, private unless shared is true. The style is checked as
            the renderer would check it, so a design that cannot render is refused here.
            """)
    @ApiResponse(responseCode = "201", description = "The saved design")
    @ApiResponse(responseCode = "400", description = "The name or style was rejected")
    public ResponseEntity<DesignResponse> create(@Valid @RequestBody CreateDesignRequest request) {
        DesignResponse design = service.create(request, callers.current());
        return ResponseEntity.created(URI.create("/api/designs/" + design.id())).body(design);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a design", description = """
            Renames it, shares or unshares it, and replaces its style when one is sent. Leaving
            out the style keeps the saved one, so renaming does not resend the images.
            """)
    @ApiResponse(responseCode = "200", description = "The changed design")
    @ApiResponse(responseCode = "400", description = "The name or style was rejected")
    @ApiResponse(responseCode = "403", description = "The caller may see the design but not change it")
    @ApiResponse(responseCode = "404", description = "No design with that id is visible to the caller")
    public DesignResponse update(@PathVariable long id, @Valid @RequestBody UpdateDesignRequest request) {
        return service.update(id, request, callers.current());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a design")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "The caller may see the design but not delete it")
    @ApiResponse(responseCode = "404", description = "No design with that id is visible to the caller")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id, callers.current());
        return ResponseEntity.noContent().build();
    }
}
