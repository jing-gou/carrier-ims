package io.github.nku100.carrierims.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PresetTest {

    @Test
    public void oppoIsTheDefaultPreset() {
        assertEquals(Preset.OPPO, Preset.defaultPreset());
        assertEquals(Thresholds.defaults(), Preset.OPPO.thresholds());
    }

    @Test
    public void everyPresetIsAcceptedByTheValidator() {
        for (Preset preset : Preset.values()) {
            assertTrue(preset.id(), Thresholds.validate(preset.dbmValues()).isOk());
        }
    }

    @Test
    public void storedValuesAreAscendingNotAsPublished() {
        // Brand tables are usually written T1..T4 descending; the stored order must be ascending.
        assertEquals(Thresholds.of(-115, -105, -95, -85), Preset.SAMSUNG.thresholds());
        assertEquals(Thresholds.of(-120, -113, -105, -97), Preset.OPPO.thresholds());
        assertEquals(Thresholds.of(-115, -110, -105, -97), Preset.VIVO.thresholds());
        assertEquals(Thresholds.of(-120, -113, -103, -90), Preset.HUAWEI.thresholds());
    }

    @Test
    public void lookupFindsTheMatchingPreset() {
        for (Preset preset : Preset.values()) {
            assertEquals(preset, Preset.of(preset.thresholds()));
        }
    }

    @Test
    public void customValuesHaveNoPreset() {
        assertNull(Preset.of(Thresholds.of(-140, -100, -60, -44)));
        assertNull(Preset.of(null));
    }
}
