package org.skrall.qrcode.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates short links and resolves them for the redirect endpoint.
 */
@Service
public class ShortLinkService {

    /** Enough retries to cover a collision, which should never happen at this code length. */
    private static final int CODE_ATTEMPTS = 5;

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_TARGET_LENGTH = 2048;
    public static final int MAX_GONE_MESSAGE_LENGTH = 280;

    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private final ShortLinkRepository repository;
    private final LinkCodeGenerator codeGenerator;
    private final LinkProperties properties;
    private final GoneProperties goneProperties;
    private final ScanService scans;
    private final TransactionTemplate transactions;

    public ShortLinkService(ShortLinkRepository repository, LinkCodeGenerator codeGenerator,
            LinkProperties properties, GoneProperties goneProperties, ScanService scans,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.goneProperties = goneProperties;
        this.scans = scans;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ShortLink create(String url, Instant expiresAt) {
        return create(url, expiresAt, null, null);
    }

    /**
     * Stores a destination under a new code.
     *
     * @param goneUrl     where a scan goes once the link has expired or been deleted, or null
     * @param goneMessage plain text for the 410 page shown when there is no fallback, or null
     */
    @Transactional
    public ShortLink create(String url, Instant expiresAt, String goneUrl, String goneMessage) {
        String target = targetUrl(url);
        String fallback = goneUrl(goneUrl);
        String message = goneMessage(goneMessage);
        Instant now = Instant.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidLinkException("expiresAt must be in the future");
        }

        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (repository.existsByCode(code)) {
                continue;
            }
            ShortLink link = new ShortLink(code, target, now, expiresAt);
            link.setGoneOptions(fallback, message);
            return repository.save(link);
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
        return update(code, url, expiresAt, null, null);
    }

    /**
     * Like {@link #update(String, String, Instant)}, replacing the gone options as well. A null
     * option clears it: the request is a whole replacement, as with the expiry.
     */
    @Transactional
    public Optional<ShortLink> update(String code, String url, Instant expiresAt, String goneUrl,
            String goneMessage) {
        Optional<ShortLink> found = repository.findByCode(code).filter(link -> !link.isDeleted());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        String target = targetUrl(url);
        String fallback = goneUrl(goneUrl);
        String message = goneMessage(goneMessage);
        Instant now = Instant.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidLinkException("expiresAt must be in the future");
        }

