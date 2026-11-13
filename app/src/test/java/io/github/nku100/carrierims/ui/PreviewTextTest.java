package io.github.nku100.carrierims.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.nku100.carrierims.config.Thresholds;

/**
 * Locked-in expectations for the preview text. These are the strings a user sees next to the
 * coloured bands, so a change here is a product change, not a refactor.
 */
public class PreviewTextTest {

    private final Thresholds thresholds = Thresholds.of(-128, -118, -108, -98);

    @Test
    public void previewRangesMatchAospBoundaries() {
        assertEquals("< -128 dBm", thresholds.bandLabel(Thresholds.LEVEL_NONE));
        assertEquals("-128 to -118 dBm", thresholds.bandLabel(Thresholds.LEVEL_POOR));
        assertEquals("-118 to -108 dBm", thresholds.bandLabel(Thresholds.LEVEL_MODERATE));
        assertEquals("-108 to -98 dBm", thresholds.bandLabel(Thresholds.LEVEL_GOOD));
        assertEquals(">= -98 dBm (max -44)", thresholds.bandLabel(Thresholds.LEVEL_EXCELLENT));
    }

    @Test
    public void levelFourIsAospGreat() {
        // CarrierConfig documentation calls level 4 EXCELLENT; SignalStrength calls it GREAT.
        assertEquals("EXCELLENT", Thresholds.levelName(4));
        assertEquals("SIGNAL_STRENGTH_GREAT", Thresholds.levelCodeName(4));
    }

    @Test
    public void previewUsesTheEditedValues() {
        Thresholds edited = Thresholds.of(-130, -115, -100, -85);

        assertEquals("-130 to -115 dBm", edited.bandLabel(Thresholds.LEVEL_POOR));
        assertEquals(">= -85 dBm (max -44)", edited.bandLabel(Thresholds.LEVEL_EXCELLENT));
    }

    @Test
    public void bandViewInputIsACopy() {
        int[] values = thresholds.toArray();
        assertArrayEquals(new int[]{-128, -118, -108, -98}, values);

        values[0] = 0;
        assertEquals(-128, thresholds.get(0));
    }
}
