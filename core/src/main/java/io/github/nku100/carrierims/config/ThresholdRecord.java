package io.github.nku100.carrierims.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire format shared by the GUI and the hook.
 *
 * <p>Everything lives in one preferences group and is written with a single commit, so the hook
 * observes either the previous record or the next one — never a half-written mix. The record
 * carries a schema version and a checksum over the values, which is what lets the hook reject a
 * corrupted or hand-edited file instead of injecting garbage into
 * {@code 5g_nr_ssrsrp_thresholds_int_array}.
 *
 * <p>Version 1 layout:
 *
 * <pre>
 *   schema_version : int    always 1
 *   threshold_1    : int    lowest boundary, dBm
 *   threshold_2    : int
 *   threshold_3    : int
 *   threshold_4    : int    highest boundary, dBm
 *   checksum       : int    FNV-1a over "1|t1|t2|t3|t4"
 *   applied_at     : long   wall clock, display only, excluded from the checksum
 * </pre>
 */
public final class ThresholdRecord {

    public static final int SCHEMA_VERSION = 1;

    public static final String KEY_SCHEMA_VERSION = "schema_version";
    public static final String KEY_THRESHOLD_1 = "threshold_1";
    public static final String KEY_THRESHOLD_2 = "threshold_2";
    public static final String KEY_THRESHOLD_3 = "threshold_3";
    public static final String KEY_THRESHOLD_4 = "threshold_4";
    public static final String KEY_CHECKSUM = "checksum";
    public static final String KEY_APPLIED_AT = "applied_at";

    private static final String[] THRESHOLD_KEYS = {
            KEY_THRESHOLD_1, KEY_THRESHOLD_2, KEY_THRESHOLD_3, KEY_THRESHOLD_4,
    };

    private ThresholdRecord() {
    }

    /**
     * Values to hand to a single {@code SharedPreferences.Editor.commit()}.
     *
     * @param appliedAtMillis wall clock time of the write, or 0 when not interesting
     */
    public static Map<String, Object> encode(Thresholds thresholds, long appliedAtMillis) {
        int[] values = thresholds.toArray();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        for (int i = 0; i < Thresholds.COUNT; i++) {
            map.put(THRESHOLD_KEYS[i], values[i]);
        }
        map.put(KEY_CHECKSUM, checksum(SCHEMA_VERSION, values));
        if (appliedAtMillis > 0) {
            map.put(KEY_APPLIED_AT, appliedAtMillis);
        }
        return map;
    }

    /** Reads a record written by {@link #encode}; never throws. */
    public static LoadResult decode(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return LoadResult.absent();
        }

        Object version = values.get(KEY_SCHEMA_VERSION);
        if (version == null) {
            return LoadResult.invalid("Missing schema version.");
        }
        if (!(version instanceof Integer)) {
            return LoadResult.invalid("Schema version is not an integer.");
        }
        int schema = (Integer) version;
        if (schema != SCHEMA_VERSION) {
            return LoadResult.invalid("Unsupported schema version " + schema + ".");
        }

        int[] thresholds = new int[Thresholds.COUNT];
        for (int i = 0; i < Thresholds.COUNT; i++) {
            Object raw = values.get(THRESHOLD_KEYS[i]);
            if (raw == null) {
                return LoadResult.invalid("Missing " + THRESHOLD_KEYS[i] + ".");
            }
            if (!(raw instanceof Integer)) {
                return LoadResult.invalid(THRESHOLD_KEYS[i] + " is not an integer.");
            }
            thresholds[i] = (Integer) raw;
        }

        Object checksum = values.get(KEY_CHECKSUM);
        if (!(checksum instanceof Integer)) {
            return LoadResult.invalid("Missing or invalid checksum.");
        }
        if ((Integer) checksum != checksum(schema, thresholds)) {
            return LoadResult.invalid("Checksum mismatch.");
        }

        Validation validation = Thresholds.validate(thresholds);
        if (!validation.isOk()) {
            return LoadResult.invalid(validation.message());
        }
        return LoadResult.valid(Thresholds.of(thresholds));
    }

    /** FNV-1a, 32 bit. Deterministic across processes and platforms. */
    public static int checksum(int schemaVersion, int[] thresholds) {
        String canonical = schemaVersion
                + "|" + thresholds[0]
                + "|" + thresholds[1]
                + "|" + thresholds[2]
                + "|" + thresholds[3];
        int hash = 0x811C9DC5;
        for (byte b : canonical.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= 0x01000193;
        }
        return hash;
    }

    public static String thresholdKey(int index) {
        return THRESHOLD_KEYS[index];
    }
}
