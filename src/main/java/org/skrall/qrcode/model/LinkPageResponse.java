package org.skrall.qrcode.model;

import java.util.List;

/** One page of stored links. */
public record LinkPageResponse(List<ShortLinkResponse> items, long total, int page, int size) {
}
