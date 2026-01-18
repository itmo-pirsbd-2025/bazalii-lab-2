package com.itmo.inMemory;

import com.itmo.common.Entry;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class Node<K extends Comparable<K>, V> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Each node (except root) must have at least (t-1) keys.
     * Each node can contain at most (2t-1) keys.
     */
    private final int degree;

    private final List<Entry<K, V>> entries;
    private final List<Node<K, V>> children;

    Node(int degree) {
        this.degree = degree;
        this.entries = new ArrayList<>(2 * degree - 1);
        this.children = new ArrayList<>(2 * degree);
    }

    int getDegree() {
        return degree;
    }

    List<Entry<K, V>> entries() {
        return entries;
    }

    List<Node<K, V>> children() {
        return children;
    }

    boolean isLeaf() {
        return children.isEmpty();
    }

    boolean hasReachedMaxEntries() {
        return entries.size() == 2 * degree - 1;
    }

    boolean hasReachedMinEntries() {
        return entries.size() == degree - 1;
    }
}
