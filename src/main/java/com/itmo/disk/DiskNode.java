package com.itmo.disk;

final class DiskNode {
    final int degree;
    final int maxKeys;
    final int maxChildren;

    final int pageId;

    boolean isLeaf;
    int keyCount;

    final int[] keys;
    final int[] values;
    final int[] children;

    boolean dirty;

    DiskNode(int pageId, int degree) {
        this.pageId = pageId;
        this.degree = degree;
        this.maxKeys = 2 * degree - 1;
        this.maxChildren = 2 * degree;

        this.keys = new int[maxKeys];
        this.values = new int[maxKeys];
        this.children = new int[maxChildren];

        for (var i = 0; i < maxChildren; i++) {
            children[i] = -1;
        }

        this.isLeaf = true;
        this.keyCount = 0;
        this.dirty = false;
    }

    void markDirty() {
        this.dirty = true;
    }

    int findKeyIndex(int key) {
        int lo = 0, hi = keyCount;
        while (lo < hi) {
            var mid = (lo + hi) >>> 1;
            var mk = keys[mid];
            if (mk < key) {
                lo = mid + 1;
            } else hi = mid;
        }

        return lo;
    }

    int findChildIndex(int key) {
        return findKeyIndex(key);
    }
}
