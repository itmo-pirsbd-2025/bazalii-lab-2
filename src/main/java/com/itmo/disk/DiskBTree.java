package com.itmo.disk;

import com.itmo.common.Entry;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Disk-backed B-Tree for int, int pairs.
 * All nodes are stored in a single file as fixed-size pages.
 */
public final class DiskBTree implements Closeable {

    private final int degree;                 // t
    private final int maxKeys;                // 2t-1
    private final int minKeys;                // t-1
    private final int maxChildren;            // 2t

    private final DiskPageManager pageManager;

    public static DiskBTree createNew(Path file, int degree, int cachePages) throws IOException {
        return new DiskBTree(DiskPageManager.createNew(file, degree, cachePages));
    }

    public static DiskBTree open(Path file, int cachePages) throws IOException {
        return new DiskBTree(DiskPageManager.open(file, cachePages));
    }

    private DiskBTree(DiskPageManager pageManager) throws IOException {
        this.pageManager = pageManager;
        this.degree = pageManager.getDegree();
        this.maxKeys = 2 * degree - 1;
        this.minKeys = degree - 1;
        this.maxChildren = 2 * degree;

        // Ensure root exists
        if (pageManager.getRootPageId() < 0) {
            var root = pageManager.allocateNodePage();
            var n = pageManager.get(root);
            n.isLeaf = true;
            n.keyCount = 0;
            n.markDirty();
            pageManager.setRootPageId(root);
            pageManager.flush();
        }
    }

    public int degree() {
        return degree;
    }

    public Entry<Integer, Integer> find(int key) throws IOException {
        var pageId = pageManager.getRootPageId();
        while (pageId >= 0) {
            var n = pageManager.get(pageId);
            var i = n.findKeyIndex(key);
            if (i < n.keyCount && n.keys[i] == key) {
                return new Entry<>(key, n.values[i]);
            }
            if (n.isLeaf) {
                return null;
            }
            pageId = n.children[i];
        }
        return null;
    }

    public void insert(int key, int value) throws IOException {
        var rootId = pageManager.getRootPageId();
        var root = pageManager.get(rootId);

        if (root.keyCount == maxKeys) {
            var allocatedNodePageId = pageManager.allocateNodePage();
            var node = pageManager.get(allocatedNodePageId);
            node.isLeaf = false;
            node.keyCount = 0;
            node.children[0] = rootId;
            node.markDirty();

            pageManager.setRootPageId(allocatedNodePageId);

            splitChild(allocatedNodePageId, 0);           // splits old root
            insertNonFull(allocatedNodePageId, key, value);
        } else {
            insertNonFull(rootId, key, value);
        }
    }

    private void insertNonFull(int nodeId, int key, int value) throws IOException {
        var node = pageManager.get(nodeId);

        var i = node.keyCount - 1;
        if (node.isLeaf) {
            // If key exists -> overwrite value
            var pos = node.findKeyIndex(key);
            if (pos < node.keyCount && node.keys[pos] == key) {
                node.values[pos] = value;
                node.markDirty();
                return;
            }

            // shift right
            while (i >= 0 && key < node.keys[i]) {
                node.keys[i + 1] = node.keys[i];
                node.values[i + 1] = node.values[i];
                i--;
            }
            node.keys[i + 1] = key;
            node.values[i + 1] = value;
            node.keyCount++;
            node.markDirty();
            return;
        }

        var childIndex = node.findChildIndex(key);
        var childId = node.children[childIndex];
        var child = pageManager.get(childId);

        if (child.keyCount == maxKeys) {
            splitChild(nodeId, childIndex);

            var newNode = pageManager.get(nodeId);
            if (key > newNode.keys[childIndex]) {
                childIndex++;
            }
        }

        insertNonFull(pageManager.get(nodeId).children[childIndex], key, value);
    }

