package com.itmo.inMemory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FullBTreeStore {
    private FullBTreeStore() {}

    public static <K extends Comparable<K>, V> void save(Path file, InMemoryBTree<K, V> tree) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeObject(tree);
        }
    }

    public static <K extends Comparable<K>, V> InMemoryBTree<K, V> load(Path file) throws IOException {
        try (var in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            try {
                //noinspection unchecked
                return (InMemoryBTree<K, V>) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Failed to deserialize BTree", e);
            }
        }
    }
}
