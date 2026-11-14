package org.skrall.qrcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.skrall.qrcode.nativeimage.ApiModelRuntimeHints;
import org.skrall.qrcode.nativeimage.FlywayRuntimeHints;
import org.skrall.qrcode.nativeimage.FontRuntimeHints;
import org.skrall.qrcode.nativeimage.HibernateRuntimeHints;
import org.skrall.qrcode.nativeimage.HikariRuntimeHints;
import org.skrall.qrcode.nativeimage.JdbcRuntimeHints;
import org.skrall.qrcode.nativeimage.LogbackRuntimeHints;
import org.skrall.qrcode.nativeimage.ValidationRuntimeHints;

/**
 * Entry point for the QR code generator.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
// What a GraalVM native image needs that nothing references directly. See the nativeimage package.
@ImportRuntimeHints({
        HibernateRuntimeHints.class,
        FlywayRuntimeHints.class,
        HikariRuntimeHints.class,
        JdbcRuntimeHints.class,
        ValidationRuntimeHints.class,
        ApiModelRuntimeHints.class,
        LogbackRuntimeHints.class,
        FontRuntimeHints.class})
public class QrCodeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrCodeGeneratorApplication.class, args);
    }
}
