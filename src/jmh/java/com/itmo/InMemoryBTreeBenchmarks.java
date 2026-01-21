package com.itmo;

import com.itmo.inMemory.ArraysCreator;
import com.itmo.common.Entry;
import com.itmo.inMemory.FullBTreeStore;
import com.itmo.inMemory.InMemoryBTree;
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
public class InMemoryBTreeBenchmarks {

    private static final int TREE_DEGREE = 25;
    private static final int NUMBER_OF_KEYS = 50;

    @Param({"10000", "100000"})
    public int size;

    private List<Entry<Integer, Integer>> entries;
    private List<Entry<Integer, Integer>> entriesToInsert;
    private int[] keys;

    private InMemoryBTree<Integer, Integer> treeForSearch;
    private InMemoryBTree<Integer, Integer> treeForInsert;

    private Path tempFile;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        entries = ArraysCreator.createRandomEntries(size, 12345L);
        keys = ArraysCreator.createRandomKeys(entries, NUMBER_OF_KEYS, 54321L);

        treeForSearch = new InMemoryBTree<>(TREE_DEGREE, entries);

        tempFile = Files.createTempFile("btree-", ".temp");
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        treeForInsert = new InMemoryBTree<>(TREE_DEGREE, entries);
        entriesToInsert = ArraysCreator.createRandomEntries(10_000, 99999L);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Benchmark
    public InMemoryBTree<Integer, Integer> btreeCreation() {
        return new InMemoryBTree<>(TREE_DEGREE, entries);
    }

    @Benchmark
    public void btreeSearch(Blackhole blackhole) {
        for (var key : keys) {
            var entry = treeForSearch.find(key);
            blackhole.consume(entry.value());
        }
    }

    @Benchmark
    public void linearArraySearch(Blackhole blackhole) {
        for (var key : keys) {
            var value = 0;
            for (var entry : entries) {
                if (entry.key() == key) {
                    value = entry.value();
                    break;
                }
            }
            blackhole.consume(value);
        }
    }

    @Benchmark
    public void btreeInsert(Blackhole blackhole) {
        for (var entry : entriesToInsert) {
            treeForInsert.insert(entry.key(), entry.value());
        }

        blackhole.consume(treeForInsert);
    }

    @Benchmark
    public InMemoryBTree<Integer, Integer> btreeSaveAndLoad() throws IOException {
        var tree = new InMemoryBTree<>(TREE_DEGREE, entries);

        FullBTreeStore.save(tempFile, tree);

        return FullBTreeStore.load(tempFile);
    }
}
