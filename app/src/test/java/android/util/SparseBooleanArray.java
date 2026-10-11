package android.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard test implementation of android.util.SparseBooleanArray for JVM unit tests,
 * allowing Media3 CachedContentIndex to track removed IDs without android.jar stubs.
 */
public class SparseBooleanArray implements Cloneable {
    private final Map<Integer, Boolean> map = new HashMap<>();

    public SparseBooleanArray() {}

    public SparseBooleanArray(int initialCapacity) {}

    public int size() {
        return map.size();
    }

    public boolean get(int key) {
        return get(key, false);
    }

    public boolean get(int key, boolean valueIfKeyNotFound) {
        Boolean val = map.get(key);
        return val != null ? val : valueIfKeyNotFound;
    }

    public void put(int key, boolean value) {
        map.put(key, value);
    }

    public void append(int key, boolean value) {
        put(key, value);
    }

    public void delete(int key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int keyAt(int index) {
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        if (index < 0 || index >= keys.size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return keys.get(index);
    }

    public boolean valueAt(int index) {
        return get(keyAt(index));
    }

    public void setValueAt(int index, boolean value) {
        put(keyAt(index), value);
    }

    public int indexOfKey(int key) {
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        int res = Collections.binarySearch(keys, key);
        return res >= 0 ? res : -1;
    }

    public int indexOfValue(boolean value) {
        for (int i = 0; i < size(); i++) {
            if (valueAt(i) == value) return i;
        }
        return -1;
    }
}
