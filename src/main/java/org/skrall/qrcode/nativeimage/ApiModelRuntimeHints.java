package org.skrall.qrcode.nativeimage;

import org.skrall.qrcode.design.DesignListing;
import org.skrall.qrcode.model.ScanTrial;
import org.skrall.qrcode.web.ValidationProblemDetailAdvice;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Response types Spring's AOT processing cannot see from the controller signatures.
 *
 * <p>{@code FieldProblem} is put into a {@code ProblemDetail}'s properties map, so its type only
 * exists at runtime; unregistered, Jackson finds no accessors and a 400 loses its field list.
 * {@code ScanTrial[]} was recorded by the tracing agent as created reflectively while writing the
 * scan check's response.
 *
 * <p>{@code DesignListing} is built by Hibernate from a JPQL constructor expression, which finds
 * the record's constructor reflectively.
 */
public class ApiModelRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(ValidationProblemDetailAdvice.FieldProblem.class,
                MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        hints.reflection().registerType(ScanTrial[].class);
        hints.reflection().registerType(DesignListing.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
}
