package io.github.nku100.carrierims.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class CarrierConfigPatchTest {

    private static Map<String, Object> frameworkConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("carrier_volte_available_bool", Boolean.FALSE);
        config.put("carrier_wfc_ims_available_bool", Boolean.FALSE);
        config.put("vonr_enabled_bool", Boolean.FALSE);
        config.put("editable_enhanced_4g_lte_bool", Boolean.FALSE);
        config.put("5g_nr_ssrsrp_thresholds_int_array", new int[]{-110, -90, -80, -65});
        config.put("carrier_nr_availabilities_int_array", new int[]{2});
        config.put("some_string_key", "untouched");
        return config;
    }

    @Test
    public void keyMatchesCarrierConfigManagerConstant() {
        // CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY
        assertEquals("5g_nr_ssrsrp_thresholds_int_array", CarrierConfigPatch.KEY);
    }

    @Test
    public void replacesOnlyTheThresholdKey() {
        Map<String, Object> original = frameworkConfig();
        Map<String, Object> patched =
                CarrierConfigPatch.apply(original, Thresholds.of(-128, -118, -108, -98));

        assertArrayEquals(new int[]{-128, -118, -108, -98},
                (int[]) patched.get(CarrierConfigPatch.KEY));

        for (Map.Entry<String, Object> entry : original.entrySet()) {
            if (entry.getKey().equals(CarrierConfigPatch.KEY)) {
                continue;
            }
            assertEquals(entry.getKey(), entry.getValue(), patched.get(entry.getKey()));
        }
        assertEquals(original.size(), patched.size());
    }

    @Test
    public void doesNotMutateTheOriginalBundle() {
        Map<String, Object> original = frameworkConfig();
        Map<String, Object> before = new LinkedHashMap<>(original);

        CarrierConfigPatch.apply(original, Thresholds.of(-128, -118, -108, -98));

        assertNotSame(before, original);
        assertArrayEquals(new int[]{-110, -90, -80, -65},
                (int[]) original.get(CarrierConfigPatch.KEY));
        assertEquals(before, original);
    }

    @Test
    public void doesNotAffectOtherCarrierConfigFeatures() {
        Map<String, Object> patched = CarrierConfigPatch.apply(
                frameworkConfig(), Thresholds.of(-128, -118, -108, -98));

        assertFalse((Boolean) patched.get("carrier_volte_available_bool"));
        assertFalse((Boolean) patched.get("carrier_wfc_ims_available_bool"));
        assertFalse((Boolean) patched.get("vonr_enabled_bool"));
        assertFalse((Boolean) patched.get("editable_enhanced_4g_lte_bool"));
        assertEquals("untouched", patched.get("some_string_key"));
        assertArrayEquals(new int[]{2},
                (int[]) patched.get("carrier_nr_availabilities_int_array"));
        assertTrue(patched.containsKey(CarrierConfigPatch.KEY));
    }
}
