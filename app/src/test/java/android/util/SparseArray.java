package android.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard test implementation of android.util.SparseArray for JVM unit tests,
 * allowing Media3 CachedContentIndex to function seamlessly without android.jar stubs.
 */
public class SparseArray<E> implements Cloneable {
    private final Map<Integer, E> map = new HashMap<>();

    public SparseArray() {}

    public SparseArray(int initialCapacity) {}

    public int size() {
        return map.size();
    }

    public E get(int key) {
        return get(key, null);
    }

    public E get(int key, E valueIfKeyNotFound) {
        E val = map.get(key);
        return val != null ? val : valueIfKeyNotFound;
    }

    public void put(int key, E value) {
        map.put(key, value);
    }

    public void append(int key, E value) {
        put(key, value);
    }

    public void delete(int key) {
        map.remove(key);
    }

    public void remove(int key) {
        delete(key);
    }

    public void removeAt(int index) {
        delete(keyAt(index));
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

    public E valueAt(int index) {
        return get(keyAt(index));
    }

    public void setValueAt(int index, E value) {
        put(keyAt(index), value);
    }

    public int indexOfKey(int key) {
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        int res = Collections.binarySearch(keys, key);
        return res >= 0 ? res : -1;
    }

    public int indexOfValue(E value) {
        for (int i = 0; i < size(); i++) {
            if (valueAt(i) == value) return i;
        }
        return -1;
    }
}
