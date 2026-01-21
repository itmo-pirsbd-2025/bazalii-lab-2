package com.itmo;

import com.itmo.inMemory.ArraysCreator;
import com.itmo.inMemory.FullBTreeStore;
import com.itmo.inMemory.InMemoryBTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class BTreeTests {

    @Test
    void InMemoryBTreeInsert_InsertElements_ElementsArePresentInTree() {
        // Arrange
        var tree = new InMemoryBTree<Integer, Integer>(3);

        // Act
        for (var i = 0; i < 10_000; i++) {
            tree.insert(i, i * 2);
        }

        // Assert
        assertEquals(200, tree.find(100).value());
        assertNull(tree.find(-1));
    }

    @Test
    void InMemoryBTreeDelete_DeleteElement_ElementIsDeleted() {
        // Arrange
        var tree = new InMemoryBTree<Integer, Integer>(3);
        for (var i = 0; i < 1_000; i++) {
            tree.insert(i, i);
        }

        assertNotNull(tree.find(499), "Key 499 disappeared after deleting 500");
        assertNotNull(tree.find(501), "Key 501 disappeared after deleting 500");

        // Act
        tree.delete(500);

        // Assert
        assertNull(tree.find(500));
        assertNotNull(tree.find(499), "Key 499 disappeared after deleting 500");
        assertNotNull(tree.find(501), "Key 501 disappeared after deleting 500");
        assertEquals(499, tree.find(499).value());
        assertEquals(501, tree.find(501).value());
    }

    @Test
    void FullBTreeStoreSaveAndLoad_SaveAndLoadBtree_BtreeIsSavedAndLoadedCorrectly() throws Exception {
        // Arrange
        var data = ArraysCreator.createRandomEntries(50_000, 1L);
        var tree = new InMemoryBTree<Integer, Integer>(25, data);

        var file = Files.createTempFile("btree-test-", ".temp");

        // Act
        FullBTreeStore.save(file, tree);
        InMemoryBTree<Integer, Integer> loaded = FullBTreeStore.load(file);

        int key = data.get(10_000).key();
        assertEquals(tree.find(key), loaded.find(key));

        Files.deleteIfExists(file);
    }
}
