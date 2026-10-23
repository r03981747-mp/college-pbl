package com.smartcampus.security;

/**
 * Holds the identity of the user that owns the <b>current request</b>.
 *
 * <p>Implemented with a {@link ThreadLocal} because a servlet container runs
 * many requests concurrently on a pool of threads: without a thread-local,
 * two users being served at the same instant would overwrite each other's
 * identity.</p>
 *
 * <p><b>Multithreading point for the viva:</b></p>
 * <ul>
 *   <li>each request thread gets its own private copy of the value -</li>
 *   <li>no {@code synchronized} is needed because the data is not shared -</li>
 *   <li>the value is always cleared in {@code afterCompletion()} otherwise a
 *       pooled thread would leak the previous user into the next request.</li>
 * </ul>
 */
public final class AuthContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
        // utility class - not instantiable
    }

    /** Called once per request by {@link AuthInterceptor}. */
    public static void set(AuthenticatedUser user) {
        CURRENT.set(user);
    }

    /** The signed-in user, or {@code null} for an anonymous request. */
    public static AuthenticatedUser get() {
        return CURRENT.get();
    }

    public static boolean isAuthenticated() {
        return CURRENT.get() != null;
    }

    /** Convenience accessors so callers do not repeat null checks. */
    public static Long userId() {
        AuthenticatedUser user = CURRENT.get();
        return user == null ? null : user.id();
    }

    public static String username() {
        AuthenticatedUser user = CURRENT.get();
        return user == null ? "system" : user.username();
    }

    public static String displayName() {
        AuthenticatedUser user = CURRENT.get();
        return user == null ? "System" : user.fullName();
    }

    /**
     * MUST be called when the request finishes - otherwise a pooled thread
     * would keep serving the previous identity.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
