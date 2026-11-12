package org.skrall.qrcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the QR code generator.
 */
@SpringBootApplication
public class QrCodeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrCodeGeneratorApplication.class, args);
    }
}
