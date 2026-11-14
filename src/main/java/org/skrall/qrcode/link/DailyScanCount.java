package org.skrall.qrcode.link;

import java.time.LocalDate;

/**
 * Scans on one UTC day, split by the bot flag. Built by Hibernate from a JPQL constructor
 * expression, which finds the constructor reflectively; registered in {@code ApiModelRuntimeHints}.
 */
public record DailyScanCount(LocalDate day, boolean bot, long scans) {
}
