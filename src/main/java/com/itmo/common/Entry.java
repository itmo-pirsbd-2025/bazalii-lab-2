package com.itmo.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class Entry<K extends Comparable<K>, V> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final K key;
    private final V value;

    public Entry(K key, V value) {
        this.key = Objects.requireNonNull(key, "key");
        this.value = Objects.requireNonNull(value, "value");
    }

    public K key() {
        return key;
    }

    public V value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entry<?, ?> entry = (Entry<?, ?>) o;
        return Objects.equals(key, entry.key) && Objects.equals(value, entry.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "Entry{" + "key=" + key + ", value=" + value + '}';
    }
}
