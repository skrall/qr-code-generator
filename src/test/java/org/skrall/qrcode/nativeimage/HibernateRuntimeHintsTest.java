package org.skrall.qrcode.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Tests for the native image runtime hints.
 */
class HibernateRuntimeHintsTest {

    /**
     * The implementation JBoss Logging fails to find in the native image build.
     */
    private static final String FAILING_LOGGER = "org.hibernate.jpa.internal.JpaLogger_$logger";

    /**
     * The implementation Hibernate's strategy lookup fails to find in the image built by
     * the buildpacks builder, while the local {@code native:compile} binary starts.
     */
    private static final String FAILING_STRATEGY = "org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard";

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new HibernateRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    @Test
    void registersTheLoggerImplementationFromTheNativeImageFailure() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("the implementation JBoss Logging looks up reflectively for JpaLogger")
                .contains(FAILING_LOGGER);
    }

    @Test
    void registersEveryGeneratedHibernateLogger() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("all generated hibernate loggers, not just the one in the error message")
                .filteredOn(name -> name.endsWith("_$logger"))
                .hasSizeGreaterThan(1);
    }

    @Test
    void allowsConstructingLoggers() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().toList())
                .filteredOn(hint -> hint.getType().getName().equals(FAILING_LOGGER))
                .allSatisfy(hint -> assertThat(hint.getMemberCategories())
                        .as("member categories for %s", FAILING_LOGGER)
                        .contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
    }

    @Test
    void registersLoggerMessageBundles() {
        RuntimeHints hints = register();

        assertThat(hints.resources().resourcePatternHints()
                .flatMap(patternHints -> patternHints.getIncludes().stream())
                .map(pattern -> pattern.getPattern())
                .toList())
                .as("message bundles the generated loggers read their messages from")
                .anyMatch(pattern -> pattern.contains("i18n.properties"));
    }

    @Test
    void registersTheStrategyImplementationFromTheNativeImageFailure() {
        assertThat(constructorHints())
                .as("the implementation Hibernate resolves by name when building the EntityManagerFactory")
                .contains(FAILING_STRATEGY);
    }

    @Test
    void registersEveryNamedStrategy() {
        assertThat(constructorHints())
                .as("all strategies Hibernate instantiates by name, not just the first one to fail")
                .containsAll(HibernateRuntimeHints.strategies());
    }

    @Test
    void everyNamedStrategyStillExists() {
        assertThat(HibernateRuntimeHints.strategies())
                .as("a strategy that Hibernate has renamed or removed is registered by a name that now "
                        + "resolves to nothing, so the image would break again with no build-time warning")
                .allSatisfy(name -> assertThat(Class.forName(name, false, getClass().getClassLoader()))
                        .as("strategy class named in HibernateRuntimeHints")
                        .isNotNull());
    }

    @Test
    void registersHibernateSchemaResources() {
        // "/" is the root Spring includes implicitly, not a pattern anyone chose.
        assertThat(registeredResourcePatterns().stream().filter(pattern -> !"/".equals(pattern)).toList())
                .as("the DTDs and XSDs the JAXB binder resolves by name while reading mappings")
                .anyMatch(pattern -> pattern.endsWith("*.xsd"))
                .anyMatch(pattern -> pattern.endsWith("*.dtd"));
    }

    @Test
    void schemaPatternsStillMatchTheSchemaHibernateShips() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(getClass().getClassLoader());

        assertThat(HibernateRuntimeHints.resourcePatterns())
                .filteredOn(pattern -> pattern.endsWith(".xsd") || pattern.endsWith(".dtd"))
                .as("a pattern that no longer matches anything would silently stop registering the schemas")
                .allSatisfy(pattern -> assertThat(resolve(resolver, "classpath*:" + pattern))
                        .as("resources matching %s", pattern)
                        .isNotEmpty());
    }

    /**
     * Asks using the patterns the hints actually register, rather than a pattern written out here.
     * A literal copy of the pattern passes whether or not the registered one works.
     * {@link #registersTheSchemaAndMessagesAsMatchableResources} checks the same file the way the
     * image builder matches it.
     */
    @Test
    void theSchemaFromTheNativeImageFailureIsMatched() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(getClass().getClassLoader());

        assertThat(registeredResourcePatterns().stream()
                .filter(pattern -> pattern.endsWith(".dtd"))
                .flatMap(pattern -> resolve(resolver, "classpath*:" + pattern).stream()))
                .as("the DTD named in the native image failure")
                .anyMatch(path -> path.endsWith("org/hibernate/hibernate-mapping-3.0.dtd"));
    }

    /**
     * Resource patterns are image-builder globs, matched against every jar at build time, not
     * Spring resource locations. A {@code classpath*:} prefix is taken literally by the builder,
     * so the pattern matches nothing and the image fails at the first schema lookup:
     * {@code Unable to locate schema [org/hibernate/hibernate-mapping-3.0.dtd] via classpath}.
     * The tests above add the prefix themselves when resolving, so they check the same set of
     * jars the builder sees.
     */
    @Test
    void resourcePatternsArePlainClasspathGlobs() {
        // "/" is the root Spring includes implicitly, not a pattern anyone chose.
        assertThat(registeredResourcePatterns().stream().filter(pattern -> !"/".equals(pattern)).toList())
                .isNotEmpty()
                .allSatisfy(pattern -> assertThat(pattern).doesNotContain("classpath"));
    }

    private List<String> resolve(PathMatchingResourcePatternResolver resolver, String pattern) {
        List<String> paths = new ArrayList<>();
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                paths.add(resource.getURL().getPath());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return paths;
    }

    private List<String> registeredResourcePatterns() {
        return register().resources().resourcePatternHints()
                .flatMap(patternHints -> patternHints.getIncludes().stream())
                .map(pattern -> pattern.getPattern())
                .toList();
    }

    /**
     * Names of the classes the hints make constructible.
     */
    private List<String> constructorHints() {
        return register().reflection().typeHints()
                .filter(hint -> hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                .map(hint -> hint.getType().getName())
                .toList();
    }

    /**
     * The schema the native image failed to find, matched the way the image builder matches a
     * resource: by its plain classpath path. A pattern carrying the scanner's {@code classpath*:}
     * prefix matches nothing here.
     */
    @Test
    void registersTheSchemaAndMessagesAsMatchableResources() {
        RuntimeHints hints = register();

        org.assertj.core.api.Assertions.assertThat(
                org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource()
                        .forResource("org/hibernate/hibernate-mapping-3.0.dtd"))
                .accepts(hints);
        org.assertj.core.api.Assertions.assertThat(
                org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource()
                        .forResource("org/hibernate/boot/BootLogging.i18n.properties"))
                .accepts(hints);
    }
}
