package org.skrall.qrcode.link;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}, for the daily scan cleanup in {@link ScanService}. */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
