package com.smartcampus.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Hashes and verifies passwords with <b>BCrypt</b>.
 *
 * <p><b>Why not store the password itself?</b> If the database leaks, plain
 * text passwords would be readable instantly.  BCrypt is deliberately slow and
 * salted, so even a stolen hash is expensive to crack and two identical
 * passwords produce two different hashes.</p>
 *
 * <p>The application only ever stores/compares the hash - the raw password
 * leaves the server memory as soon as login finishes.</p>
 */
@Service
public class PasswordService {

    /** cost factor 10 -> 2^10 rounds; good balance for a college project. */
    private static final int STRENGTH = 10;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);

    /** Converts a plain-text password into a BCrypt hash for storage. */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Verifies a plain-text password against the stored hash.
     *
     * @return {@code true} only when they match
     */
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, encodedHash);
    }
}