        found.get().update(target, expiresAt);
        found.get().setGoneOptions(fallback, message);
        return found;
    }

    /** {@link #resolve(String, ScanSource)} for a request that says nothing about itself. */
    public Resolution resolve(String code) {
        return resolve(code, ScanSource.UNKNOWN);
    }

    /**
     * Looks a code up for the redirect endpoint, counting the hit and recording the scan when the
     * link is usable.
     *
     * <p>Deleted and expired links are still found so the caller can tell {@code 410} from
     * {@code 404}. A gone link resolves to a fallback or a message (see {@link #gone}), never to
     * its old destination. A gone scan is neither counted as a hit nor recorded, even when it is
     * sent on to a fallback.
     *
     * <p>A bot (a crawler or a chat app's link preview) is redirected like anyone else and its
     * scan is recorded with the bot flag, but it does not move the hit count or the last hit, so
     * those keep meaning "people who opened the link".
     *
     * <p>The scan is written in its own transaction after the hit count has committed, and a
     * failure there is logged and swallowed: analytics must never cost a scanner the redirect.
     * Writing it inside the same transaction would let a failed insert roll the hit count back
     * and fail the request at commit. Doing it asynchronously would save one indexed insert of
     * latency at the price of an executor and scans lost on shutdown.
     */
    public Resolution resolve(String code, ScanSource source) {
        Instant now = Instant.now();
        Lookup lookup = transactions.execute(status -> lookup(code, source, now));

        if (lookup.resolution().status() == Status.FOUND) {
            try {
                scans.record(lookup.linkId(), source, now);
            } catch (RuntimeException e) {
                log.warn("Could not record a scan of link {}; the redirect is unaffected", code, e);
            }
        }
        return lookup.resolution();
    }

    private Lookup lookup(String code, ScanSource source, Instant now) {
        Optional<ShortLink> found = repository.findByCode(code);
        if (found.isEmpty()) {
            return new Lookup(new Resolution(Status.NOT_FOUND, null), null);
        }

        ShortLink link = found.get();
        if (link.isDeleted() || link.isExpired(now)) {
            return new Lookup(gone(link), link.getId());
        }

        if (!source.bot()) {
            link.recordHit(now);
        }
        return new Lookup(new Resolution(Status.FOUND, link.getTargetUrl()), link.getId());
    }

    private record Lookup(Resolution resolution, Long linkId) {
    }

    /**
     * The link's own fallback wins, then its own message; the configured default only stands in
     * for a link that says nothing of its own, so a message someone wrote is never skipped.
     */
    private Resolution gone(ShortLink link) {
        if (link.getGoneUrl() != null) {
            return new Resolution(Status.GONE, link.getGoneUrl(), null);
        }
        if (link.getGoneMessage() != null) {
            return new Resolution(Status.GONE, null, link.getGoneMessage());
        }
        return new Resolution(Status.GONE, goneProperties.goneUrl(), null);
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
        return validUrl("url", raw);
    }

    /** The fallback for a gone link: blank means none, anything else is checked like a destination. */
    static String goneUrl(String raw) {
        return raw == null || raw.isBlank() ? null : validUrl("goneUrl", raw);
    }

    /**
     * The message for a gone link's page: blank means none. Plain text only; line breaks are
     * kept and other control characters rejected. It is escaped when the page is rendered, so
     * markup is allowed here and simply shows as text.
     */
    static String goneMessage(String raw) {
        if (raw == null) {
            return null;
        }
        String message = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (message.isEmpty()) {
            return null;
        }
        if (message.length() > MAX_GONE_MESSAGE_LENGTH) {
            throw new InvalidLinkException(
                    "goneMessage must be at most " + MAX_GONE_MESSAGE_LENGTH + " characters");
        }
        if (message.chars().anyMatch(c -> c != '\n' && c != '\t' && Character.isISOControl(c))) {
            throw new InvalidLinkException("goneMessage must not contain control characters");
        }
        return message;
    }

    /** Checks one URL field; {@code field} names it in the error. */
    static String validUrl(String field, String raw) {
        String target = raw == null ? "" : raw.strip();
        if (target.isEmpty()) {
            throw new InvalidLinkException(field + " must not be blank");
        }
        if (target.length() > MAX_TARGET_LENGTH) {
            throw new InvalidLinkException(field + " must be at most " + MAX_TARGET_LENGTH + " characters");
        }

        // Check the scheme before parsing: `data:` and `javascript:` payloads are often not
        // valid URIs at all, and "not a valid URL" would hide the reason they were rejected.
        Matcher matcher = SCHEME.matcher(target);
        if (!matcher.find()) {
            throw new InvalidLinkException(field + " must be an absolute http or https URL");
        }
        String scheme = matcher.group(1);
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new InvalidLinkException(field + " must be an absolute http or https URL");
        }

        URI uri;
        try {
            uri = new URI(target);
        } catch (URISyntaxException e) {
            throw new InvalidLinkException(field + " is not a valid absolute URL");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidLinkException(field + " must name a host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidLinkException(field + " must not contain credentials");
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
     * @param targetUrl where to redirect: the destination when {@code FOUND}; when {@code GONE},
     *                  the link's fallback or the configured default, or null to show the page
     * @param message   when {@code GONE}, the link's own text for the page, or null
     */
    public record Resolution(Status status, String targetUrl, String message) {

        public Resolution(Status status, String targetUrl) {
            this(status, targetUrl, null);
        }
    }
}
