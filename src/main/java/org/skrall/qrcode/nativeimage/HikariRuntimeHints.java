package org.skrall.qrcode.nativeimage;

import java.sql.Statement;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints HikariCP 7 needs in a GraalVM native image.
 *
 * <p>Every pooled connection tracks its open statements in a {@code FastList} backed by an array
 * Hikari creates reflectively. A native image only allows that for registered array types, so
 * the very first connection fails and the pool reports it as a bare {@code NullPointerException}
 * in {@code HikariPool.throwPoolInitializationException}. With Hikari's debug logging on, the real
 * error shows:
 * <pre>
 * org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively instantiate
 *     the array class 'java.sql.Statement[]'.
 *     at com.zaxxer.hikari.pool.PoolEntry.&lt;init&gt;(PoolEntry.java:71)
 * </pre>
 * The GraalVM reachability metadata repository has entries for older Hikari releases only.
 *
 * <p>The arrays come from {@code FastList}, Hikari's only reflective array creation, which is
 * built with an element class by exactly two callers: {@code PoolEntry} (statements) and
 * {@code ConcurrentBag} (its per-thread list of entries). The second is only reached on the
 * thread-local path a native image takes, so a tracing-agent run on the JVM does not record it.
 */
public class HikariRuntimeHints implements RuntimeHintsRegistrar {

    static final String BAG_ENTRY_ARRAY = "com.zaxxer.hikari.util.ConcurrentBag$IConcurrentBagEntry[]";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(Statement[].class);
        hints.reflection().registerType(TypeReference.of(BAG_ENTRY_ARRAY));
    }
}
