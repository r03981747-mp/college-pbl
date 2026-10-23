package com.smartcampus.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.smartcampus.model.enums.Role;

/**
 * Declares which roles are allowed to invoke a controller method.
 *
 * <pre>
 * &#64;RequireRoles({Role.ADMIN, Role.FACULTY})
 * &#64;PostMapping
 * public ResponseEntity&lt;?&gt; create(...) { ... }
 * </pre>
 *
 * <p>The check is performed by {@link AuthInterceptor} <b>on the server</b>
 * before the controller ever runs.  Hiding a button in the browser is only a
 * convenience - this annotation is the real security.</p>
 *
 * <p><b>OOP concept:</b> <i>annotations</i> (metadata read by reflection).</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    /** Roles that may execute the annotated operation. */
    Role[] value();
}
