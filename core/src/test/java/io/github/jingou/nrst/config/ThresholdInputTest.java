package io.github.jingou.nrst.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThresholdInputTest {

    @Test
    public void parsesFourValidIntegers() {
        ThresholdInput.Result result =
                ThresholdInput.parse("-120", "-113", "-105", "-97");

        assertTrue(result.isOk());
        assertEquals(Thresholds.defaults(), result.thresholds());
    }

    @Test
    public void trimsWhitespace() {
        assertTrue(ThresholdInput.parse(" -128 ", " -118 ", " -108 ", " -98 ").isOk());
    }

    @Test
    public void rejectsMalformedSign() {
        ThresholdInput.Result result = ThresholdInput.parse("-128", "+-118", "-108", "-98");

        assertFalse(result.isOk());
        assertEquals(Validation.Code.NOT_A_NUMBER, result.validation().code());
        assertEquals(1, result.validation().index());
    }

    @Test
    public void rejectsEmptyField() {
        ThresholdInput.Result result = ThresholdInput.parse("-128", "", "-108", "-98");

        assertFalse(result.isOk());
        assertEquals(Validation.Code.EMPTY, result.validation().code());
        assertEquals(1, result.validation().index());
    }

    @Test
    public void rejectsNonInteger() {
        assertEquals(Validation.Code.NOT_A_NUMBER,
                ThresholdInput.parse("-128", "-118.5", "-108", "-98").validation().code());
        assertEquals(Validation.Code.NOT_A_NUMBER,
                ThresholdInput.parse("-128", "abc", "-108", "-98").validation().code());
        assertEquals(Validation.Code.NOT_A_NUMBER,
                ThresholdInput.parse("-128", "--118", "-108", "-98").validation().code());
    }

    @Test
    public void rejectsIntegerOverflow() {
        ThresholdInput.Result result =
                ThresholdInput.parse("-128", "99999999999999999999", "-108", "-98");

        assertFalse(result.isOk());
        assertEquals(Validation.Code.NOT_A_NUMBER, result.validation().code());
    }

    @Test
    public void rejectsOutOfRangeValues() {
        assertEquals(Validation.Code.OUT_OF_RANGE,
                ThresholdInput.parse("-200", "-118", "-108", "-98").validation().code());
        assertEquals(Validation.Code.OUT_OF_RANGE,
                ThresholdInput.parse("-128", "-118", "-108", "-10").validation().code());
    }

    @Test
    public void rejectsNonIncreasingValues() {
        assertEquals(Validation.Code.NOT_STRICTLY_INCREASING,
                ThresholdInput.parse("-128", "-128", "-108", "-98").validation().code());
        assertEquals(Validation.Code.NOT_STRICTLY_INCREASING,
                ThresholdInput.parse("-98", "-108", "-118", "-128").validation().code());
    }

    @Test
    public void reportsWrongFieldCount() {
        assertEquals(Validation.Code.WRONG_COUNT,
                ThresholdInput.parse("-128", "-118", "-108").validation().code());
        assertEquals(Validation.Code.WRONG_COUNT,
                ThresholdInput.parse().validation().code());
    }
}
