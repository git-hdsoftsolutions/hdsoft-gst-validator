package com.hdsoft.gst;

/**
 * Immutable outcome of an offline GSTIN check performed by {@link GSTINOfflineValidator}.
 * <p>
 * The parsed components ({@link #getStateCode()}, {@link #getPan()}, ...) are populated as far as the
 * check got: a result that failed on the check digit still carries the state code and PAN, while a
 * result that failed on the format carries none of them.
 */
public final class GSTINCheckResult {

    /**
     * Reason an offline check failed.
     */
    public enum Error {
        /** The input was {@code null} or blank. */
        EMPTY("GSTIN cannot be empty."),
        /** The input is not 15 characters in the standard GSTIN layout. */
        INVALID_FORMAT("Invalid GSTIN format."),
        /** The first two digits are not a known GST state / jurisdiction code. */
        INVALID_STATE_CODE("Invalid GSTIN state code."),
        /** The 15th character does not match the computed check digit. */
        INVALID_CHECK_DIGIT("Invalid GSTIN check digit.");

        private final String message;

        Error(String message) {
            this.message = message;
        }

        /**
         * Returns a human readable failure message.
         *
         * @return a short, human readable description of the failure
         */
        public String getMessage() {
            return message;
        }
    }

    private final String gstin;
    private final Error error;
    private final String stateCode;
    private final String stateName;
    private final String pan;
    private final char entityNumber;
    private final char checkDigit;

    GSTINCheckResult(String gstin, Error error, String stateCode, String stateName,
                     String pan, char entityNumber, char checkDigit) {
        this.gstin = gstin;
        this.error = error;
        this.stateCode = stateCode;
        this.stateName = stateName;
        this.pan = pan;
        this.entityNumber = entityNumber;
        this.checkDigit = checkDigit;
    }

    /**
     * Tells whether the GSTIN passed every offline check.
     *
     * @return {@code true} when the GSTIN passed the format, state code and check digit checks
     */
    public boolean isValid() {
        return error == null;
    }

    /**
     * Returns the reason the check failed.
     *
     * @return the reason the check failed, or {@code null} when {@link #isValid()} is {@code true}
     */
    public Error getError() {
        return error;
    }

    /**
     * Returns a human readable failure message.
     *
     * @return the failure message, or {@code null} when {@link #isValid()} is {@code true}
     */
    public String getMessage() {
        return error == null ? null : error.getMessage();
    }

    /**
     * Returns the GSTIN that was checked.
     *
     * @return the GSTIN as it was checked (trimmed and upper-cased), or {@code null} if the input was blank
     */
    public String getGstin() {
        return gstin;
    }

    /**
     * Returns the two digit state code.
     *
     * @return the two digit state / jurisdiction code, or {@code null} if the format check failed
     */
    public String getStateCode() {
        return stateCode;
    }

    /**
     * Returns the state or jurisdiction name.
     *
     * @return the state / jurisdiction name for {@link #getStateCode()}, or {@code null} if it is unknown
     */
    public String getStateName() {
        return stateName;
    }

    /**
     * Returns the PAN embedded in the GSTIN.
     *
     * @return the 10 character PAN embedded in the GSTIN, or {@code null} if the format check failed
     */
    public String getPan() {
        return pan;
    }

    /**
     * Returns the registration number within the state.
     *
     * @return the registration (entity) number of this PAN within the state, or {@code '\0'} if the format check failed
     */
    public char getEntityNumber() {
        return entityNumber;
    }

    /**
     * Returns the 15th character of the GSTIN.
     *
     * @return the 15th character of the GSTIN, or {@code '\0'} if the format check failed
     */
    public char getCheckDigit() {
        return checkDigit;
    }

    @Override
    public String toString() {
        return isValid()
                ? "GSTINCheckResult{valid, gstin=" + gstin + ", state=" + stateName + "}"
                : "GSTINCheckResult{invalid, gstin=" + gstin + ", error=" + error + "}";
    }
}
