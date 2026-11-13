package io.github.nku100.carrierims.data;

import android.content.SharedPreferences;

import io.github.nku100.carrierims.config.LoadResult;
import io.github.nku100.carrierims.config.ThresholdRecord;
import io.github.nku100.carrierims.config.Thresholds;

import java.util.Map;

/**
 * Persistence layer for the four thresholds.
 *
 * <p>The same class is used on both sides of the process boundary:
 *
 * <ul>
 *   <li>the GUI writes through the writable {@code SharedPreferences} handed over by
 *       {@code XposedService#getRemotePreferences};</li>
 *   <li>the hook reads through the read-only instance returned by
 *       {@code XposedInterface#getRemotePreferences}.</li>
 * </ul>
 *
 * <p>Both sides therefore share one encoding, one schema version and one checksum implementation.
 * A save writes every key in a single {@code commit()}, which the framework applies as one update:
 * the hook can only ever observe the old record or the new one.
 */
public final class ThresholdStore {

    /**
     * Preferences group. LSPosed stores it in its own database, so it survives reboots and does not
     * depend on the module's app-private storage being readable by {@code com.android.phone}.
     */
    public static final String PREFS_GROUP = "nr_ssrsrp_thresholds";

    private final SharedPreferences prefs;

    public ThresholdStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Reads and validates the stored record. Never throws for bad data. */
    public LoadResult load() {
        Map<String, ?> values;
        try {
            values = prefs.getAll();
        } catch (Throwable t) {
            return LoadResult.invalid("Cannot read preferences: " + t);
        }
        return ThresholdRecord.decode(values);
    }

    /**
     * Validates and writes the record atomically.
     *
     * <p>The values are validated again here even though the GUI already validated them: this is
     * the last gate before data reaches CarrierConfig, and it must not depend on the caller.
     */
    public WriteResult save(Thresholds thresholds, long appliedAtMillis) {
        if (thresholds == null) {
            return WriteResult.failure("No thresholds supplied.");
        }
        if (!Thresholds.validate(thresholds.toArray()).isOk()) {
            return WriteResult.failure("Refusing to save an invalid configuration.");
        }

        Map<String, Object> record = ThresholdRecord.encode(thresholds, appliedAtMillis);
        try {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(entry.getKey(), (Long) value);
                } else {
                    return WriteResult.failure(
                            "Unsupported value type for " + entry.getKey() + ".");
                }
            }
            // commit(), not apply(): the GUI reports success only after the framework acknowledged.
            if (!editor.commit()) {
                return WriteResult.failure("The framework rejected the write.");
            }
        } catch (Throwable t) {
            return WriteResult.failure("Cannot write preferences: " + t);
        }
        return WriteResult.success();
    }

    /** Result of a write. */
    public static final class WriteResult {
        private final boolean ok;
        private final String message;

        private WriteResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static WriteResult success() {
            return new WriteResult(true, null);
        }

        static WriteResult failure(String message) {
            return new WriteResult(false, message);
        }

        public boolean isOk() {
            return ok;
        }

        public String message() {
            return message;
        }
    }
}
