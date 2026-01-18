package com.itmo.inMemory;

import com.itmo.common.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ArraysCreator {
    private ArraysCreator() {
    }

    public static List<Entry<Integer, Integer>> createRandomEntries(int size, long seed) {
        Random rnd = new Random(seed);
        var list = new ArrayList<Entry<Integer, Integer>>(size);
        for (int i = 0; i < size; i++) {
            // Duplicates are allowed, but for fair search benchmarks we want many distinct keys, so we also mix in i using XOR.
            int key = rnd.nextInt() ^ i;
            int val = rnd.nextInt();
            list.add(new Entry<>(key, val));
        }

        return list;
    }

    public static int[] createRandomKeys(List<Entry<Integer, Integer>> data, int n, long seed) {
        Random rnd = new Random(seed);
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) {
            keys[i] = data.get(rnd.nextInt(data.size())).key();
        }

        return keys;
    }
}
