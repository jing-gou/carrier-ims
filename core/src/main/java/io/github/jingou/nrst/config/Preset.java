package io.github.jingou.nrst.config;

/**
 * Vendor presets for the four NR SS-RSRP thresholds.
 *
 * <p>Values are stored **ascending** (threshold1 &lt; threshold2 &lt; threshold3 &lt; threshold4),
 * because that is the order {@link Thresholds#validate} requires and the order Android's
 * {@code CellSignalStrengthNr} indexes. Brand tables are commonly published descending, so the
 * numbers here may look "reversed" compared with the table they came from:
 *
 * <pre>
 * brand    published (T1..T4)      stored ascending
 * Samsung  -85  -95  -105  -115    -115, -105, -95, -85
 * OPPO     -97  -105 -113  -120    -120, -113, -105, -97
 * vivo     -97  -105 -110  -115    -115, -110, -105, -97
 * Huawei   -90  -103 -113  -120    -120, -113, -103, -90
 * </pre>
 */
public enum Preset {

    SAMSUNG("samsung", -115, -105, -95, -85),
    OPPO("oppo", -120, -113, -105, -97),
    VIVO("vivo", -115, -110, -105, -97),
    HUAWEI("huawei", -120, -113, -103, -90);

    private final String id;
    private final Thresholds thresholds;

    Preset(String id, int threshold1, int threshold2, int threshold3, int threshold4) {
        this.id = id;
        this.thresholds = Thresholds.of(threshold1, threshold2, threshold3, threshold4);
    }

    /** Stable identifier, useful for storage or tests. Not shown to users. */
    public String id() {
        return id;
    }

    public Thresholds thresholds() {
        return thresholds;
    }

    /** The four values in ascending order, safe to hand to a bundle. */
    public int[] dbmValues() {
        return thresholds.toArray();
    }

    /** The preset selected on first run and by "Restore defaults". */
    public static Preset defaultPreset() {
        return OPPO;
    }

    /** Matching preset for these values, or {@code null} when they are custom. */
    public static Preset of(Thresholds thresholds) {
        if (thresholds == null) {
            return null;
        }
        for (Preset preset : values()) {
            if (preset.thresholds.equals(thresholds)) {
                return preset;
            }
        }
        return null;
    }
}
