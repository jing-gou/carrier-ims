package io.github.jingou.nrst.data;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal in-memory {@link SharedPreferences} for JVM tests. No Android runtime involved.
 *
 * <p>Deliberately mimics the two properties the production code relies on: reads see only
 * committed values, and the number of commits is observable.
 */
public final class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();

    private int commitCount;
    private boolean failCommits;

    public void failCommits(boolean fail) {
        this.failCommits = fail;
    }

    public int commitCount() {
        return commitCount;
    }

    public void putRaw(String key, Object value) {
        values.put(key, value);
    }

    public void removeRaw(String key) {
        values.remove(key);
    }

    @Override
    public Map<String, ?> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = values.get(key);
        if (value instanceof Set) {
            //noinspection unchecked
            return new HashSet<>((Set<String>) value);
        }
        return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        return value instanceof Float ? (Float) value : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private final class FakeEditor implements Editor {

        private final Map<String, Object> puts = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            puts.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            puts.put(key, values == null ? null : new HashSet<>(values));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            puts.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            puts.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            puts.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            puts.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            puts.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            puts.clear();
            removals.clear();
            return this;
        }

        @Override
        public boolean commit() {
            commitCount++;
            if (failCommits) {
                return false;
            }
            if (clear) {
                values.clear();
            }
            for (String key : removals) {
                values.remove(key);
            }
            for (Map.Entry<String, Object> entry : puts.entrySet()) {
                if (entry.getValue() == null) {
                    values.remove(entry.getKey());
                } else {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
            for (OnSharedPreferenceChangeListener listener : new ArrayList<>(listeners)) {
                listener.onSharedPreferenceChanged(FakeSharedPreferences.this, null);
            }
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
