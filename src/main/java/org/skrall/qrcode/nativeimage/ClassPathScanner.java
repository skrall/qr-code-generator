package org.skrall.qrcode.nativeimage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Finds classes on the classpath by resource pattern, for the runtime hint registrars.
 */
final class ClassPathScanner {

    private ClassPathScanner() {
    }

    /**
     * Resolves a Spring classpath resource pattern, for example one ending in
     * {@code hibernate-core.jar}, to the binary names of the classes it matches, sorted
     * and without duplicates.
     */
    static List<String> findClassNames(ClassLoader classLoader, String pattern) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver(classLoader).getResources(pattern);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not scan the classpath for " + pattern, ex);
        }
        return Arrays.stream(resources)
                .map(ClassPathScanner::toClassName)
                .filter(Objects::nonNull)
                // Package and module descriptors match a "*.class" pattern but are not types, and
                // a hint naming one fails AOT processing with "Invalid class name".
                .filter(name -> !name.endsWith(".package-info") && !name.endsWith(".module-info"))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The annotation types among the classes a pattern matches, loaded without initialising them.
     * Packages such as {@code jakarta.persistence} mix annotations with ordinary types; only the
     * annotations need their attribute methods registered.
     */
    static List<Class<?>> findAnnotationTypes(ClassLoader classLoader, String pattern) {
        return findClassNames(classLoader, pattern).stream()
                .<Class<?>>map(name -> {
                    try {
                        return Class.forName(name, false, classLoader);
                    } catch (ClassNotFoundException | LinkageError ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(Class::isAnnotation)
                .toList();
    }

    /**
     * Turns a resource location such as {@code jar:file:/path/hibernate-core.jar!/org/hibernate/jpa/internal/JpaLogger_$logger.class}
     * into a binary class name.
     */
    private static String toClassName(Resource resource) {
        try {
            String path = resource.getURL().getPath();
            int jarSeparator = path.indexOf("!/");
            if (jarSeparator != -1) {
                path = path.substring(jarSeparator + 1);
            }
            if (path.endsWith(".class")) {
                path = path.substring(0, path.length() - ".class".length());
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path.replace('/', '.');
        } catch (IOException ex) {
            return null;
        }
    }
}
