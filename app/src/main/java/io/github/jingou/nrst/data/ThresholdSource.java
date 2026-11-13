package io.github.jingou.nrst.data;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import io.github.jingou.nrst.config.LoadResult;
import io.github.jingou.nrst.config.Thresholds;

/**
 * Hook-side view of the stored configuration.
 *
 * <p>Carrier config reads are one of the hottest paths in the system, so the parsed value is cached
 * in a volatile field and the preferences are re-read only when:
 *
 * <ul>
 *   <li>the framework reports a preference change (LSPosed pushes remote preference updates), or</li>
 *   <li>{@link #REFRESH_INTERVAL_MS} has elapsed, as a safety net for frameworks that do not push.</li>
 * </ul>
 *
 * <p>An invalid record is never cached as usable data: it is dropped, logged once, and the hook
 * leaves the framework's own value in place.
 */
public final class ThresholdSource {

    private static final String TAG = "NrThresholds";
    private static final long REFRESH_INTERVAL_MS = 3_000L;

    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    private volatile Thresholds current;
    private volatile long lastRefresh;
    private volatile String lastInvalidReason;
    private volatile boolean invalidLogged;

    public ThresholdSource(SharedPreferences prefs) {
        this.prefs = prefs;
        this.listener = (sharedPreferences, key) -> refresh();
        this.lastRefresh = 0L;
        refresh();
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener);
        } catch (Throwable t) {
            Log.w(TAG, "cannot observe remote preference changes; falling back to polling", t);
        }
    }

    /** The last valid configuration, or {@code null} when none is stored or the record is bad. */
    public Thresholds get() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefresh >= REFRESH_INTERVAL_MS) {
            refresh();
        }
        return current;
    }

    /** Last validation problem, for diagnostics. {@code null} when the record is valid. */
    public String lastInvalidReason() {
        return lastInvalidReason;
    }

    private synchronized void refresh() {
        lastRefresh = SystemClock.elapsedRealtime();
        LoadResult result;
        try {
            result = new ThresholdStore(prefs).load();
        } catch (Throwable t) {
            result = LoadResult.invalid("Read failed: " + t);
        }

        switch (result.status()) {
            case VALID:
                current = result.thresholds();
                lastInvalidReason = null;
                invalidLogged = false;
                break;
            case ABSENT:
                current = null;
                lastInvalidReason = null;
                invalidLogged = false;
                break;
            case INVALID:
            default:
                current = null;
                lastInvalidReason = result.message();
                if (!invalidLogged) {
                    invalidLogged = true;
                    Log.w(TAG, "stored configuration ignored: " + result.message());
                }
                break;
        }
    }
}
