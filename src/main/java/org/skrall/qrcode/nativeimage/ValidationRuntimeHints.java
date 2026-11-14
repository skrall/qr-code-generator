package org.skrall.qrcode.nativeimage;

import java.util.List;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Runtime hints Hibernate Validator needs to check request bodies in a GraalVM native image.
 *
 * <p>Found with the tracing agent rather than by trial: every one of these is reached on the
 * first validated request, not at startup, so the image would start cleanly and then answer the
 * first render with a 500.
 * <ul>
 *   <li>The built-in validators ({@code NotBlankValidator}, {@code MaxValidatorForInteger},
 *       {@code SizeValidatorForCharSequence} and so on) are instantiated by class name.</li>
 *   <li>The constraint annotations' attributes ({@code message()}, {@code groups()},
 *       {@code max()}) are read reflectively; unregistered, an annotation type reports none.</li>
 *   <li>{@code JPATraversableResolver} is instantiated by name because JPA is present.</li>
 *   <li>The {@code Messages_$bundle} message classes are found by name and read through their
 *       {@code INSTANCE} field. ({@code _$logger} classes are covered by
 *       {@link HibernateRuntimeHints}, whose pattern spans all of {@code org/hibernate}.)</li>
 * </ul>
 */
public class ValidationRuntimeHints implements RuntimeHintsRegistrar {

    static final String VALIDATOR_PATTERN =
            "classpath*:org/hibernate/validator/internal/constraintvalidators/**/*.class";

    static final String BUNDLE_PATTERN = "classpath*:org/hibernate/validator/**/*_$bundle*.class";

    static final String CONSTRAINT_PATTERN = "classpath*:jakarta/validation/constraints/*.class";

    /**
     * Looked up by name, or called reflectively ({@code externalClassLoader}), as recorded by the
     * tracing agent. The localized bundle names are probes that may not exist; registering an
     * absent class is skipped.
     */
    static final List<String> NAMED = List.of(
            "org.hibernate.validator.internal.engine.resolver.JPATraversableResolver",
            "org.hibernate.validator.HibernateValidator",
            "org.hibernate.validator.constraintvalidation.HibernateConstraintValidator",
            "org.hibernate.validator.internal.engine.AbstractConfigurationImpl",
            "org.hibernate.validator.internal.engine.ConfigurationImpl",
            "org.hibernate.validator.internal.util.logging.Messages_$bundle_en",
            "org.hibernate.validator.internal.util.logging.Messages_$bundle_en_US");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String validator : ClassPathScanner.findClassNames(classLoader, VALIDATOR_PATTERN)) {
            hints.reflection().registerTypeIfPresent(classLoader, validator,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        for (String bundle : ClassPathScanner.findClassNames(classLoader, BUNDLE_PATTERN)) {
            hints.reflection().registerTypeIfPresent(classLoader, bundle,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS);
        }
        for (Class<?> constraint : ClassPathScanner.findAnnotationTypes(classLoader, CONSTRAINT_PATTERN)) {
            hints.reflection().registerType(constraint, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(TypeReference.of(constraint.getName() + "[]"));
        }
        for (String type : NAMED) {
            hints.reflection().registerTypeIfPresent(classLoader, type,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
