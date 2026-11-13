package io.github.nku100.carrierims.config;

/**
 * Result of validating four NR SS-RSRP thresholds.
 *
 * <p>Kept free of Android and of UI strings so the GUI and the hook validate with the exact same
 * rules. {@link #message()} is a short English explanation suitable for a field-level error.
 */
public final class Validation {

    public enum Code {
        OK,
        WRONG_COUNT,
        EMPTY,
        NOT_A_NUMBER,
        OUT_OF_RANGE,
        NOT_STRICTLY_INCREASING,
    }

    private static final Validation OK = new Validation(Code.OK, null, -1);

    private final Code code;
    private final String message;
    private final int index;

    private Validation(Code code, String message, int index) {
        this.code = code;
        this.message = message;
        this.index = index;
    }

    public static Validation ok() {
        return OK;
    }

    public static Validation error(Code code, String message, int index) {
        if (code == Code.OK) {
            throw new IllegalArgumentException("OK is not an error");
        }
        return new Validation(code, message, index);
    }

    public boolean isOk() {
        return code == Code.OK;
    }

    public Code code() {
        return code;
    }

    /** Human readable reason, or {@code null} when valid. */
    public String message() {
        return message;
    }

    /**
     * Index (0-3) of the field that caused the problem, or -1 when the problem is not attributable
     * to a single field (for example a wrong count).
     */
    public int index() {
        return index;
    }

    @Override
    public String toString() {
        return isOk() ? "Validation{OK}" : "Validation{" + code + ": " + message + "}";
    }
}
