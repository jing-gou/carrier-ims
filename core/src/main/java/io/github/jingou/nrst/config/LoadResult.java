package io.github.jingou.nrst.config;

/**
 * Outcome of reading a stored threshold configuration.
 *
 * <p>Three states matter to the hook: a valid config, no config at all (first run), and a config
 * that exists but cannot be trusted. Only {@link Status#VALID} may ever reach CarrierConfig — the
 * hook must not inject unverified user data.
 */
public final class LoadResult {

    public enum Status {
        VALID,
        ABSENT,
        INVALID,
    }

    private final Status status;
    private final Thresholds thresholds;
    private final String message;

    private LoadResult(Status status, Thresholds thresholds, String message) {
        this.status = status;
        this.thresholds = thresholds;
        this.message = message;
    }

    public static LoadResult valid(Thresholds thresholds) {
        return new LoadResult(Status.VALID, thresholds, null);
    }

    public static LoadResult absent() {
        return new LoadResult(Status.ABSENT, null, "No saved configuration.");
    }

    public static LoadResult invalid(String message) {
        return new LoadResult(Status.INVALID, null, message);
    }

    public Status status() {
        return status;
    }

    /** The parsed values for {@link Status#VALID}, otherwise {@code null}. */
    public Thresholds thresholds() {
        return thresholds;
    }

    /** Explanation for {@link Status#ABSENT} and {@link Status#INVALID}. */
    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "LoadResult{" + status + (message == null ? "" : ": " + message) + "}";
    }
}
