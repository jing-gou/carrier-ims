package io.github.nku100.carrierims.config;

/**
 * Parses the four GUI text fields into {@link Thresholds}.
 *
 * <p>Shared by the GUI (immediate feedback) and by anything else that consumes text input, so the
 * rules for empty input, signs, overflow and range cannot drift apart.
 */
public final class ThresholdInput {

    private ThresholdInput() {
    }

    public static final class Result {
        private final Thresholds thresholds;
        private final Validation validation;

        private Result(Thresholds thresholds, Validation validation) {
            this.thresholds = thresholds;
            this.validation = validation;
        }

        static Result ok(Thresholds thresholds) {
            return new Result(thresholds, Validation.ok());
        }

        static Result error(Validation validation) {
            return new Result(null, validation);
        }

        public boolean isOk() {
            return thresholds != null;
        }

        /** The parsed values, or {@code null} when invalid. */
        public Thresholds thresholds() {
            return thresholds;
        }

        public Validation validation() {
            return validation;
        }
    }

    public static Result parse(String... fields) {
        if (fields == null || fields.length != Thresholds.COUNT) {
            return Result.error(Validation.error(
                    Validation.Code.WRONG_COUNT,
                    "Exactly " + Thresholds.COUNT + " thresholds are required.",
                    -1));
        }
        int[] values = new int[Thresholds.COUNT];
        for (int i = 0; i < Thresholds.COUNT; i++) {
            String raw = fields[i] == null ? "" : fields[i].trim();
            if (raw.isEmpty()) {
                return Result.error(Validation.error(
                        Validation.Code.EMPTY,
                        "Threshold " + (i + 1) + " is empty.",
                        i));
            }
            try {
                values[i] = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                // Covers letters, decimals, and values outside the int range.
                return Result.error(Validation.error(
                        Validation.Code.NOT_A_NUMBER,
                        "Threshold " + (i + 1) + " must be a whole number.",
                        i));
            }
        }
        Validation validation = Thresholds.validate(values);
        if (!validation.isOk()) {
            return Result.error(validation);
        }
        return Result.ok(Thresholds.of(values));
    }
}
