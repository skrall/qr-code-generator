package org.skrall.qrcode.design;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.skrall.qrcode.model.CreateDesignRequest;
import org.skrall.qrcode.model.DesignResponse;
import org.skrall.qrcode.model.DesignSummary;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.model.UpdateDesignRequest;
import org.skrall.qrcode.security.Caller;
import org.skrall.qrcode.service.QrCodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saves and loads designs, and decides who may see and change each one.
 *
 * <p>A caller sees their own designs and every shared one. A private design of someone else's is
 * treated as absent, even by an operator, so "private" means what it says. Changing a design is
 * for its owner, and for an operator when the design is shared: a shared design is team property,
 * and someone has to be able to tidy the library.
 */
@Service
public class DesignService {

    private final DesignRepository repository;
    private final QrCodeService qrCodes;
    private final JsonMapper json;

    public DesignService(DesignRepository repository, QrCodeService qrCodes, JsonMapper json) {
        this.repository = repository;
        this.qrCodes = qrCodes;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<DesignSummary> list(Caller caller) {
        return repository.findVisibleTo(caller.id()).stream()
                .map(listing -> new DesignSummary(listing.id(), listing.name(), listing.ownerName(),
                        listing.shared(), isMine(listing.owner(), caller),
                        isEditable(listing.owner(), listing.shared(), caller),
                        listing.createdAt(), listing.updatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DesignResponse get(long id, Caller caller) {
        return response(visible(id, caller), caller);
    }

    @Transactional
    public DesignResponse create(CreateDesignRequest request, Caller caller) {
        qrCodes.validate(request.style());
        Design design = new Design(request.name().strip(), caller.id(), caller.name(),
                Boolean.TRUE.equals(request.shared()), write(request.style()), Instant.now());
        return response(repository.save(design), caller);
    }

    @Transactional
    public DesignResponse update(long id, UpdateDesignRequest request, Caller caller) {
        Design design = editable(id, caller);
        if (request.style() != null) {
            qrCodes.validate(request.style());
        }
        design.update(request.name().strip(),
                request.shared() == null ? design.isShared() : request.shared(),
                request.style() == null ? null : write(request.style()),
                Instant.now());
        return response(design, caller);
    }

    @Transactional
    public void delete(long id, Caller caller) {
        repository.delete(editable(id, caller));
    }

    private Design visible(long id, Caller caller) {
        return repository.findById(id)
                .filter(design -> design.isShared() || isMine(design.getOwner(), caller))
                .orElseThrow(() -> new DesignNotFoundException(id));
    }

    private Design editable(long id, Caller caller) {
        Design design = visible(id, caller);
        if (!isEditable(design.getOwner(), design.isShared(), caller)) {
            throw new DesignNotEditableException();
        }
        return design;
    }

    private static boolean isMine(String owner, Caller caller) {
        return Objects.equals(owner, caller.id());
    }

    private static boolean isEditable(String owner, boolean shared, Caller caller) {
        return isMine(owner, caller) || (shared && caller.operator());
    }

    private DesignResponse response(Design design, Caller caller) {
        return new DesignResponse(design.getId(), design.getName(), design.getOwnerName(),
                design.isShared(), isMine(design.getOwner(), caller),
                isEditable(design.getOwner(), design.isShared(), caller),
                design.getCreatedAt(), design.getUpdatedAt(),
                json.readValue(design.getStyle(), QrStyle.class));
    }

    private String write(QrStyle style) {
        return json.writeValueAsString(style);
    }
}
