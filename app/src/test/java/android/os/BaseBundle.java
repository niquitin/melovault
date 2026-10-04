package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BaseBundle {
    protected final Map<String, Object> mMap = new HashMap<>();

    public BaseBundle() {}

    public void putString(String key, String value) {
        mMap.put(key, value);
    }

    public void putLong(String key, long value) {
        mMap.put(key, value);
    }

    public void putInt(String key, int value) {
        mMap.put(key, value);
    }

    public void putDouble(String key, double value) {
        mMap.put(key, value);
    }

    public void putBoolean(String key, boolean value) {
        mMap.put(key, value);
    }

    public String getString(String key) {
        Object val = mMap.get(key);
        return val != null ? val.toString() : null;
    }

    public String getString(String key, String defaultValue) {
        Object val = mMap.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        Object val = mMap.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return defaultValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        Object val = mMap.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        Object val = mMap.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultValue;
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = mMap.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return defaultValue;
    }

    public boolean containsKey(String key) {
        return mMap.containsKey(key);
    }

    public boolean isEmpty() {
        return mMap.isEmpty();
    }

    public int size() {
        return mMap.size();
    }

    public Set<String> keySet() {
        return mMap.keySet();
    }

    public void clear() {
        mMap.clear();
    }
}
