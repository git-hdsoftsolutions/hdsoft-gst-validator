package com.hdsoft.gst;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the paths of {@link GSTINValidator} that finish before a browser is started.
 * No network access or browser is needed.
 */
public class GSTINValidatorTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    public void blankInputReturnsErrorWithoutGstin() {
        JsonObject json = parse(new GSTINValidator().validateGSTIN("  "));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("GSTIN cannot be empty.", json.get("error").getAsString());
        assertFalse(json.has("gstin"));
    }

    @Test
    public void nullInputReturnsError() {
        JsonObject json = parse(new GSTINValidator().validateGSTIN(null));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("GSTIN cannot be empty.", json.get("error").getAsString());
    }

    @Test
    public void badFormatReturnsError() {
        JsonObject json = parse(new GSTINValidator().validateGSTIN("not-a-gstin"));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("NOT-A-GSTIN", json.get("gstin").getAsString());
        assertEquals("Invalid GSTIN format.", json.get("error").getAsString());
    }

    @Test
    public void unknownStateCodeReturnsError() {
        JsonObject json = parse(new GSTINValidator().validateGSTIN("00AAAAA0000A1ZI"));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Invalid GSTIN state code.", json.get("error").getAsString());
    }

    @Test
    public void wrongCheckDigitReturnsErrorAndNeverAsksForCaptcha() {
        final AtomicInteger calls = new AtomicInteger();
        GSTINValidator validator = new GSTINValidator();
        validator.work((image, answer) -> calls.incrementAndGet());

        JsonObject json = parse(validator.validateGSTIN("18AYAPA2598P1ZA"));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("Invalid GSTIN check digit.", json.get("error").getAsString());
        assertEquals(0, calls.get());
    }

    @Test
    public void validGstinWithoutCaptchaHandlerFailsFast() {
        JsonObject json = parse(new GSTINValidator().validateGSTIN(" 18ayapa2598p1zz "));
        assertFalse(json.get("success").getAsBoolean());
        assertEquals("18AYAPA2598P1ZZ", json.get("gstin").getAsString());
        assertTrue(json.get("error").getAsString().contains("CAPTCHA handler is not set"));
    }
}
