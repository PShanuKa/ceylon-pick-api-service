package lk.ceylonpick.shared;

import java.util.regex.Pattern;

/**
 * Sri Lankan mobile numbers, normalised to one canonical form.
 *
 * <p>The phone is the buyer's identity (Architecture §11.1), and BR-18 blocks
 * self-referral by comparing a creator's phone with the buyer's — both only work
 * if {@code 0771234567}, {@code +94771234567} and {@code 94 77 123 4567} all
 * end up as the same string.
 */
public final class Phones {

    /** Canonical form: {@code +947XXXXXXXX}. */
    private static final Pattern CANONICAL = Pattern.compile("^\\+947\\d{8}$");
    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9+]");

    private Phones() {
    }

    /**
     * Returns the canonical form, or {@code null} if this is not a valid SL
     * mobile number.
     */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = NON_DIGITS.matcher(raw.trim()).replaceAll("");

        String candidate;
        if (digits.startsWith("+94")) {
            candidate = digits;
        } else if (digits.startsWith("94")) {
            candidate = "+" + digits;
        } else if (digits.startsWith("0")) {
            candidate = "+94" + digits.substring(1);
        } else if (digits.startsWith("7") && digits.length() == 9) {
            candidate = "+94" + digits;
        } else {
            return null;
        }
        return CANONICAL.matcher(candidate).matches() ? candidate : null;
    }

    public static boolean isValid(String raw) {
        return normalise(raw) != null;
    }

    /** {@code +94771234567} -> {@code +94 ***** 567}, for "we sent a code to ..." copy. */
    public static String mask(String canonical) {
        if (canonical == null || canonical.length() < 4) {
            return "***";
        }
        return "+94 ***** " + canonical.substring(canonical.length() - 3);
    }
}
