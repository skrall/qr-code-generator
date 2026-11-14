package org.skrall.qrcode.nativeimage;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Runtime hints needed to run Hibernate in a GraalVM native image.
 *
 * <p>Hibernate declares its logging interfaces with JBoss Logging, whose annotation
 * processor generates a matching implementation class named {@code <Interface>_$logger} for
 * each one. At runtime {@code Logger.getMessageLogger} finds that implementation by name and
 * instantiates it reflectively, so every one of them needs to be present in the image and
 * reachable through its constructor.
 *
 * <p>A native image without these hints fails at startup, or on first use of a code path, with:
 * <pre>
 * java.lang.IllegalArgumentException: Invalid logger interface
 *     org.hibernate.jpa.internal.JpaLogger (implementation not found)
 * </pre>
 *
 * <p>Registering just the one interface from the error message is not enough. The loggers are
 * initialised lazily, so the next untouched one fails instead, and Hibernate adds new ones
 * between releases. This scans hibernate-core for the generated implementations instead of
 * hardcoding a list, so a Hibernate upgrade does not silently reintroduce the failure.
 *
 * <p>{@link #STRATEGIES} covers the second reflective path: Hibernate's {@code StrategySelector}
 * looks implementations up by name and calls their no-argument constructor. See that field for
 * why those cannot be discovered the same way.
 */
public class HibernateRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Suffix of the implementation the JBoss Logging processor generates for a
     * message logger interface.
     */
    private static final String LOGGER_SUFFIX = "_$logger";

    private static final String LOGGER_PATTERN = "classpath*:org/hibernate/**/*" + LOGGER_SUFFIX + ".class";

    /**
     * Message bundles the generated loggers read their messages from.
     *
     * <p>Resource patterns are plain classpath globs, with no {@code classpath*:} prefix. Unlike
     * {@link #LOGGER_PATTERN}, which Spring's resolver scans here and now, these are handed to the
     * image builder, which matches them against every jar at build time. A prefixed pattern is
     * taken literally there and matches nothing: the image then fails at the first schema lookup
     * even though the hint appears to be registered. {@code HibernateRuntimeHintsTest} checks the
     * patterns against real resource paths for that reason.
     */
    private static final String MESSAGES_PATTERN = "org/hibernate/**/*i18n.properties";

    /**
     * DTDs and XSDs the JAXB binder resolves while reading mappings, which a native image
     * otherwise drops because nothing references them from code:
     * <pre>
     * Caused by: org.hibernate.boot.jaxb.internal.stax.XmlInfrastructureException:
     *     Unable to locate schema [org/hibernate/hibernate-mapping-3.0.dtd] via classpath
     * </pre>
     *
     * <p>Matched by pattern rather than by name because Hibernate carries a fixed set of them
     * across its mapping, configuration and JPA namespaces, and gains one with each revision
     * of the specs it supports.
     */
    private static final List<String> SCHEMA_PATTERNS =
            List.of("org/hibernate/**/*.xsd", "org/hibernate/**/*.dtd");

    /**
     * Implementations Hibernate resolves by name and instantiates through their no-argument
     * constructor, which a native image otherwise drops:
     * <pre>
     * Caused by: java.lang.NoSuchMethodException:
     *     org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard.&lt;init&gt;()
     * </pre>
     *
     * <p>Taken from the reachability metadata Hibernate publishes for itself, which the
     * GraalVM metadata repository applies per version. There is no entry for 7.4.x yet, so
     * nothing is applied automatically and the image fails to start on the very first one.
     * Re-check the published metadata on each Hibernate upgrade; once it covers the version
     * in use this list can go, and HibernateRuntimeHintsTest will flag any name that has been
     * renamed or removed.
     *
     * <p>Not derived from a class-name convention on purpose. Names like
     * {@code ColumnOrderingStrategyStandard} and {@code PhysicalNamingStrategyStandardImpl}
     * do not follow one, so a suffix rule silently misses the classes that break startup.
     */
    private static final List<String> STRATEGIES = List.of(
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl$JaxbPropertiesImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl$JaxbPropertiesImpl$JaxbPropertyImpl",
            "org.hibernate.boot.archive.scan.internal.DisabledScanner",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard",
            "org.hibernate.dialect.type.OracleArrayJdbcTypeConstructor",
            "org.hibernate.dialect.type.OracleNestedTableJdbcTypeConstructor",
            "org.hibernate.dialect.type.OracleStructJdbcType",
            "org.hibernate.dialect.type.PostgreSQLInetJdbcType",
            "org.hibernate.dialect.type.PostgreSQLIntervalSecondJdbcType",
            "org.hibernate.dialect.type.PostgreSQLJsonArrayPGObjectJsonbJdbcTypeConstructor",
            "org.hibernate.dialect.type.PostgreSQLJsonPGObjectJsonbType",
            "org.hibernate.dialect.type.PostgreSQLStructPGObjectJdbcType",
            "org.hibernate.generator.internal.GeneratedAlwaysGeneration",
            "org.hibernate.id.Assigned",
            "org.hibernate.id.ForeignGenerator",
            "org.hibernate.id.GUIDGenerator",
            "org.hibernate.id.IdentityGenerator",
            "org.hibernate.id.IncrementGenerator",
            "org.hibernate.id.SelectGenerator",
            "org.hibernate.id.UUIDGenerator",
            "org.hibernate.id.UUIDHexGenerator",
            "org.hibernate.id.enhanced.LegacyNamingStrategy",
            "org.hibernate.id.enhanced.SequenceStyleGenerator",
            "org.hibernate.id.enhanced.SingleNamingStrategy",
            "org.hibernate.id.enhanced.StandardNamingStrategy",
            "org.hibernate.id.enhanced.TableGenerator",
            "org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorBuilderImpl");

    /**
     * Hibernate's model of each annotation it reads. {@code hibernate-models} builds one per
     * annotation through a constructor taking a {@code ModelsContext}, found reflectively:
     * <pre>
     * Caused by: java.lang.NoSuchMethodException:
     *     org.hibernate.boot.models.annotations.internal.CacheAnnotation.&lt;init&gt;(org.hibernate.models.spi.ModelsContext)
     * </pre>
     * Every class in the package is registered, because which ones are built depends on the
     * annotations the entities carry, and the package grows with each Hibernate release.
     */
    static final String ANNOTATION_MODEL_PATTERN =
            "classpath*:org/hibernate/boot/models/annotations/internal/*.class";

    /**
     * The annotations themselves, whose attribute methods {@code hibernate-models} reads
     * reflectively and whose arrays it creates reflectively. In a native image an annotation type
     * reports no methods unless registered, so every attribute would read as absent.
     */
    static final List<String> ANNOTATION_TYPE_PATTERNS = List.of(
            "classpath*:org/hibernate/annotations/*.class",
            "classpath*:jakarta/persistence/*.class");

    /**
     * Event listener interfaces. Hibernate keeps each event's listeners in an array created
     * reflectively from the listener type, the same failure as Hikari's arrays.
     */
    static final String EVENT_LISTENER_PATTERN = "classpath*:org/hibernate/event/spi/*EventListener.class";

    /**
     * Persisters, built through a constructor Hibernate looks up by its parameter types (the
     * tracing agent recorded {@code SingleTableEntityPersister(PersistentClass, ...)}).
     */
    static final List<String> PERSISTER_PATTERNS = List.of(
            "classpath*:org/hibernate/persister/entity/*EntityPersister.class",
            "classpath*:org/hibernate/persister/collection/*Persister.class");

    /**
     * {@code DialectOverride}, whose nested annotation types Hibernate discovers through
     * {@code getNestMembers()} when the class initialises. A native image reports only registered
     * nested classes, so the lookup comes back empty and the first entity fails to bind:
     * <pre>
     * Caused by: org.hibernate.HibernateException: Specified Annotation type
     *     (org.hibernate.annotations.SQLInsert) does not have an override form
     * </pre>
     * The nested annotations themselves are covered by {@link #ANNOTATION_TYPE_PATTERNS}.
     */
    static final String DIALECT_OVERRIDE = "org.hibernate.annotations.DialectOverride";

    /**
     * Classes Hibernate looks up by name, or whose public members it reads reflectively, as
     * recorded by the native-image tracing agent while the app served every endpoint against H2
     * and Postgres. In a native image a lookup by name fails unless the class is registered:
     * <pre>
     * Caused by: java.lang.ClassNotFoundException: org.hibernate.boot.beanvalidation.TypeSafeActivator
     * </pre>
     * Among them, {@code HibernateAnnotations}, {@code JpaAnnotations} and {@code XmlAnnotations}
     * are registries built by reading every static field; unregistered, they come up empty.
     *
     * <p>Each is registered with its public constructors, methods and fields, which covers every
     * member the agent saw used. Re-record on a Hibernate upgrade: run the jar on a GraalVM JDK
     * with {@code -agentlib:native-image-agent}, exercise the app, and compare.
     */
    static final List<String> LOOKUPS = List.of(
            "org.hibernate.Incubating",
            "org.hibernate.Session",
            "org.hibernate.SessionFactory",
            "org.hibernate.SharedSessionContract",
            "org.hibernate.StatelessSession",
            "org.hibernate.boot.beanvalidation.TypeSafeActivator",
            "org.hibernate.boot.cfgxml.internal.CfgXmlAccessServiceImpl",
            "org.hibernate.boot.internal.Abstract",
            "org.hibernate.boot.internal.AnyKeyType",
            "org.hibernate.boot.internal.CollectionClassification",
            "org.hibernate.boot.internal.DefaultSessionFactoryBuilderService",
            "org.hibernate.boot.internal.Extends",
            "org.hibernate.boot.internal.Target",
            "org.hibernate.boot.models.DialectOverrideAnnotations",
            "org.hibernate.boot.models.HibernateAnnotations",
            "org.hibernate.boot.models.JpaAnnotations",
            "org.hibernate.boot.models.XmlAnnotations",
            "org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl",
            "org.hibernate.bytecode.spi.ReflectionOptimizer$InstantiationOptimizer",
            "org.hibernate.cache.internal.DisabledCaching",
            "org.hibernate.cache.internal.NoCachingRegionFactory",
            "org.hibernate.engine.config.internal.ConfigurationServiceImpl",
            "org.hibernate.engine.extension.internal.ExtensionIntegrationServiceImpl",
            "org.hibernate.engine.jdbc.batch.internal.BatchBuilderImpl",
            "org.hibernate.engine.jdbc.connections.internal.DataSourceConnectionProvider",
            "org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider",
            "org.hibernate.engine.jdbc.dialect.internal.DialectFactoryImpl",
            "org.hibernate.engine.jdbc.dialect.internal.DialectResolverSet",
            "org.hibernate.engine.jdbc.env.internal.JdbcEnvironmentImpl",
            "org.hibernate.engine.jdbc.internal.JdbcServicesImpl",
            "org.hibernate.engine.jdbc.mutation.internal.StandardMutationExecutorService",
            "org.hibernate.engine.jdbc.spi.SqlStatementLogger",
            "org.hibernate.engine.jndi.internal.JndiServiceImpl",
            "org.hibernate.engine.query.internal.NativeQueryInterpreterStandardImpl",
            "org.hibernate.engine.spi.PrimeAmongSecondarySupertypes",
            "org.hibernate.engine.transaction.jta.platform.internal.StandardJtaPlatformResolver",
            "org.hibernate.internal.util.cache.InternalCacheFactoryImpl",
            "org.hibernate.jpa.HibernatePersistenceProvider",
            "org.hibernate.metamodel.model.domain.JpaMetamodel",
            "org.hibernate.persister.internal.PersisterFactoryImpl",
            "org.hibernate.persister.internal.StandardPersisterClassResolver",
            "org.hibernate.property.access.internal.PropertyAccessStrategyResolverStandardImpl",
            "org.hibernate.proxy.HibernateProxy",
            "org.hibernate.proxy.ProxyConfiguration",
            "org.hibernate.proxy.ProxyConfiguration$InterceptorDispatcher",
            "org.hibernate.query.QueryProducer",
            "org.hibernate.query.TypedParameterValue",
            "org.hibernate.resource.beans.internal.ManagedBeanRegistryImpl",
            "org.hibernate.service.internal.SessionFactoryServiceRegistryFactoryImpl",
            "org.hibernate.sql.ast.internal.ParameterMarkerStrategyStandard",
            "org.hibernate.sql.results.jdbc.internal.JdbcValuesMappingProducerProviderStandard",
            "org.hibernate.stat.internal.StatisticsImpl",
            "org.hibernate.temporal.internal.ChangesetCoordinatorImpl",
            "org.hibernate.tool.schema.internal.HibernateSchemaManagementTool");

    /** Arrays Hibernate creates reflectively, from the same agent run. */
    static final List<String> ARRAYS = List.of(
"org.hibernate.generator.EventType[]");

    /** Constant holders Hibernate reads every static field of by reflection. */
    static final List<String> CONSTANT_HOLDERS = List.of(
            "org.hibernate.event.spi.EventType",
            "org.hibernate.type.SqlTypes");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String model : ClassPathScanner.findClassNames(classLoader, ANNOTATION_MODEL_PATTERN)) {
            hints.reflection().registerTypeIfPresent(classLoader, model,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        for (String pattern : ANNOTATION_TYPE_PATTERNS) {
            for (Class<?> annotation : ClassPathScanner.findAnnotationTypes(classLoader, pattern)) {
                hints.reflection().registerType(annotation, MemberCategory.INVOKE_PUBLIC_METHODS);
                hints.reflection().registerType(TypeReference.of(annotation.getName() + "[]"));
            }
        }
        for (String listener : ClassPathScanner.findClassNames(classLoader, EVENT_LISTENER_PATTERN)) {
            hints.reflection().registerType(TypeReference.of(listener + "[]"));
        }
        for (String pattern : PERSISTER_PATTERNS) {
            for (String persister : ClassPathScanner.findClassNames(classLoader, pattern)) {
                hints.reflection().registerTypeIfPresent(classLoader, persister,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            }
        }
        hints.reflection().registerTypeIfPresent(classLoader, DIALECT_OVERRIDE,
                MemberCategory.DECLARED_CLASSES, MemberCategory.PUBLIC_CLASSES);
        for (String type : LOOKUPS) {
            hints.reflection().registerTypeIfPresent(classLoader, type,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS);
        }
        for (String array : ARRAYS) {
            hints.reflection().registerType(TypeReference.of(array));
        }
        for (String holder : CONSTANT_HOLDERS) {
            hints.reflection().registerTypeIfPresent(classLoader, holder, MemberCategory.ACCESS_PUBLIC_FIELDS);
        }
        List<String> loggers = ClassPathScanner.findClassNames(classLoader, LOGGER_PATTERN);
        for (String logger : loggers) {
            hints.reflection().registerTypeIfPresent(classLoader, logger, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        for (String strategy : STRATEGIES) {
            hints.reflection().registerTypeIfPresent(classLoader, strategy, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        hints.resources().registerPattern(MESSAGES_PATTERN);
        for (String schema : SCHEMA_PATTERNS) {
            hints.resources().registerPattern(schema);
        }
    }

    /**
     * The resource patterns that need registering, for verification by the tests.
     */
    static List<String> resourcePatterns() {
        return Stream.concat(Stream.of(MESSAGES_PATTERN), SCHEMA_PATTERNS.stream()).toList();
    }

    /**
     * The strategy implementations that need registering, for verification by the tests.
     */
    static List<String> strategies() {
        return STRATEGIES;
    }
}
