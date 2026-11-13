package io.github.jingou.nrst.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one CarrierConfig key this module is allowed to touch.
 *
 * <p>The hook applies the change with {@code bundle.putIntArray(KEY, values)}; the class below is
 * the pure, unit-tested statement of that same rule: take the original config map, replace exactly
 * this key, leave every other key exactly as it was.
 */
public final class CarrierConfigPatch {

    /** {@code CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY}. */
    public static final String KEY = "5g_nr_ssrsrp_thresholds_int_array";

    private CarrierConfigPatch() {
    }

    /**
     * Returns a copy of {@code original} with only {@link #KEY} replaced.
     *
     * <p>Never mutates the argument: the caller's bundle is the framework's freshly built config
     * and other readers may still hold a reference to it.
     */
    public static Map<String, Object> apply(Map<String, Object> original, Thresholds thresholds) {
        Map<String, Object> patched = new LinkedHashMap<>(original);
        patched.put(KEY, thresholds.toArray());
        return patched;
    }
}
