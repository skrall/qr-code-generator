package org.skrall.qrcode.web;

import org.skrall.qrcode.model.QrFormat;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Teaches the request pipeline to accept {@code ?format=png}.
 *
 * <p>Spring converts a query parameter to an enum with {@code Enum.valueOf}, which is
 * case-sensitive, so the natural lowercase spelling of the format would be rejected. Routing
 * {@link QrFormat} through {@link QrFormat#parse(String)} keeps the parameter typed while
 * accepting either case; a rejected value still surfaces as a 400.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, QrFormat.class, QrFormat::parse);
    }
}