    private void splitChild(int parentId, int i) throws IOException {
        var node = pageManager.get(parentId);
        var firstNodeId = node.children[i];
        var firstNode = pageManager.get(firstNodeId);

        var secondNodeId = pageManager.allocateNodePage();
        var secondNode = pageManager.get(secondNodeId);
        secondNode.isLeaf = firstNode.isLeaf;
        secondNode.keyCount = degree - 1;

        // Copy last t-1 keys from firstNode to secondNode
        for (var j = 0; j < degree - 1; j++) {
            secondNode.keys[j] = firstNode.keys[j + degree];
            secondNode.values[j] = firstNode.values[j + degree];
        }

        if (!firstNode.isLeaf) {
            if (degree >= 0) {
                System.arraycopy(firstNode.children, degree, secondNode.children, 0, degree);
            }
        }

        // Reduce firstNode
        firstNode.keyCount = degree - 1;

        // Shift children in parent to make room for secondNode
        for (var j = node.keyCount; j >= i + 1; j--) {
            node.children[j + 1] = node.children[j];
        }
        node.children[i + 1] = secondNodeId;

        // Shift keys in parent to make room for median
        for (var j = node.keyCount - 1; j >= i; j--) {
            node.keys[j + 1] = node.keys[j];
            node.values[j + 1] = node.values[j];
        }

        // Move median key/value up to parent
        node.keys[i] = firstNode.keys[degree - 1];
        node.values[i] = firstNode.values[degree - 1];
        node.keyCount++;

        node.markDirty();
        firstNode.markDirty();
        secondNode.markDirty();
    }

    public void delete(int key) throws IOException {
        var rootId = pageManager.getRootPageId();
        deleteInternal(rootId, key);

        // If root has 0 keys and is internal -> shrink height
        var root = pageManager.get(pageManager.getRootPageId());
        if (!root.isLeaf && root.keyCount == 0) {
            var newRoot = root.children[0];
            pageManager.setRootPageId(newRoot);
        }
    }

    private void deleteInternal(int nodeId, int key) throws IOException {
        var x = pageManager.get(nodeId);
        var idx = x.findKeyIndex(key);

        // Case 1: key in this node
        if (idx < x.keyCount && x.keys[idx] == key) {
            if (x.isLeaf) {
                // Remove from leaf
                removeKeyAt(x, idx);
                x.markDirty();
                return;
            }

            // Internal node: replace by predecessor or successor
            var leftChildId = x.children[idx];
            var rightChildId = x.children[idx + 1];
            var left = pageManager.get(leftChildId);
            var right = pageManager.get(rightChildId);

            if (left.keyCount > minKeys) {
                // predecessor
                var pred = maxInSubtree(leftChildId);
                x.keys[idx] = pred.key;
                x.values[idx] = pred.value;
                x.markDirty();
                deleteInternal(leftChildId, pred.key);
                return;
            } else if (right.keyCount > minKeys) {
                // successor
                var succ = minInSubtree(rightChildId);
                x.keys[idx] = succ.key;
                x.values[idx] = succ.value;
                x.markDirty();
                deleteInternal(rightChildId, succ.key);
                return;
            } else {
                // merge key + right into left, then delete from merged
                mergeChildren(nodeId, idx);
                deleteInternal(leftChildId, key);
                return;
            }
        }

        // Case 2: key not in this node
        if (x.isLeaf) {
            return; // not found
        }

        var childIndex = x.findChildIndex(key);
        var childId = x.children[childIndex];
        var child = pageManager.get(childId);

        // Ensure child has at least t keys before descending
        if (child.keyCount == minKeys) {
            var leftSiblingId = (childIndex > 0) ? x.children[childIndex - 1] : -1;
            var rightSiblingId = (childIndex < x.keyCount) ? x.children[childIndex + 1] : -1;

            if (leftSiblingId != -1 && pageManager.get(leftSiblingId).keyCount > minKeys) {
                borrowFromLeft(nodeId, childIndex);
            } else if (rightSiblingId != -1 && pageManager.get(rightSiblingId).keyCount > minKeys) {
                borrowFromRight(nodeId, childIndex);
            } else {
                // merge with a sibling
                if (rightSiblingId != -1) {
                    mergeChildren(nodeId, childIndex);
                    // childId stays the same
                } else {
                    // merge into left sibling
                    mergeChildren(nodeId, childIndex - 1);
                    childId = x.children[childIndex - 1];
                }
            }
        }

        deleteInternal(childId, key);
    }

    private void removeKeyAt(DiskNode leaf, int idx) {
        for (var i = idx; i < leaf.keyCount - 1; i++) {
            leaf.keys[i] = leaf.keys[i + 1];
            leaf.values[i] = leaf.values[i + 1];
        }
        leaf.keyCount--;
    }

