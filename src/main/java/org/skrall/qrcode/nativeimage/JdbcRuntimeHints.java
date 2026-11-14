package org.skrall.qrcode.nativeimage;

import java.util.List;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * The JDBC drivers, which Hikari loads by the class name Spring Boot infers from the URL and
 * instantiates reflectively. Both are registered so either database profile works from the same
 * image.
 */
public class JdbcRuntimeHints implements RuntimeHintsRegistrar {

    static final List<String> DRIVERS = List.of("org.h2.Driver", "org.postgresql.Driver");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String driver : DRIVERS) {
            hints.reflection().registerTypeIfPresent(classLoader, driver,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
    }
}
