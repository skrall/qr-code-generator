package org.skrall.qrcode.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/**
 * Pins each hint to the native-image failure it exists for. A rename upstream should fail here,
 * on the JVM, rather than in a container after a ten-minute native build.
 */
class AppRuntimeHintsTest {

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void flywayCanConstructTheSlf4jLogCreator() {
        RuntimeHints hints = new RuntimeHints();
        new FlywayRuntimeHints().registerHints(hints, classLoader);

        assertThat(FlywayRuntimeHints.logCreators(classLoader))
                .contains("org.flywaydb.core.internal.logging.slf4j.Slf4jLogCreator");
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.flywaydb.core.internal.logging.slf4j.Slf4jLogCreator.class)
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
    }

    @Test
    void flywayCanReadItsVersion() {
        RuntimeHints hints = new RuntimeHints();
        new FlywayRuntimeHints().registerHints(hints, classLoader);

        assertThat(classLoader.getResource(FlywayRuntimeHints.VERSION_RESOURCE)).isNotNull();
        assertThat(RuntimeHintsPredicates.resource().forResource(FlywayRuntimeHints.VERSION_RESOURCE))
                .accepts(hints);
    }

    @Test
    void hikariCanCreateItsStatementArray() {
        RuntimeHints hints = new RuntimeHints();
        new HikariRuntimeHints().registerHints(hints, classLoader);

        assertThat(RuntimeHintsPredicates.reflection().onType(java.sql.Statement[].class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.springframework.aot.hint.TypeReference.of(HikariRuntimeHints.BAG_ENTRY_ARRAY)))
                .accepts(hints);
    }

    @Test
    void logbackCanConstructItsFallbackConfigurators() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new LogbackRuntimeHints().registerHints(hints, classLoader);

        for (String resource : LogbackRuntimeHints.VERSION_RESOURCES) {
            assertThat(classLoader.getResource(resource)).as(resource).isNotNull();
            assertThat(RuntimeHintsPredicates.resource().forResource(resource)).as(resource).accepts(hints);
        }
        for (String configurator : LogbackRuntimeHints.configurators()) {
            assertThat(RuntimeHintsPredicates.reflection()
                    .onType(Class.forName(configurator))
                    .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                    .as(configurator)
                    .accepts(hints);
        }
    }

    @Test
    void theBundledFontsAreResources() {
        RuntimeHints hints = new RuntimeHints();
        new FontRuntimeHints().registerHints(hints, classLoader);

        assertThat(RuntimeHintsPredicates.resource().forResource("fonts/Inter-Regular.ttf")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("fonts/Inter-Bold.ttf")).accepts(hints);
    }

    private static RuntimeHints all(ClassLoader classLoader) {
        RuntimeHints hints = new RuntimeHints();
        new HibernateRuntimeHints().registerHints(hints, classLoader);
        new ValidationRuntimeHints().registerHints(hints, classLoader);
        new FlywayRuntimeHints().registerHints(hints, classLoader);
        new JdbcRuntimeHints().registerHints(hints, classLoader);
        new ApiModelRuntimeHints().registerHints(hints, classLoader);
        return hints;
    }

    @Test
    void hibernateCanBuildItsAnnotationModels() throws Exception {
        RuntimeHints hints = all(classLoader);
        Class<?> model = Class.forName("org.hibernate.boot.models.annotations.internal.CacheAnnotation");

        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(
                model.getDeclaredConstructor(org.hibernate.models.spi.ModelsContext.class)))
                .accepts(hints);
    }

    @Test
    void hibernateCanReadAnnotationAttributesAndCreateTheirArrays() throws Exception {
        RuntimeHints hints = all(classLoader);

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                jakarta.persistence.Table.class.getMethod("uniqueConstraints")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(jakarta.persistence.UniqueConstraint[].class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(org.hibernate.annotations.Cache.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS))
                .accepts(hints);
    }

    @Test
    void hibernateCanActivateBeanValidationAndReadItsAnnotationRegistries() throws Exception {
        RuntimeHints hints = all(classLoader);
        Class<?> activator = Class.forName("org.hibernate.boot.beanvalidation.TypeSafeActivator");
        java.lang.reflect.Method activate = java.util.Arrays.stream(activator.getMethods())
                .filter(method -> method.getName().equals("activate")).findFirst().orElseThrow();

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(activate)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(
                org.hibernate.boot.models.JpaAnnotations.class.getField("TABLE"))).accepts(hints);
        for (String type : HibernateRuntimeHints.LOOKUPS) {
            assertThat(Class.forName(type, false, classLoader)).as("%s still exists", type).isNotNull();
        }
    }

    @Test
    void hibernateCanFindTheDialectOverrideForms() {
        RuntimeHints hints = all(classLoader);

        assertThat(RuntimeHintsPredicates.reflection().onType(org.hibernate.annotations.DialectOverride.class)
                .withMemberCategory(MemberCategory.DECLARED_CLASSES)).accepts(hints);
        // A nest member Hibernate reads the meta-annotation of.
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.hibernate.annotations.DialectOverride.SQLInsert.class)).accepts(hints);
    }

    @Test
    void hibernateCanCreateEventListenerArraysAndPersisters() throws Exception {
        RuntimeHints hints = all(classLoader);

        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.hibernate.event.spi.PersistEventListener[].class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.hibernate.persister.entity.SingleTableEntityPersister.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(org.hibernate.event.spi.EventType.class)
                .withMemberCategory(MemberCategory.ACCESS_PUBLIC_FIELDS)).accepts(hints);
    }

    @Test
    void validatorCanBuildItsValidatorsAndReadConstraints() throws Exception {
        RuntimeHints hints = all(classLoader);

        for (String validator : java.util.List.of(
                "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator",
                "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForInteger",
                "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForCharSequence")) {
            assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(
                    Class.forName(validator).getDeclaredConstructor()))
                    .as(validator).accepts(hints);
        }
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                jakarta.validation.constraints.Max.class.getMethod("message")))
                .accepts(hints);
        // Found by name and read through its INSTANCE field. Checked by binary name, which is what
        // the image builder reads: a "$" inside a top-level class name makes Spring's Class-based
        // predicates parse it as a nested type and report a miss for a hint that is present.
        assertThat(hints.reflection().typeHints()
                .filter(hint -> hint.getType().getName()
                        .equals("org.hibernate.validator.internal.util.logging.Messages_$bundle")))
                .singleElement()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.ACCESS_DECLARED_FIELDS));
    }

    @Test
    void flywayCanConfigureItsPostgresExtensionAndDriversCanBeLoaded() throws Exception {
        RuntimeHints hints = all(classLoader);

        assertThat(RuntimeHintsPredicates.reflection().onType(
                Class.forName("org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension"))
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS))
                .accepts(hints);
        for (String driver : JdbcRuntimeHints.DRIVERS) {
            assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(
                    Class.forName(driver).getDeclaredConstructor()))
                    .as(driver).accepts(hints);
        }
    }

    @Test
    void fieldProblemsSerialize() throws Exception {
        RuntimeHints hints = all(classLoader);

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                org.skrall.qrcode.web.ValidationProblemDetailAdvice.FieldProblem.class.getMethod("field")))
                .accepts(hints);
    }
}