    private IntPair maxInSubtree(int nodeId) throws IOException {
        var id = nodeId;
        while (true) {
            var n = pageManager.get(id);
            if (n.isLeaf) {
                var i = n.keyCount - 1;
                return new IntPair(n.keys[i], n.values[i]);
            }
            id = n.children[n.keyCount];
        }
    }

    private IntPair minInSubtree(int nodeId) throws IOException {
        var id = nodeId;
        while (true) {
            var n = pageManager.get(id);
            if (n.isLeaf) {
                return new IntPair(n.keys[0], n.values[0]);
            }
            id = n.children[0];
        }
    }

    /**
     * Merge child[index] + separator key + child[index+1] into child[index].
     */
    private void mergeChildren(int parentId, int index) throws IOException {
        var p = pageManager.get(parentId);
        var leftId = p.children[index];
        var rightId = p.children[index + 1];

        var left = pageManager.get(leftId);
        var right = pageManager.get(rightId);

        // left gets separator key
        left.keys[left.keyCount] = p.keys[index];
        left.values[left.keyCount] = p.values[index];
        left.keyCount++;

        // copy right keys
        for (var j = 0; j < right.keyCount; j++) {
            left.keys[left.keyCount] = right.keys[j];
            left.values[left.keyCount] = right.values[j];
            left.keyCount++;
        }

        // copy right children if needed
        if (!left.isLeaf) {
            for (var j = 0; j <= right.keyCount; j++) {
                left.children[(degree) + j] = right.children[j]; // safe because left had minKeys and right had minKeys
            }
        }

        // remove key from parent + shift
        for (var j = index; j < p.keyCount - 1; j++) {
            p.keys[j] = p.keys[j + 1];
            p.values[j] = p.values[j + 1];
        }
        for (var j = index + 1; j < p.keyCount; j++) {
            p.children[j] = p.children[j + 1];
        }
        p.keyCount--;

        left.markDirty();
        p.markDirty();

        pageManager.freeNodePage(rightId);
    }

    private void borrowFromLeft(int parentId, int childIndex) throws IOException {
        var p = pageManager.get(parentId);
        var childId = p.children[childIndex];
        var leftId = p.children[childIndex - 1];

        var child = pageManager.get(childId);
        var left = pageManager.get(leftId);

        // shift child keys right by 1
        for (var i = child.keyCount - 1; i >= 0; i--) {
            child.keys[i + 1] = child.keys[i];
            child.values[i + 1] = child.values[i];
        }
        if (!child.isLeaf) {
            for (var i = child.keyCount; i >= 0; i--) {
                child.children[i + 1] = child.children[i];
            }
        }

        // bring separator from parent down to child[0]
        child.keys[0] = p.keys[childIndex - 1];
        child.values[0] = p.values[childIndex - 1];

        // move left's last child if needed
        if (!child.isLeaf) {
            child.children[0] = left.children[left.keyCount];
        }

        // move left's last key up to parent
        p.keys[childIndex - 1] = left.keys[left.keyCount - 1];
        p.values[childIndex - 1] = left.values[left.keyCount - 1];

        left.keyCount--;
        child.keyCount++;

        left.markDirty();
        child.markDirty();
        p.markDirty();
    }

    private void borrowFromRight(int parentId, int childIndex) throws IOException {
        var p = pageManager.get(parentId);
        var childId = p.children[childIndex];
        var rightId = p.children[childIndex + 1];

        var child = pageManager.get(childId);
        var right = pageManager.get(rightId);

        // bring separator from parent down to child end
        child.keys[child.keyCount] = p.keys[childIndex];
        child.values[child.keyCount] = p.values[childIndex];

        if (!child.isLeaf) {
            child.children[child.keyCount + 1] = right.children[0];
        }

        // move right first key up to parent
        p.keys[childIndex] = right.keys[0];
        p.values[childIndex] = right.values[0];

        // shift right keys/children left
        for (var i = 0; i < right.keyCount - 1; i++) {
            right.keys[i] = right.keys[i + 1];
            right.values[i] = right.values[i + 1];
        }
        if (!right.isLeaf) {
            for (var i = 0; i < right.keyCount; i++) {
                right.children[i] = right.children[i + 1];
            }
        }

        right.keyCount--;
        child.keyCount++;

        right.markDirty();
        child.markDirty();
        p.markDirty();
    }

    @Override
    public void close() throws IOException {
        pageManager.close();
    }

    public void flush() throws IOException {
        pageManager.flush();
    }

    private record IntPair(int key, int value) {
    }
}