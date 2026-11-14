package org.skrall.qrcode.nativeimage;

import java.util.List;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Logback's fallback configurators, which it instantiates by name before Spring Boot's logging
 * system takes over.
 *
 * <p>Without these a native image prints a block of status lines at the top of every start:
 * <pre>
 * Instantiation failure: java.lang.ClassNotFoundException:
 *     ch.qos.logback.classic.util.DefaultJoranConfigurator
 * </pre>
 * They are harmless, because Spring Boot configures Logback itself moments later, but they read
 * like a failure.
 *
 * <p>Logback also compares the versions of its two jars at start, reading each from a properties
 * resource. Unregistered, both read as {@code ?}, and the resulting warning is what makes Logback
 * print its whole status block:
 * <pre>
 * WARN in ch.qos.logback.classic.LoggerContext[default] - Versions of logback-classic and ? are
 *     different or unknown.
 * </pre>
 */
public class LogbackRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<String> CONFIGURATORS = List.of(
            "ch.qos.logback.classic.util.DefaultJoranConfigurator",
            "ch.qos.logback.classic.BasicConfigurator");

    static final List<String> VERSION_RESOURCES = List.of(
            "ch/qos/logback/classic/logback-classic-version.properties",
            "ch/qos/logback/core/logback-core-version.properties");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String resource : VERSION_RESOURCES) {
            hints.resources().registerPattern(resource);
        }
        for (String configurator : CONFIGURATORS) {
            hints.reflection().registerTypeIfPresent(classLoader, configurator,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
    }

    static List<String> configurators() {
        return CONFIGURATORS;
    }
}
