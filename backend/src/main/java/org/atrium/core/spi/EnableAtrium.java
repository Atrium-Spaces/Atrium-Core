package org.atrium.core.spi;

import org.atrium.core.autoconfigure.AtriumAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Explicit opt-in annotation for host applications that prefer annotation-driven wiring
 * over Spring Boot auto-configuration discovery.
 *
 * <p>In Spring Boot apps, adding the Atrium dependency is already enough because
 * {@link AtriumAutoConfiguration} is listed under
 * {@code AutoConfiguration.imports}. This annotation exists mainly for clarity in
 * host code and for non-Boot test slices that import config manually.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AtriumAutoConfiguration.class)
public @interface EnableAtrium {
}
