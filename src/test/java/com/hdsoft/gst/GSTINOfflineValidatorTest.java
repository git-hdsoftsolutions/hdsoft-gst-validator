package com.hdsoft.gst;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GSTINOfflineValidatorTest {

    /** GSTINs taken from the portal; their check digits are independent ground truth. */
    private static final String[] REAL = {
            "18AYAPA2598P1ZZ",
            "18AABFI4155K1Z9",
            "15ADZPH9868F2ZP"
    };

    /** Synthetic, well formed GSTINs with correct check digits. */
    private static final String[] SYNTHETIC = {
            "27AAAAA0000A1Z2",
            "07AAAAA0000A1Z4",
            "29ABCDE1234F1ZW",
            "97AAAAA0000A1ZV",
            "99AAAAA0000A1ZR",
            "24AAAAA0000A9Z0",
            "24AAAAA0000AZZA"
    };

    @Test
    public void acceptsRealPortalGstins() {
        for (String gstin : REAL) {
            GSTINCheckResult r = GSTINOfflineValidator.check(gstin);
            assertTrue(gstin, r.isValid());
            assertNull(r.getError());
            assertNull(r.getMessage());
        }
    }

    @Test
    public void acceptsSyntheticGstins() {
        for (String gstin : SYNTHETIC) {
            assertTrue(gstin, GSTINOfflineValidator.isValid(gstin));
        }
    }

    @Test
    public void exposesParsedComponents() {
        GSTINCheckResult r = GSTINOfflineValidator.check("18AYAPA2598P1ZZ");
        assertEquals("18AYAPA2598P1ZZ", r.getGstin());
        assertEquals("18", r.getStateCode());
        assertEquals("Assam", r.getStateName());
        assertEquals("AYAPA2598P", r.getPan());
        assertEquals('1', r.getEntityNumber());
        assertEquals('Z', r.getCheckDigit());
    }

    @Test
    public void ignoresCaseAndSurroundingWhitespace() {
        GSTINCheckResult r = GSTINOfflineValidator.check("  27aaaaa0000a1z2\t");
        assertTrue(r.isValid());
        assertEquals("27AAAAA0000A1Z2", r.getGstin());
    }

    @Test
    public void normalisationIsIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            // Under tr-TR a plain toUpperCase() turns 'i' into a dotted capital I and breaks matching.
            assertTrue(GSTINOfflineValidator.isValid("29abcde1234f1zw"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void rejectsNullAndBlank() {
        assertEquals(GSTINCheckResult.Error.EMPTY, GSTINOfflineValidator.check(null).getError());
        assertEquals(GSTINCheckResult.Error.EMPTY, GSTINOfflineValidator.check("").getError());
        assertEquals(GSTINCheckResult.Error.EMPTY, GSTINOfflineValidator.check("   ").getError());
        assertNull(GSTINOfflineValidator.check(null).getGstin());
    }

    @Test
    public void rejectsWrongFormat() {
        String[] bad = {
                "18AYAPA2598P1Z",       // 14 characters
                "18AYAPA2598P1ZZZ",     // 16 characters
                "AAAYAPA2598P1ZZ",      // state code not digits
                "181YAPA2598P1ZZ",      // PAN starts with a digit
                "18AYAPA25A8P1ZZ",      // letter inside the PAN digits
                "18AYAPA2598P0ZZ",      // entity number 0 is not allowed
                "18AYAPA2598P1AZ",      // 14th character must be Z
                "18AYAPA2598P1Z-",      // illegal check character
                "18AYAPA 2598P1ZZ",     // inner whitespace
                "18AUNPG67382ZN"        // malformed
        };
        for (String gstin : bad) {
            GSTINCheckResult r = GSTINOfflineValidator.check(gstin);
            assertFalse(gstin, r.isValid());
            assertEquals(gstin, GSTINCheckResult.Error.INVALID_FORMAT, r.getError());
            assertNull(r.getPan());
            assertEquals("Invalid GSTIN format.", r.getMessage());
        }
    }

    @Test
    public void rejectsUnknownStateCodes() {
        // Each of these has a correct check digit, so only the state code is wrong.
        String[] bad = {"00AAAAA0000A1ZI", "39AAAAA0000A1ZX", "98AAAAA0000A1ZT"};
        for (String gstin : bad) {
            GSTINCheckResult r = GSTINOfflineValidator.check(gstin);
            assertEquals(gstin, GSTINCheckResult.Error.INVALID_STATE_CODE, r.getError());
            assertNull(r.getStateName());
            assertEquals(gstin.substring(2, 12), r.getPan());
        }
    }

    @Test
    public void rejectsWrongCheckDigit() {
        for (String gstin : REAL) {
            String wrong = gstin.substring(0, 14) + (gstin.charAt(14) == '0' ? '1' : '0');
            GSTINCheckResult r = GSTINOfflineValidator.check(wrong);
            assertEquals(wrong, GSTINCheckResult.Error.INVALID_CHECK_DIGIT, r.getError());
            assertEquals("Invalid GSTIN check digit.", r.getMessage());
            assertTrue(r.getStateName() != null);
        }
    }

    @Test
    public void detectsSingleCharacterTypos() {
        // A mod-36 checksum must catch every single-character substitution in the first 14 characters.
        String valid = "29ABCDE1234F1ZW";
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 14; i++) {
            for (int c = 0; c < alphabet.length(); c++) {
                char replacement = alphabet.charAt(c);
                if (replacement == valid.charAt(i)) {
                    continue;
                }
                String typo = valid.substring(0, i) + replacement + valid.substring(i + 1);
                assertFalse(typo, GSTINOfflineValidator.isValid(typo));
            }
        }
    }

    @Test
    public void computesCheckDigit() {
        assertEquals('Z', GSTINOfflineValidator.computeCheckDigit("18AYAPA2598P1Z"));
        assertEquals('9', GSTINOfflineValidator.computeCheckDigit("18AABFI4155K1Z"));
        assertEquals('P', GSTINOfflineValidator.computeCheckDigit("15ADZPH9868F2Z"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void computeCheckDigitRejectsWrongLength() {
        GSTINOfflineValidator.computeCheckDigit("18AYAPA2598P1ZZ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void computeCheckDigitRejectsUnsupportedCharacters() {
        GSTINOfflineValidator.computeCheckDigit("18ayapa2598p1z");
    }

    @Test
    public void looksUpStateNames() {
        assertEquals("Delhi", GSTINOfflineValidator.getStateName("07"));
        assertEquals("Ladakh", GSTINOfflineValidator.getStateName("38"));
        assertEquals("Centre Jurisdiction", GSTINOfflineValidator.getStateName("99"));
        assertNull(GSTINOfflineValidator.getStateName("00"));
        assertNull(GSTINOfflineValidator.getStateName(null));
    }
}
