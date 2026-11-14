package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScanSourceTest {

    private static final String IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
    private static final String IPAD_SAFARI = "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";
    private static final String IPHONE_CHROME = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.6778.73 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_CHROME = "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.81 Mobile Safari/537.36";
    private static final String ANDROID_SAMSUNG = "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S921B) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36";
    private static final String ANDROID_CUBOT = "Mozilla/5.0 (Linux; Android 10; CUBOT X30) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36";
    private static final String WINDOWS_EDGE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";
    private static final String MAC_SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15";
    private static final String MAC_FIREFOX =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.7; rv:132.0) Gecko/20100101 Firefox/132.0";
    private static final String LINUX_FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0";
    private static final String CHROMEOS = "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Test
    void classifiesPhones() {
        assertThat(ScanSource.device(IPHONE_SAFARI)).isEqualTo(DeviceClass.IOS);
        assertThat(ScanSource.device(IPHONE_CHROME)).isEqualTo(DeviceClass.IOS);
        assertThat(ScanSource.device(IPAD_SAFARI)).isEqualTo(DeviceClass.IOS);
        assertThat(ScanSource.device(ANDROID_CHROME)).isEqualTo(DeviceClass.ANDROID);
        assertThat(ScanSource.device(ANDROID_SAMSUNG)).isEqualTo(DeviceClass.ANDROID);
    }

    @Test
    void classifiesDesktops() {
        assertThat(ScanSource.device(WINDOWS_EDGE)).isEqualTo(DeviceClass.WINDOWS);
        assertThat(ScanSource.device(MAC_SAFARI)).isEqualTo(DeviceClass.MACOS);
        assertThat(ScanSource.device(MAC_FIREFOX)).isEqualTo(DeviceClass.MACOS);
        assertThat(ScanSource.device(LINUX_FIREFOX)).isEqualTo(DeviceClass.LINUX);
    }

    @Test
    void anythingElseIsOther() {
        assertThat(ScanSource.device(CHROMEOS)).isEqualTo(DeviceClass.OTHER);
        assertThat(ScanSource.device("curl/8.10.1")).isEqualTo(DeviceClass.OTHER);
        assertThat(ScanSource.device("")).isEqualTo(DeviceClass.OTHER);
        assertThat(ScanSource.device(null)).isEqualTo(DeviceClass.OTHER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
        "Slackbot 1.0 (+https://api.slack.com/robots)",
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        "Twitterbot/1.0",
        "WhatsApp/2.23.20.0 A",
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0.6778.69 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
        "TelegramBot (like TwitterBot)",
        "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)",
        "LinkedInBot/1.0 (compatible; Mozilla/5.0; Apache-HttpClient +http://www.linkedin.com)",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_5) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/13.1.1 Safari/605.1.15 (Applebot/0.1; +http://www.apple.com/go/applebot)",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) SkypeUriPreview Preview/0.5",
        "Mastodon/4.3.1 (http.rb/5.2.0; +https://mastodon.social/)",
        "curl/8.10.1",
        "Wget/1.24.5",
        "python-requests/2.32.3",
        "Go-http-client/2.0",
    })
    void recognisesCrawlersPreviewsAndScripts(String agent) {
        assertThat(ScanSource.isBot(agent)).as(agent).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        IPHONE_SAFARI, IPHONE_CHROME, ANDROID_CHROME, ANDROID_SAMSUNG, ANDROID_CUBOT,
        WINDOWS_EDGE, MAC_SAFARI, LINUX_FIREFOX, CHROMEOS, ""
    })
    void peopleAreNotBots(String agent) {
        assertThat(ScanSource.isBot(agent)).as(agent).isFalse();
    }

    @Test
    void aMissingAgentIsAPerson() {
        assertThat(ScanSource.isBot(null)).isFalse();
        assertThat(ScanSource.of(null, null)).isEqualTo(ScanSource.UNKNOWN);
    }

    @Test
    void keepsOnlyTheReferrersHost() {
        assertThat(ScanSource.referrerHost("https://www.Example.com/path?token=secret#x"))
                .isEqualTo("www.example.com");
        assertThat(ScanSource.referrerHost("android-app://com.slack/")).isEqualTo("com.slack");
        assertThat(ScanSource.referrerHost("http://192.168.1.20:8080/")).isEqualTo("192.168.1.20");
    }

    @Test
    void dropsReferrersWithoutAHost() {
        assertThat(ScanSource.referrerHost(null)).isNull();
        assertThat(ScanSource.referrerHost("  ")).isNull();
        assertThat(ScanSource.referrerHost("not a url")).isNull();
        assertThat(ScanSource.referrerHost("/relative/path")).isNull();
        assertThat(ScanSource.referrerHost("about:blank")).isNull();
    }

    @Test
    void capsAnOversizedHost() {
        String host = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";
        assertThat(ScanSource.referrerHost("https://" + host + "/")).hasSize(ScanSource.MAX_HOST_LENGTH);
    }
}
