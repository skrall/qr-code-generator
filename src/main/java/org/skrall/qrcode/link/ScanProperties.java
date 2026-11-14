package org.skrall.qrcode.link;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long recorded scans are kept.
 *
 * @param scanRetentionDays scans from before this many UTC days ago are deleted by a daily
 *                          cleanup; 0 keeps them forever. The link's hit count is never pruned.
 */
@ConfigurationProperties(prefix = "app.links")
public record ScanProperties(@DefaultValue("365") int scanRetentionDays) {

    public ScanProperties {
        if (scanRetentionDays < 0) {
            throw new IllegalArgumentException(
                    "app.links.scan-retention-days must be 0 (keep forever) or more");
        }
    }
}
