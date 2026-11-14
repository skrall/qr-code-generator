package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import org.skrall.qrcode.model.CurrentUserResponse;
import org.skrall.qrcode.security.Caller;
import org.skrall.qrcode.security.CallerResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the UI who is signed in and whether they hold the OPERATOR role.
 *
 * <p>The server still enforces the role on every change; this only lets the UI hide buttons that
 * would answer 403, rather than offering them and failing.
 */
@RestController
public class CurrentUserController {

    private final CallerResolver callers;

    public CurrentUserController(CallerResolver callers) {
        this.callers = callers;
    }

    @GetMapping("/api/me")
    @Operation(summary = "The signed-in user", description = """
            The user's name and whether they may create, edit and delete short links. With
            security off, the name is null and every action is allowed.
            """)
    public CurrentUserResponse me() {
        Caller caller = callers.current();
        return new CurrentUserResponse(caller.name(), caller.operator());
    }
}
