package lk.ceylonpick.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-way hashing and secure random generation.
 *
 * <p>Used for values that are compared but never read back: OTP codes
 * (Architecture §5 — "code_hash only (SHA-256 + salt)"), opaque session and
 * reset tokens, and IP addresses (NFR-06 — "IPs hashed").
 *
 * <p>Not for passwords. Those go through the delegating {@code PasswordEncoder}
 * (bcrypt), which is deliberately slow; SHA-256 is not.
 */
public final class Hashes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_B64 = Base64.getUrlEncoder().withoutPadding();

    private Hashes() {
    }

    /** SHA-256 of {@code salt || value}, hex encoded. */
    public static String sha256(String salt, String value) {
        return sha256((salt == null ? "" : salt) + value);
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison, so a wrong OTP or token cannot be found by timing. */
    public static boolean matches(String expectedHash, String candidateHash) {
        if (expectedHash == null || candidateHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    /** A URL-safe random token of {@code bytes} entropy, for refresh and reset links. */
    public static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return URL_B64.encodeToString(buf);
    }

    /** A zero-padded numeric code of the given length, e.g. "042913". */
    public static String randomNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** NFR-06: store a hash of the caller's IP, never the address itself. */
    public static String ipHash(String ip) {
        return ip == null || ip.isBlank() ? null : sha256(ip);
    }
}
