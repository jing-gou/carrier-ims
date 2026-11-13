package io.github.jingou.nrst.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.github.jingou.nrst.config.LoadResult;
import io.github.jingou.nrst.config.ThresholdRecord;
import io.github.jingou.nrst.config.Thresholds;

public class ThresholdStoreTest {

    private FakeSharedPreferences prefs;
    private ThresholdStore store;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
        store = new ThresholdStore(prefs);
    }

    @Test
    public void emptyPreferencesAreAbsent() {
        assertEquals(LoadResult.Status.ABSENT, store.load().status());
    }

    @Test
    public void saveThenLoadRoundTrips() {
        Thresholds thresholds = Thresholds.of(-130, -115, -105, -95);

        assertTrue(store.save(thresholds, 0L).isOk());

        LoadResult result = store.load();
        assertEquals(LoadResult.Status.VALID, result.status());
        assertEquals(thresholds, result.thresholds());
    }

    @Test
    public void writesTheWholeRecordInASingleCommit() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);

        // One commit means the framework applies the record as one atomic update.
        assertEquals(1, prefs.commitCount());
        assertEquals(6, prefs.getAll().size()); // version + 4 thresholds + checksum
    }

    @Test
    public void overwritesPreviousConfiguration() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);
        store.save(Thresholds.of(-135, -120, -100, -90), 0L);

        assertEquals(Thresholds.of(-135, -120, -100, -90), store.load().thresholds());
        assertEquals(2, prefs.commitCount());
    }

    @Test
    public void rejectsNullWithoutTouchingStorage() {
        store.save(Thresholds.defaults(), 0L);
        int commits = prefs.commitCount();

        ThresholdStore.WriteResult result = store.save(null, 0L);

        assertFalse(result.isOk());
        assertEquals(commits, prefs.commitCount());
        assertEquals(Thresholds.defaults(), store.load().thresholds());
    }

    @Test
    public void reportsCommitFailureAndKeepsPreviousValue() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);
        prefs.failCommits(true);

        ThresholdStore.WriteResult result =
                store.save(Thresholds.of(-130, -120, -110, -100), 0L);

        assertFalse(result.isOk());
        assertTrue(result.message().contains("rejected"));
        assertEquals(Thresholds.of(-128, -118, -108, -98), store.load().thresholds());
    }

    @Test
    public void corruptedValueIsRejectedByChecksum() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_2, -60);

        LoadResult result = store.load();

        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("Checksum"));
        assertNull(result.thresholds());
    }

    @Test
    public void missingFieldIsInvalid() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);
        prefs.removeRaw(ThresholdRecord.KEY_THRESHOLD_4);

        assertEquals(LoadResult.Status.INVALID, store.load().status());
    }

    @Test
    public void partiallyWrittenRecordIsInvalid() {
        // Simulates a write that stopped halfway instead of using a single commit.
        prefs.putRaw(ThresholdRecord.KEY_SCHEMA_VERSION, ThresholdRecord.SCHEMA_VERSION);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_1, -128);

        LoadResult result = store.load();

        assertEquals(LoadResult.Status.INVALID, result.status());
        assertNotNull(result.message());
    }

    @Test
    public void unsupportedSchemaVersionIsInvalid() {
        store.save(Thresholds.of(-128, -118, -108, -98), 0L);
        prefs.putRaw(ThresholdRecord.KEY_SCHEMA_VERSION, ThresholdRecord.SCHEMA_VERSION + 1);

        LoadResult result = store.load();

        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("Unsupported schema version"));
    }

    @Test
    public void invalidRecordNeverYieldsThresholds() {
        prefs.putRaw(ThresholdRecord.KEY_SCHEMA_VERSION, 1);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_1, -108);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_2, -118);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_3, -128);
        prefs.putRaw(ThresholdRecord.KEY_THRESHOLD_4, -140);

        LoadResult result = store.load();

        assertEquals(LoadResult.Status.INVALID, result.status());
        assertNull(result.thresholds());
    }
}
