package io.github.jingou.nrst.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ThresholdsTest {

    @Test
    public void acceptsProjectDefaults() {
        Thresholds thresholds = Thresholds.of(-120, -113, -105, -97);

        assertEquals(-120, thresholds.get(0));
        assertEquals(-97, thresholds.get(3));
        assertEquals("-120, -113, -105, -97", thresholds.csv());
    }

    @Test
    public void acceptsBoundaryValues() {
        assertTrue(Thresholds.validate(-140, -100, -60, -44).isOk());
    }

    @Test
    public void rejectsValuesBelowFrameworkMinimum() {
        Validation validation = Thresholds.validate(-141, -118, -108, -98);

        assertFalse(validation.isOk());
        assertEquals(Validation.Code.OUT_OF_RANGE, validation.code());
        assertEquals(0, validation.index());
        assertTrue(validation.message().contains("-140"));
    }

    @Test
    public void rejectsValuesAboveFrameworkMaximum() {
        Validation validation = Thresholds.validate(-128, -118, -108, -43);

        assertFalse(validation.isOk());
        assertEquals(Validation.Code.OUT_OF_RANGE, validation.code());
        assertEquals(3, validation.index());
    }

    @Test
    public void rejectsEqualNeighbours() {
        Validation validation = Thresholds.validate(-128, -118, -118, -98);

        assertFalse(validation.isOk());
        assertEquals(Validation.Code.NOT_STRICTLY_INCREASING, validation.code());
        assertEquals(2, validation.index());
    }

    @Test
    public void rejectsDescendingValues() {
        Validation validation = Thresholds.validate(-98, -108, -118, -128);

        assertFalse(validation.isOk());
        assertEquals(Validation.Code.NOT_STRICTLY_INCREASING, validation.code());
        assertEquals(1, validation.index());
    }

    @Test
    public void rejectsWrongCount() {
        assertEquals(Validation.Code.WRONG_COUNT, Thresholds.validate(-128, -118, -108).code());
        assertEquals(Validation.Code.WRONG_COUNT, Thresholds.validate().code());
    }

    @Test
    public void ofThrowsWithValidationAttached() {
        try {
            Thresholds.of(-128, -128, -108, -98);
            fail("expected InvalidConfigException");
        } catch (Thresholds.InvalidConfigException e) {
            assertEquals(Validation.Code.NOT_STRICTLY_INCREASING, e.validation().code());
        }
    }

    @Test
    public void levelsFollowCellSignalStrengthNr() {
        Thresholds thresholds = Thresholds.of(-128, -118, -108, -98);

        // Below the first threshold Android reports SIGNAL_STRENGTH_NONE_OR_UNKNOWN.
        assertEquals(Thresholds.LEVEL_NONE, thresholds.levelFor(-129));
        assertEquals(Thresholds.LEVEL_NONE, thresholds.levelFor(-140));

        // Boundaries are inclusive on the higher bucket (>= thresholds[i]).
        assertEquals(Thresholds.LEVEL_POOR, thresholds.levelFor(-128));
        assertEquals(Thresholds.LEVEL_POOR, thresholds.levelFor(-119));
        assertEquals(Thresholds.LEVEL_MODERATE, thresholds.levelFor(-118));
        assertEquals(Thresholds.LEVEL_GOOD, thresholds.levelFor(-108));
        assertEquals(Thresholds.LEVEL_EXCELLENT, thresholds.levelFor(-98));
        assertEquals(Thresholds.LEVEL_EXCELLENT, thresholds.levelFor(-44));
    }

    @Test
    public void levelNamesMatchAndroidConstants() {
        assertEquals("NONE", Thresholds.levelName(Thresholds.LEVEL_NONE));
        assertEquals("EXCELLENT", Thresholds.levelName(Thresholds.LEVEL_EXCELLENT));
        assertEquals("SIGNAL_STRENGTH_NONE_OR_UNKNOWN",
                Thresholds.levelCodeName(Thresholds.LEVEL_NONE));
        assertEquals("SIGNAL_STRENGTH_GREAT",
                Thresholds.levelCodeName(Thresholds.LEVEL_EXCELLENT));
    }

    @Test
    public void bandLabelsUseTheConfiguredBounds() {
        Thresholds thresholds = Thresholds.of(-128, -118, -108, -98);

        assertEquals("< -128 dBm", thresholds.bandLabel(Thresholds.LEVEL_NONE));
        assertEquals("-128 to -118 dBm", thresholds.bandLabel(Thresholds.LEVEL_POOR));
        assertEquals("-118 to -108 dBm", thresholds.bandLabel(Thresholds.LEVEL_MODERATE));
        assertEquals("-108 to -98 dBm", thresholds.bandLabel(Thresholds.LEVEL_GOOD));
        assertEquals(">= -98 dBm (max -44)", thresholds.bandLabel(Thresholds.LEVEL_EXCELLENT));
    }

    @Test
    public void defensiveCopiesOfValues() {
        int[] input = {-128, -118, -108, -98};
        Thresholds thresholds = Thresholds.of(input);

        input[0] = 0;
        int[] output = thresholds.toArray();
        output[3] = 0;

        assertEquals(-128, thresholds.get(0));
        assertEquals(-98, thresholds.get(3));
    }

    @Test
    public void equalityIsByValue() {
        assertEquals(Thresholds.of(-120, -113, -105, -97), Thresholds.defaults());
        assertNotEquals(Thresholds.of(-120, -113, -105, -97),
                Thresholds.of(-122, -113, -105, -97));
    }
}
