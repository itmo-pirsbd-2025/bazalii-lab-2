package com.itmo;

import com.itmo.common.Entry;
import com.itmo.disk.DiskBTree;
import com.itmo.inMemory.ArraysCreator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class DiskBTreeBenchmarks {

    private static final int TREE_DEGREE = 25;
    private static final int NUMBER_OF_KEYS = 50;

    @Param({"10000", "100000"})
    public int size;

    private List<Entry<Integer, Integer>> entries;
    private List<Entry<Integer, Integer>> entriesToInsert;
    private int[] keys;

    private Path fileSearch;
    private DiskBTree treeForSearch;


    private Path fileInsert;
    private DiskBTree treeForInsert;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        entries = ArraysCreator.createRandomEntries(size, 12345L);
        keys = ArraysCreator.createRandomKeys(entries, NUMBER_OF_KEYS, 54321L);

        fileSearch = Files.createTempFile("disk-btree-search-", ".temp");
        treeForSearch = DiskBTree.createNew(fileSearch, TREE_DEGREE, 256);
        for (var e : entries) {
            treeForSearch.insert(e.key(), e.value());
        }
        treeForSearch.flush();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        entriesToInsert = ArraysCreator.createRandomEntries(10_000, 99999L);

        if (treeForInsert != null) {
            try {
                treeForInsert.close();
            } catch (Exception ignored) {
            }
        }
        if (fileInsert != null) {
            try {
                Files.deleteIfExists(fileInsert);
            } catch (Exception ignored) {
            }
        }

        try {
            fileInsert = Files.createTempFile("disk-btree-insert-", ".temp");
            treeForInsert = DiskBTree.createNew(fileInsert, TREE_DEGREE, 256);
            for (var e : entries) {
                treeForInsert.insert(e.key(), e.value());
            }
            treeForInsert.flush();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (treeForSearch != null) {
            treeForSearch.close();
        }
        if (treeForInsert != null) {
            treeForInsert.close();
        }

        Files.deleteIfExists(fileSearch);
        Files.deleteIfExists(fileInsert);
    }

    @Benchmark
    public DiskBTree btreeCreation() throws IOException {
        var f = Files.createTempFile("disk-btree-create-", ".temp");
        var tree = DiskBTree.createNew(f, TREE_DEGREE, 256);
        for (var entry : entries) {
            tree.insert(entry.key(), entry.value());
        }

        tree.flush();
        tree.close();
        Files.deleteIfExists(f);
        
        return tree;
    }

    @Benchmark
    public void btreeSearch(Blackhole blackhole) throws IOException {
        for (var key : keys) {
            var e = treeForSearch.find(key);
            blackhole.consume(e == null ? 0 : e.value());
        }
    }

    @Benchmark
    public void btreeInsert(Blackhole blackhole) throws IOException {
        for (var e : entriesToInsert) {
            treeForInsert.insert(e.key(), e.value());
        }
        treeForInsert.flush();
        blackhole.consume(treeForInsert);
    }
}