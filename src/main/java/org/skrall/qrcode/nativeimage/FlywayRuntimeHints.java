package org.skrall.qrcode.nativeimage;

import java.util.List;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints Flyway needs in a GraalVM native image, beyond those Spring Boot registers.
 *
 * <p>Flyway picks its logging backend by probing for one and instantiating the matching
 * {@code LogCreator} by class name. Nothing references those classes directly, so a native image
 * drops them and Flyway cannot even start, which takes the {@code entityManagerFactory} down with
 * it:
 * <pre>
 * Caused by: org.flywaydb.core.api.FlywayException: Unable to instantiate class
 *     org.flywaydb.core.internal.logging.slf4j.Slf4jLogCreator
 * Caused by: java.lang.ClassNotFoundException:
 *     org.flywaydb.core.internal.logging.slf4j.Slf4jLogCreator
 * </pre>
 *
 * <p>Every creator in Flyway's logging package is registered, not just the SLF4J one, because
 * which one is chosen depends on what else is on the classpath.
 *
 * <p>Flyway also reads its own version from a resource when the {@code Flyway} class loads. A
 * native image leaves out resources nothing registers, so the read returns {@code null}:
 * <pre>
 * Caused by: java.lang.NullPointerException
 *     at org.flywaydb.core.internal.util.FileUtils.copy(FileUtils.java:134)
 *     at org.flywaydb.core.internal.license.VersionPrinter.readVersion(VersionPrinter.java:56)
 * </pre>
 *
 * <p>Flyway's plugins arrive through {@code ServiceLoader}, which a native image handles, but their
 * configuration extensions are then populated through their getters and setters by reflection.
 * The tracing agent recorded this for the baseline, publishing and PostgreSQL extensions.
 */
public class FlywayRuntimeHints implements RuntimeHintsRegistrar {

    static final String VERSION_RESOURCE = "org/flywaydb/core/internal/version.txt";

    private static final String LOG_CREATOR_PATTERN =
            "classpath*:org/flywaydb/core/internal/logging/**/*LogCreator.class";

    static final String CONFIGURATION_EXTENSION_PATTERN =
            "classpath*:org/flywaydb/**/*ConfigurationExtension.class";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String extension : ClassPathScanner.findClassNames(classLoader, CONFIGURATION_EXTENSION_PATTERN)) {
            hints.reflection().registerTypeIfPresent(classLoader, extension,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
        for (String creator : logCreators(classLoader)) {
            hints.reflection().registerTypeIfPresent(classLoader, creator,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        hints.resources().registerPattern(VERSION_RESOURCE);
    }

    /** The log creator implementations Flyway can choose between, for the tests. */
    static List<String> logCreators(ClassLoader classLoader) {
        return ClassPathScanner.findClassNames(classLoader, LOG_CREATOR_PATTERN);
    }
}
