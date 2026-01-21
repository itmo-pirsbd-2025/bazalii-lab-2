package com.itmo.inMemory;

import com.itmo.common.Entry;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-memory B-Tree implementation.
 * Can be stored on disk using FullBTreeStore
 */
public final class InMemoryBTree<K extends Comparable<K>, V> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Node<K, V> root;
    private final int degree;
    private int height;

    public InMemoryBTree(int degree) {
        if (degree < 2) {
            throw new IllegalArgumentException("BTree degree must be at least 2");
        }

        this.degree = degree;
        this.root = new Node<>(degree);
        this.height = 1;
    }

    public InMemoryBTree(int degree, List<Entry<K, V>> inputData) {
        this(degree);
        for (Entry<K, V> e : inputData) {
            insert(e.key(), e.value());
        }
    }

    public int getDegree() {
        return degree;
    }

    public int getHeight() {
        return height;
    }

    public Entry<K, V> find(K key) {
        Objects.requireNonNull(key, "key");

        return searchInternal(root, key);
    }

    public void insert(K newKey, V newValue) {
        Objects.requireNonNull(newKey, "newKey");
        Objects.requireNonNull(newValue, "newValue");

        if (!root.hasReachedMaxEntries()) {
            insertToNodeWithEmptySpace(root, newKey, newValue);
            return;
        }

        Node<K, V> oldRoot = root;
        root = new Node<>(degree);
        root.children().add(oldRoot);

        splitChild(root, 0, oldRoot);
        insertToNodeWithEmptySpace(root, newKey, newValue);
        height++;
    }

    public void delete(K keyToDelete) {
        Objects.requireNonNull(keyToDelete, "keyToDelete");
        deleteInternal(root, keyToDelete);

        if (root.entries().isEmpty() && !root.isLeaf()) {
            root = root.children().getFirst();
            height--;
        }
    }

    private void deleteInternal(Node<K, V> node, K keyToDelete) {
        int i = 0;
        while (i < node.entries().size() && keyToDelete.compareTo(node.entries().get(i).key()) > 0) {
            i++;
        }

        if (i < node.entries().size() && node.entries().get(i).key().compareTo(keyToDelete) == 0) {
            deleteKeyFromNode(node, keyToDelete, i);
            return;
        }

        if (!node.isLeaf()) {
            deleteKeyFromSubtree(node, keyToDelete, i);
        }
    }

    private void deleteKeyFromSubtree(Node<K, V> parentNode, K keyToDelete, int subtreeIndexInNode) {
        Node<K, V> childNode = parentNode.children().get(subtreeIndexInNode);

        if (childNode.hasReachedMinEntries()) {
            int leftIndex = subtreeIndexInNode - 1;
            Node<K, V> leftSibling = subtreeIndexInNode > 0 ? parentNode.children().get(leftIndex) : null;

            int rightIndex = subtreeIndexInNode + 1;
            Node<K, V> rightSibling = subtreeIndexInNode < parentNode.children().size() - 1
                    ? parentNode.children().get(rightIndex)
                    : null;

            if (leftSibling != null && leftSibling.entries().size() > degree - 1) {
                // rotate from left sibling (separator key is parentNode.entries().get(leftIndex))
                childNode.entries().addFirst(parentNode.entries().get(leftIndex));
                parentNode.entries().set(leftIndex, leftSibling.entries().removeLast());

                if (!leftSibling.isLeaf()) {
                    childNode.children().addFirst(leftSibling.children().removeLast());
                }
            } else if (rightSibling != null && rightSibling.entries().size() > degree - 1) {
                // rotate from right sibling
                childNode.entries().add(parentNode.entries().get(subtreeIndexInNode));
                parentNode.entries().set(subtreeIndexInNode, rightSibling.entries().removeFirst());

                if (!rightSibling.isLeaf()) {
                    childNode.children().add(rightSibling.children().removeFirst());
                }
            } else {
                // merge with a sibling
                if (leftSibling != null) {
                    // merge with left sibling; separator key is parentNode.entries().get(leftIndex)
                    childNode.entries().addFirst(parentNode.entries().get(leftIndex));

                    List<Entry<K, V>> oldEntries = new ArrayList<>(childNode.entries());
                    childNode.entries().clear();
                    childNode.entries().addAll(leftSibling.entries());
                    childNode.entries().addAll(oldEntries);

                    if (!leftSibling.isLeaf()) {
                        List<Node<K, V>> oldChildren = new ArrayList<>(childNode.children());
                        childNode.children().clear();
                        childNode.children().addAll(leftSibling.children());
                        childNode.children().addAll(oldChildren);
                    }

                    parentNode.children().remove(leftIndex);
                    parentNode.entries().remove(leftIndex);
                } else {
                    if (rightSibling == null) {
                        throw new IllegalStateException("Node should have at least one sibling");
                    }
                    childNode.entries().add(parentNode.entries().get(subtreeIndexInNode));
                    childNode.entries().addAll(rightSibling.entries());
                    if (!rightSibling.isLeaf()) {
                        childNode.children().addAll(rightSibling.children());
                    }

                    parentNode.children().remove(rightIndex);
                    parentNode.entries().remove(subtreeIndexInNode);
                }
            }
        }

        deleteInternal(childNode, keyToDelete);
    }

    private void deleteKeyFromNode(Node<K, V> node, K keyToDelete, int keyIndexInNode) {
        if (node.isLeaf()) {
            node.entries().remove(keyIndexInNode);
            return;
        }

        Node<K, V> predecessorChild = node.children().get(keyIndexInNode);
        if (predecessorChild.entries().size() >= degree) {
            Entry<K, V> predecessor = deletePredecessor(predecessorChild);
            node.entries().set(keyIndexInNode, predecessor);
        } else {
            Node<K, V> successorChild = node.children().get(keyIndexInNode + 1);
            if (successorChild.entries().size() >= degree) {
                Entry<K, V> successor = deleteSuccessor(successorChild);
                node.entries().set(keyIndexInNode, successor);
            } else {
                predecessorChild.entries().add(node.entries().get(keyIndexInNode));
                predecessorChild.entries().addAll(successorChild.entries());
                predecessorChild.children().addAll(successorChild.children());

                node.entries().remove(keyIndexInNode);
                node.children().remove(keyIndexInNode + 1);

                deleteInternal(predecessorChild, keyToDelete);
            }
        }
    }

    private Entry<K, V> deletePredecessor(Node<K, V> node) {
        if (node.isLeaf()) {
            return node.entries().removeLast();
        }

        return deletePredecessor(node.children().getLast());
    }

    private Entry<K, V> deleteSuccessor(Node<K, V> node) {
        if (node.isLeaf()) {
            return node.entries().removeFirst();
        }

        return deleteSuccessor(node.children().getFirst());
    }

    private Entry<K, V> searchInternal(Node<K, V> node, K key) {
        int i = 0;
        while (i < node.entries().size() && key.compareTo(node.entries().get(i).key()) > 0) {
            i++;
        }

        if (i < node.entries().size() && node.entries().get(i).key().compareTo(key) == 0) {
            return node.entries().get(i);
        }

        if (node.isLeaf()) {
            return null;
        }

        return searchInternal(node.children().get(i), key);
    }

    private void splitChild(Node<K, V> parentNode, int nodeToBeSplitIndex, Node<K, V> nodeToBeSplit) {
        Node<K, V> newNode = new Node<>(degree);

        parentNode.entries().add(nodeToBeSplitIndex, nodeToBeSplit.entries().get(degree - 1));
        parentNode.children().add(nodeToBeSplitIndex + 1, newNode);

        // move last (degree-1) keys into new node
        newNode.entries().addAll(nodeToBeSplit.entries().subList(degree, 2 * degree - 1));

        // remove keys: [degree-1, 2*degree-2]
        nodeToBeSplit.entries().subList(degree - 1, 2 * degree - 1).clear();

        if (!nodeToBeSplit.isLeaf()) {
            newNode.children().addAll(nodeToBeSplit.children().subList(degree, 2 * degree));
            nodeToBeSplit.children().subList(degree, 2 * degree).clear();
        }
    }

    private void insertToNodeWithEmptySpace(Node<K, V> node, K newKey, V newValue) {
        int positionToInsert = 0;
        while (positionToInsert < node.entries().size()
                && newKey.compareTo(node.entries().get(positionToInsert).key()) >= 0) {
            positionToInsert++;
        }

        if (node.isLeaf()) {
            node.entries().add(positionToInsert, new Entry<>(newKey, newValue));
            return;
        }

        Node<K, V> child = node.children().get(positionToInsert);
        if (child.hasReachedMaxEntries()) {
            splitChild(node, positionToInsert, child);
            if (newKey.compareTo(node.entries().get(positionToInsert).key()) > 0) {
                positionToInsert++;
            }
        }

        insertToNodeWithEmptySpace(node.children().get(positionToInsert), newKey, newValue);
    }
}
