package io.github.nku100.carrierims.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import io.github.nku100.carrierims.config.ThresholdRecord;
import io.github.nku100.carrierims.config.Thresholds;

/**
 * Hook-side behaviour: a stored record is only ever handed to CarrierConfig when it is valid.
 *
 * <p>Runs as a plain JVM test. {@code SystemClock} is a stub that returns 0, so the constructor's
 * initial read is the one under test and the periodic refresh timer does not interfere.
 */
public class ThresholdSourceTest {

    private FakeSharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
    }

    @Test
    public void absentConfigurationYieldsNull() {
        ThresholdSource source = new ThresholdSource(prefs);

        assertNull(source.get());
        assertNull(source.lastInvalidReason());
    }

    @Test
    public void validConfigurationIsReturned() {
        new ThresholdStore(prefs).save(Thresholds.of(-128, -118, -108, -98), 0L);

        ThresholdSource source = new ThresholdSource(prefs);

        assertEquals(Thresholds.of(-128, -118, -108, -98), source.get());
    }

    @Test
    public void corruptedConfigurationYieldsNullAndAReason() {
        new ThresholdStore(prefs).save(Thresholds.of(-128, -118, -108, -98), 0L);
        prefs.putRaw(ThresholdRecord.KEY_CHECKSUM, 12345);

        ThresholdSource source = new ThresholdSource(prefs);

        assertNull(source.get());
        assertNotNull(source.lastInvalidReason());
    }

    @Test
    public void updateThroughPreferencesIsObserved() {
        new ThresholdStore(prefs).save(Thresholds.of(-128, -118, -108, -98), 0L);
        ThresholdSource source = new ThresholdSource(prefs);
        assertEquals(Thresholds.of(-128, -118, -108, -98), source.get());

        // The GUI commits a new record; the framework notifies the listener in the hooked process.
        new ThresholdStore(prefs).save(Thresholds.of(-135, -120, -100, -90), 0L);

        assertEquals(Thresholds.of(-135, -120, -100, -90), source.get());
    }

    @Test
    public void invalidUpdateStopsInjectionInsteadOfKeepingStaleValues() {
        new ThresholdStore(prefs).save(Thresholds.of(-128, -118, -108, -98), 0L);
        ThresholdSource source = new ThresholdSource(prefs);
        assertEquals(Thresholds.of(-128, -118, -108, -98), source.get());

        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_1, -60);
        prefs.edit().putInt("unrelated", 1).commit();

        assertNull(source.get());
        assertNotNull(source.lastInvalidReason());
    }
}
