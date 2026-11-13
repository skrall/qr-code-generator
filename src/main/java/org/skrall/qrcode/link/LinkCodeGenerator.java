package org.skrall.qrcode.link;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Makes the code in a short URL.
 *
 * <p>Random rather than sequential: a counter would let anyone walk the range and read every
 * destination that has ever been stored. Base62 keeps the URL short while staying
 * case-sensitive, which matters because the code is the only secret in the link.
 *
 * <p>Nine characters give 62^9 ≈ 1.3×10^16 possibilities. A collision is checked by the service
 * and backed up by the unique constraint, so this class only has to be random, not unique.
 */
@Component
public class LinkCodeGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    static final int LENGTH = 9;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
