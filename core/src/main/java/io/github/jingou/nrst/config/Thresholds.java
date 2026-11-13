package io.github.jingou.nrst.config;

import java.util.Arrays;
import java.util.Locale;

/**
 * The four Android NR SS-RSRP signal thresholds.
 *
 * <p>These map 1:1 onto {@code CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY}
 * ({@code 5g_nr_ssrsrp_thresholds_int_array}). Android documents the bucket layout as:
 *
 * <pre>
 *   NONE      : [-140, threshold1)
 *   POOR      : [threshold1, threshold2)
 *   MODERATE  : [threshold2, threshold3)
 *   GOOD      : [threshold3, threshold4)
 *   EXCELLENT : [threshold4, -44]
 * </pre>
 *
 * <p>{@link android.telephony.CellSignalStrengthNr} implements the same table in
 * {@code updateLevelWithMeasure}: {@code >= thresholds[3]} is level 4
 * ({@code SIGNAL_STRENGTH_GREAT}), {@code >= thresholds[0]} is level 1
 * ({@code SIGNAL_STRENGTH_POOR}), and anything below {@code thresholds[0]} is level 0
 * ({@code SIGNAL_STRENGTH_NONE_OR_UNKNOWN}). The level names here follow the CarrierConfig
 * documentation; {@link #levelCodeName(int)} gives the constant Android uses at runtime.
 */
public final class Thresholds {

    /** Lower bound accepted by the framework, in dBm. */
    public static final int MIN_DBM = -140;

    /** Upper bound accepted by the framework, in dBm. */
    public static final int MAX_DBM = -44;

    public static final int COUNT = 4;

    /** Level 0 — {@code SIGNAL_STRENGTH_NONE_OR_UNKNOWN}. */
    public static final int LEVEL_NONE = 0;
    /** Level 1 — {@code SIGNAL_STRENGTH_POOR}. */
    public static final int LEVEL_POOR = 1;
    /** Level 2 — {@code SIGNAL_STRENGTH_MODERATE}. */
    public static final int LEVEL_MODERATE = 2;
    /** Level 3 — {@code SIGNAL_STRENGTH_GOOD}. */
    public static final int LEVEL_GOOD = 3;
    /** Level 4 — {@code SIGNAL_STRENGTH_GREAT}, shown as EXCELLENT in carrier config docs. */
    public static final int LEVEL_EXCELLENT = 4;

    /**
     * Defaults shown on first run and by "Restore defaults": -120, -113, -105, -97 dBm.
     *
     * <p>Stored in ascending order, because {@link #validate} requires
     * {@code threshold1 < threshold2 < threshold3 < threshold4}. The same four numbers written in
     * descending order (as they often appear in carrier config dumps) would be rejected.
     *
     * <p>For reference: AOSP's own default is {@code {-110, -90, -80, -65}}, and the previous
     * version of this module shipped {@code {-128, -118, -108, -98}}.
     */
    public static final int[] PROJECT_DEFAULTS = {-120, -113, -105, -97};

    private final int[] values;

    private Thresholds(int[] values) {
        this.values = values;
    }

    public static Validation validate(int... values) {
        if (values == null || values.length != COUNT) {
            return Validation.error(
                    Validation.Code.WRONG_COUNT,
                    "Exactly " + COUNT + " thresholds are required.",
                    -1);
        }
        for (int i = 0; i < COUNT; i++) {
            if (values[i] < MIN_DBM || values[i] > MAX_DBM) {
                return Validation.error(
                        Validation.Code.OUT_OF_RANGE,
                        "Threshold " + (i + 1) + " must be between " + MIN_DBM + " and "
                                + MAX_DBM + " dBm.",
                        i);
            }
        }
        for (int i = 1; i < COUNT; i++) {
            if (values[i] <= values[i - 1]) {
                return Validation.error(
                        Validation.Code.NOT_STRICTLY_INCREASING,
                        "Thresholds must be strictly increasing: threshold " + i
                                + " must be greater than threshold " + (i - 1) + ".",
                        i);
            }
        }
        return Validation.ok();
    }

