package com.hdsoft.gst;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a GSTIN without any network access.
 * <p>
 * Three checks are applied, in this order:
 * <ol>
 *   <li><b>Format</b> &ndash; 15 characters: 2 digit state code, 10 character PAN
 *       ({@code AAAAA9999A}), 1 entity number ({@code 1-9, A-Z}), the letter {@code Z}, and a check character.</li>
 *   <li><b>State code</b> &ndash; one of the GST state / jurisdiction codes.</li>
 *   <li><b>Check digit</b> &ndash; the 15th character must equal the modulo-36 checksum of the first 14.</li>
 * </ol>
 * Passing these checks means the number is <em>well formed</em>; it does not prove that the GSTIN is registered
 * or active. Use {@link GSTINValidator} to confirm that against the GST portal.
 * <p>
 * Only the standard 15 character GSTIN is supported. Identifiers issued in other layouts (for example UIN or
 * OIDAR numbers) are reported as {@link GSTINCheckResult.Error#INVALID_FORMAT}.
 * <p>
 * This class is stateless and thread-safe.
 */
public final class GSTINOfflineValidator {

    private static final String CHECK_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final Pattern FORMAT = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private static final Map<String, String> STATES;

    static {
        Map<String, String> s = new HashMap<String, String>();
        s.put("01", "Jammu and Kashmir");
        s.put("02", "Himachal Pradesh");
        s.put("03", "Punjab");
        s.put("04", "Chandigarh");
        s.put("05", "Uttarakhand");
        s.put("06", "Haryana");
        s.put("07", "Delhi");
        s.put("08", "Rajasthan");
        s.put("09", "Uttar Pradesh");
        s.put("10", "Bihar");
        s.put("11", "Sikkim");
        s.put("12", "Arunachal Pradesh");
        s.put("13", "Nagaland");
        s.put("14", "Manipur");
        s.put("15", "Mizoram");
        s.put("16", "Tripura");
        s.put("17", "Meghalaya");
        s.put("18", "Assam");
        s.put("19", "West Bengal");
        s.put("20", "Jharkhand");
        s.put("21", "Odisha");
        s.put("22", "Chhattisgarh");
        s.put("23", "Madhya Pradesh");
        s.put("24", "Gujarat");
        s.put("25", "Daman and Diu (legacy)");
        s.put("26", "Dadra and Nagar Haveli and Daman and Diu");
        s.put("27", "Maharashtra");
        s.put("28", "Andhra Pradesh (before 2014 bifurcation)");
        s.put("29", "Karnataka");
        s.put("30", "Goa");
        s.put("31", "Lakshadweep");
        s.put("32", "Kerala");
        s.put("33", "Tamil Nadu");
        s.put("34", "Puducherry");
        s.put("35", "Andaman and Nicobar Islands");
        s.put("36", "Telangana");
        s.put("37", "Andhra Pradesh");
        s.put("38", "Ladakh");
        s.put("97", "Other Territory");
        s.put("99", "Centre Jurisdiction");
        STATES = Collections.unmodifiableMap(s);
    }

    private GSTINOfflineValidator() {
    }

    /**
     * Runs the format, state code and check digit checks.
     * <p>
     * Leading / trailing whitespace is ignored and the input is upper-cased, so {@code " 27aaaaa0000a1z5 "}
     * is treated like {@code "27AAAAA0000A1Z5"}.
     *
     * @param gstin the GSTIN to check, may be {@code null}
     * @return a non-null result describing the outcome
     */
    public static GSTINCheckResult check(String gstin) {
        if (gstin == null || gstin.trim().isEmpty()) {
            return new GSTINCheckResult(null, GSTINCheckResult.Error.EMPTY, null, null, null, '\0', '\0');
        }

        String value = normalize(gstin);

        if (!FORMAT.matcher(value).matches()) {
            return new GSTINCheckResult(value, GSTINCheckResult.Error.INVALID_FORMAT, null, null, null, '\0', '\0');
        }

        String stateCode = value.substring(0, 2);
        String stateName = STATES.get(stateCode);
        String pan = value.substring(2, 12);
        char entityNumber = value.charAt(12);
        char checkDigit = value.charAt(14);

        if (stateName == null) {
            return new GSTINCheckResult(value, GSTINCheckResult.Error.INVALID_STATE_CODE,
                    stateCode, null, pan, entityNumber, checkDigit);
        }

        if (computeCheckDigit(value.substring(0, 14)) != checkDigit) {
            return new GSTINCheckResult(value, GSTINCheckResult.Error.INVALID_CHECK_DIGIT,
                    stateCode, stateName, pan, entityNumber, checkDigit);
        }

        return new GSTINCheckResult(value, null, stateCode, stateName, pan, entityNumber, checkDigit);
    }

    /**
     * Convenience for {@code check(gstin).isValid()}.
     *
     * @param gstin the GSTIN to check, may be {@code null}
     * @return {@code true} if the GSTIN is well formed, has a known state code and a correct check digit
     */
    public static boolean isValid(String gstin) {
        return check(gstin).isValid();
    }

    /**
     * Computes the GSTIN check character for the first 14 characters.
     * <p>
     * Each character is mapped to its index in {@code 0-9A-Z}, multiplied by 1 (odd positions) or 2 (even
     * positions), reduced to {@code quotient + remainder} in base 36, and summed. The check character is the one
     * that brings the sum to a multiple of 36.
     *
     * @param first14 the first 14 characters of a GSTIN (upper case, characters {@code 0-9A-Z})
     * @return the check character
     * @throws IllegalArgumentException if the input is not 14 characters of {@code 0-9A-Z}
     */
    public static char computeCheckDigit(String first14) {
        if (first14 == null || first14.length() != 14) {
            throw new IllegalArgumentException("Exactly 14 characters are required.");
        }

        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int index = CHECK_ALPHABET.indexOf(first14.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Unsupported character '" + first14.charAt(i)
                        + "' at position " + (i + 1) + ".");
            }
            int product = index * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHECK_ALPHABET.charAt((36 - sum % 36) % 36);
    }

    /**
     * Returns the state / jurisdiction name for a two digit GST state code.
     *
     * @param stateCode a code such as {@code "18"}
     * @return the name, or {@code null} if the code is not a known GST state code
     */
    public static String getStateName(String stateCode) {
        return stateCode == null ? null : STATES.get(stateCode);
    }

    static String normalize(String gstin) {
        // Locale.ROOT: the default locale must not influence the result (e.g. Turkish dotted/dotless i).
        return gstin.trim().toUpperCase(Locale.ROOT);
    }
}
