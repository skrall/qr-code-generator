package org.skrall.qrcode.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates short links and resolves them for the redirect endpoint.
 */
@Service
public class ShortLinkService {

    /** Enough retries to cover a collision, which should never happen at this code length. */
    private static final int CODE_ATTEMPTS = 5;

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_TARGET_LENGTH = 2048;

    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    private final ShortLinkRepository repository;
    private final LinkCodeGenerator codeGenerator;
    private final LinkProperties properties;

    public ShortLinkService(ShortLinkRepository repository, LinkCodeGenerator codeGenerator,
            LinkProperties properties) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
    }

    @Transactional
    public ShortLink create(String url, Instant expiresAt) {
        String target = targetUrl(url);
        Instant now = Instant.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidLinkException("expiresAt must be in the future");
        }

        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (repository.existsByCode(code)) {
                continue;
            }
            return repository.save(new ShortLink(code, target, now, expiresAt));
        }
        throw new IllegalStateException("Could not allocate a unique link code");
    }

    /**
     * Changes the destination and expiry of a live link, keeping its code.
     *
     * <p>This is what makes a printed code reusable: the short URL stays the same, so a poster
     * can be repointed without being reprinted. Deleted links are not editable. An expired link
     * can be revived by giving it a future expiry or by clearing it.
     *
     * @return the updated link, or empty when no live link has that code
     */
    @Transactional
    public Optional<ShortLink> update(String code, String url, Instant expiresAt) {
        Optional<ShortLink> found = repository.findByCode(code).filter(link -> !link.isDeleted());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        String target = targetUrl(url);
        Instant now = Instant.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidLinkException("expiresAt must be in the future");
        }

        found.get().update(target, expiresAt);
        return found;
    }

    /**
     * Looks a code up for the redirect endpoint, counting the hit when the link is usable.
     *
     * <p>Deleted and expired links are still found so the caller can tell {@code 410} from
     * {@code 404}.
     */
    @Transactional
    public Resolution resolve(String code) {
        Optional<ShortLink> found = repository.findByCode(code);
        if (found.isEmpty()) {
            return new Resolution(Status.NOT_FOUND, null);
        }

        ShortLink link = found.get();
        Instant now = Instant.now();
        if (link.isDeleted() || link.isExpired(now)) {
            return new Resolution(Status.GONE, null);
        }

        link.recordHit(now);
        return new Resolution(Status.FOUND, link.getTargetUrl());
    }

    @Transactional(readOnly = true)
    public Page<ShortLink> list(String query, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        String trimmed = query == null ? "" : query.strip();
        return trimmed.isEmpty()
                ? repository.findByDeletedAtIsNull(pageable)
                : repository.search(trimmed, pageable);
    }

    /** Soft deletes the link. Returns false when there is nothing live to delete. */
    @Transactional
    public boolean delete(String code) {
        return repository.findByCode(code)
                .filter(link -> !link.isDeleted())
                .map(link -> {
                    link.delete(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    public String shortUrl(ShortLink link) {
        return properties.shortUrl(link.getCode());
    }

    /**
     * Validates and normalizes a destination.
     *
     * <p>Only absolute http and https URLs are accepted. Without that check the redirector would
     * happily send a scanner to {@code javascript:} or {@code data:}, turning a QR code into an
     * XSS gadget, and credentials in the URL would be copied into a {@code Location} header.
     * Non-ASCII paths are percent-encoded so the header stays ASCII.
     */
    static String targetUrl(String raw) {
        String target = raw == null ? "" : raw.strip();
        if (target.isEmpty()) {
            throw new InvalidLinkException("url must not be blank");
        }
        if (target.length() > MAX_TARGET_LENGTH) {
            throw new InvalidLinkException("url must be at most " + MAX_TARGET_LENGTH + " characters");
        }

        // Check the scheme before parsing: `data:` and `javascript:` payloads are often not
        // valid URIs at all, and "not a valid URL" would hide the reason they were rejected.
        Matcher matcher = SCHEME.matcher(target);
        if (!matcher.find()) {
            throw new InvalidLinkException("url must be an absolute http or https URL");
        }
        String scheme = matcher.group(1);
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new InvalidLinkException("url must be an absolute http or https URL");
        }

        URI uri;
        try {
            uri = new URI(target);
        } catch (URISyntaxException e) {
            throw new InvalidLinkException("url is not a valid absolute URL");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidLinkException("url must name a host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidLinkException("url must not contain credentials");
        }

        return uri.toASCIIString();
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        GONE
    }

    /**
     * @param status    what the redirect endpoint should answer with
     * @param targetUrl the destination, present only when {@code status} is {@code FOUND}
     */
    public record Resolution(Status status, String targetUrl) {
    }
}