    /**
     * @throws InvalidConfigException when the values are not four strictly increasing dBm values
     *                                inside {@link #MIN_DBM}..{@link #MAX_DBM}.
     */
    public static Thresholds of(int... values) {
        Validation validation = validate(values);
        if (!validation.isOk()) {
            throw new InvalidConfigException(validation);
        }
        return new Thresholds(values.clone());
    }

    public static Thresholds defaults() {
        return of(PROJECT_DEFAULTS);
    }

    /** Defensive copy; the returned array is safe to hand to {@code PersistableBundle}. */
    public int[] toArray() {
        return values.clone();
    }

    public int get(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IndexOutOfBoundsException("index " + index);
        }
        return values[index];
    }

    /**
     * Bucket for a measured SS-RSRP, exactly as {@code CellSignalStrengthNr} computes it.
     *
     * @param rsrpDbm measured SS-RSRP in dBm
     */
    public int levelFor(int rsrpDbm) {
        if (rsrpDbm >= values[3]) {
            return LEVEL_EXCELLENT;
        }
        if (rsrpDbm >= values[2]) {
            return LEVEL_GOOD;
        }
        if (rsrpDbm >= values[1]) {
            return LEVEL_MODERATE;
        }
        if (rsrpDbm >= values[0]) {
            return LEVEL_POOR;
        }
        return LEVEL_NONE;
    }

    /** Name from the CarrierConfig documentation. */
    public static String levelName(int level) {
        switch (level) {
            case LEVEL_NONE:
                return "NONE";
            case LEVEL_POOR:
                return "POOR";
            case LEVEL_MODERATE:
                return "MODERATE";
            case LEVEL_GOOD:
                return "GOOD";
            case LEVEL_EXCELLENT:
                return "EXCELLENT";
            default:
                throw new IllegalArgumentException("unknown level " + level);
        }
    }

    /** The {@code SignalStrength} constant name Android uses for this level. */
    public static String levelCodeName(int level) {
        switch (level) {
            case LEVEL_NONE:
                return "SIGNAL_STRENGTH_NONE_OR_UNKNOWN";
            case LEVEL_POOR:
                return "SIGNAL_STRENGTH_POOR";
            case LEVEL_MODERATE:
                return "SIGNAL_STRENGTH_MODERATE";
            case LEVEL_GOOD:
                return "SIGNAL_STRENGTH_GOOD";
            case LEVEL_EXCELLENT:
                return "SIGNAL_STRENGTH_GREAT";
            default:
                throw new IllegalArgumentException("unknown level " + level);
        }
    }

    /**
     * Inclusive/exclusive dBm range for one level, for the GUI preview.
     * Bounds come from this instance, so the text always matches the numbers on screen.
     */
    public String bandLabel(int level) {
        switch (level) {
            case LEVEL_NONE:
                return "< " + values[0] + " dBm";
            case LEVEL_POOR:
                return range(values[0], values[1]);
            case LEVEL_MODERATE:
                return range(values[1], values[2]);
            case LEVEL_GOOD:
                return range(values[2], values[3]);
            case LEVEL_EXCELLENT:
                return ">= " + values[3] + " dBm (max " + MAX_DBM + ")";
            default:
                throw new IllegalArgumentException("unknown level " + level);
        }
    }

    private static String range(int low, int high) {
        return low + " to " + high + " dBm";
    }

    /** Comma separated values, e.g. {@code -120, -113, -105, -97}. */
    public String csv() {
        return String.format(Locale.ROOT, "%d, %d, %d, %d",
                values[0], values[1], values[2], values[3]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Thresholds)) {
            return false;
        }
        return Arrays.equals(values, ((Thresholds) o).values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Thresholds{" + csv() + "}";
    }

    /** Thrown when invalid values are passed to {@link #of(int...)}. */
    public static final class InvalidConfigException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final transient Validation validation;

        InvalidConfigException(Validation validation) {
            super(validation.message());
            this.validation = validation;
        }

        public Validation validation() {
            return validation;
        }
    }
}
