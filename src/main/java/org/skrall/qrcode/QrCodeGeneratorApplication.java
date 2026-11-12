package org.skrall.qrcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the QR code generator.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class QrCodeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrCodeGeneratorApplication.class, args);
    }
}
