package com.itmo;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public final class BenchmarkRunner {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(InMemoryBTreeBenchmarks.class.getSimpleName())
                .detectJvmArgs()
                .build();

        new Runner(opt).run();
    }
}
