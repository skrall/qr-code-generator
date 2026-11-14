package org.skrall.qrcode.link;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a scan is recorded as, derived from the request's {@code User-Agent} and {@code Referer}.
 *
 * <p>Only the derived values are kept: the full user agent can fingerprint a device and the full
 * referrer can carry tokens in its query, so neither is ever stored. The rules are substring
 * checks rather than a parsing library, because six device classes and a bot flag are all the
 * stats show, and a dependency would have to be kept current for a native image as well.
 *
 * @param device       the platform, {@code OTHER} when the agent is missing or unrecognised
 * @param bot          a crawler, link preview or scripted client rather than a person
 * @param referrerHost the referrer's host, lower case, or null when there was none
 */
public record ScanSource(DeviceClass device, boolean bot, String referrerHost) {

    /** A request without either header: an unrecognised device, counted as a person. */
    public static final ScanSource UNKNOWN = new ScanSource(DeviceClass.OTHER, false, null);

    static final int MAX_HOST_LENGTH = 255;

    /**
     * Link previews and crawlers that announce themselves without a {@code name/version} token
     * ending in bot, plus scripted HTTP clients. Lower case; matched as substrings.
     */
    private static final List<String> BOT_MARKERS = List.of(
            "facebookexternalhit", "facebookcatalog", "whatsapp", "slack-imgproxy", "skypeuripreview",
            "embedly", "iframely", "vkshare", "pinterest", "quora link preview", "bitlybot",
            "headlesschrome", "lighthouse", "crawler", "spider", "curl/", "wget/", "python-requests",
            "python-urllib", "go-http-client", "httpclient", "okhttp", "node-fetch", "axios/");

    /**
     * A product token whose name ends in "bot", e.g. {@code Googlebot/2.1}, {@code Slackbot-LinkExpanding
     * 1.0}, {@code Twitterbot/1.0}, or a crawler's {@code +http} contact URL. A bare "bot" substring
     * would also match phone models such as the CUBOT X30.
     */
    private static final Pattern BOT_TOKEN =
            Pattern.compile("[a-z]bot(?:[/ ;)_-]|$)|\\+https?://", Pattern.CASE_INSENSITIVE);

    /** Phone makers whose model names end in "bot" and would otherwise look like crawlers. */
    private static final Pattern BOT_LOOKALIKE = Pattern.compile("cubot", Pattern.CASE_INSENSITIVE);

    public static ScanSource of(String userAgent, String referer) {
        return new ScanSource(device(userAgent), isBot(userAgent), referrerHost(referer));
    }

    /**
     * The platform. Order matters: an iPhone says "like Mac OS X" and Android says "Linux", so
     * the mobile platforms are checked first. iPadOS asks for desktop sites by default and then
     * reports itself as a Mac; nothing in the header tells the two apart, so it counts as macOS.
     * ChromeOS ({@code CrOS}) counts as other.
     */
    static DeviceClass device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceClass.OTHER;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        if (agent.contains("iphone") || agent.contains("ipad") || agent.contains("ipod")) {
            return DeviceClass.IOS;
        }
        if (agent.contains("android")) {
            return DeviceClass.ANDROID;
        }
        if (agent.contains("windows")) {
            return DeviceClass.WINDOWS;
        }
        if (agent.contains("macintosh") || agent.contains("mac os x")) {
            return DeviceClass.MACOS;
        }
        if (agent.contains("linux")) {
            return DeviceClass.LINUX;
        }
        return DeviceClass.OTHER;
    }

    /**
     * Whether the request comes from a crawler, a chat app fetching a link preview or a script.
     * A missing user agent is not treated as a bot: it is rare from either, and counting it as a
     * person keeps the behaviour of a plain request (and of the existing hit count) unchanged.
     */
    static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        for (String marker : BOT_MARKERS) {
            if (agent.contains(marker)) {
                return true;
            }
        }
        // A phone model's name is not a bot token, so it is removed before looking for one.
        return BOT_TOKEN.matcher(BOT_LOOKALIKE.matcher(agent).replaceAll("")).find();
    }

    /** The referrer's host only, lower case; null for none or for anything without a host. */
    static String referrerHost(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referer.strip()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.length() > MAX_HOST_LENGTH ? host.substring(0, MAX_HOST_LENGTH) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
