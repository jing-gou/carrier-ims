package io.github.nku100.carrierims.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ThresholdRecordTest {

    private static Map<String, Object> validRecord() {
        return ThresholdRecord.encode(Thresholds.of(-128, -118, -108, -98), 0L);
    }

    @Test
    public void roundTrips() {
        LoadResult result = ThresholdRecord.decode(validRecord());

        assertEquals(LoadResult.Status.VALID, result.status());
        assertEquals(Thresholds.of(-128, -118, -108, -98), result.thresholds());
    }

    @Test
    public void emptyMapIsAbsentNotInvalid() {
        assertEquals(LoadResult.Status.ABSENT, ThresholdRecord.decode(new HashMap<>()).status());
        assertEquals(LoadResult.Status.ABSENT, ThresholdRecord.decode(null).status());
    }

    @Test
    public void rejectsMissingVersion() {
        Map<String, Object> record = validRecord();
        record.remove(ThresholdRecord.KEY_SCHEMA_VERSION);

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("schema version"));
    }

    @Test
    public void rejectsFutureSchemaVersion() {
        Map<String, Object> record = validRecord();
        record.put(ThresholdRecord.KEY_SCHEMA_VERSION, 2);

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("Unsupported schema version 2"));
    }

    @Test
    public void rejectsMissingThreshold() {
        Map<String, Object> record = validRecord();
        record.remove(ThresholdRecord.KEY_THRESHOLD_3);

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("threshold_3"));
    }

    @Test
    public void rejectsWrongValueType() {
        Map<String, Object> record = validRecord();
        record.put(ThresholdRecord.KEY_THRESHOLD_1, "-128");

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("not an integer"));
    }

    @Test
    public void rejectsCorruptedValuesViaChecksum() {
        Map<String, Object> record = validRecord();
        record.put(ThresholdRecord.KEY_THRESHOLD_2, -100);

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("Checksum"));
        assertNull(result.thresholds());
    }

    @Test
    public void rejectsMissingChecksum() {
        Map<String, Object> record = validRecord();
        record.remove(ThresholdRecord.KEY_CHECKSUM);

        assertEquals(LoadResult.Status.INVALID, ThresholdRecord.decode(record).status());
    }

    @Test
    public void rejectsValidChecksumWithInvalidOrdering() {
        // Someone hand-wrote a consistent but illegal record: checksum passes, ordering does not.
        int[] values = {-108, -118, -128, -140};
        Map<String, Object> record = new HashMap<>();
        record.put(ThresholdRecord.KEY_SCHEMA_VERSION, ThresholdRecord.SCHEMA_VERSION);
        for (int i = 0; i < values.length; i++) {
            record.put(ThresholdRecord.thresholdKey(i), values[i]);
        }
        record.put(ThresholdRecord.KEY_CHECKSUM,
                ThresholdRecord.checksum(ThresholdRecord.SCHEMA_VERSION, values));

        LoadResult result = ThresholdRecord.decode(record);
        assertEquals(LoadResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("strictly increasing"));
    }

    @Test
    public void appliedAtIsNotPartOfTheChecksum() {
        Map<String, Object> record = validRecord();
        record.put(ThresholdRecord.KEY_APPLIED_AT, 123456789L);

        assertEquals(LoadResult.Status.VALID, ThresholdRecord.decode(record).status());
    }

    @Test
    public void checksumIsStable() {
        int[] values = {-128, -118, -108, -98};
        assertEquals(ThresholdRecord.checksum(1, values), ThresholdRecord.checksum(1, values));
        assertFalse(ThresholdRecord.checksum(1, values)
                == ThresholdRecord.checksum(1, new int[]{-128, -118, -108, -97}));
        assertFalse(ThresholdRecord.checksum(1, values)
                == ThresholdRecord.checksum(2, values));
    }
}
