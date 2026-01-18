package com.itmo;

import com.itmo.disk.DiskBTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class DiskBTreeTests {

    @Test
    void DiskBTreeInsert_InsertElements_ElementsArePresentInTree() throws Exception {
        // Arrange
        var file = Files.createTempFile("disk-btree-", ".temp");

        try (var tree = DiskBTree.createNew(file, 3, 256)) {

            // Act
            for (var i = 0; i < 10_000; i++) {
                tree.insert(i, i * 2);
            }
            tree.flush();

            // Assert
            assertEquals(200, tree.find(100).value());
            assertNull(tree.find(-1));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void DiskBTreeDelete_DeleteElement_ElementIsDeleted() throws Exception {
        // Arrange
        var file = Files.createTempFile("disk-btree-", ".temp");

        try (var tree = DiskBTree.createNew(file, 3, 256)) {
            for (var i = 0; i < 1_000; i++) {
                tree.insert(i, i);
            }

            // Act
            tree.delete(500);
            tree.flush();

            // Assert
            assertNull(tree.find(500));
            assertEquals(499, tree.find(499).value());
            assertEquals(501, tree.find(501).value());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void canCloseAndReopen() throws Exception {
        // Arrange
        var file = Files.createTempFile("disk-btree-", ".temp");

        try {
            // Act
            try (var tree = DiskBTree.createNew(file, 25, 512)) {
                for (var i = 0; i < 50_000; i++) {
                    tree.insert(i, i);
                }
                tree.flush();
            }

            // Assert
            try (var reopened = DiskBTree.open(file, 512)) {
                assertEquals(10_000, reopened.find(10_000).value());
                assertNull(reopened.find(100_000_000));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
