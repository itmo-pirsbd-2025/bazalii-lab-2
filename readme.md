# B-Tree

I've implemented two versions of B-tree:
- In-memory stored B-Tree with option to store/load whole tree to/from disk. There are also implemented correctness tests and jmh benchmarks.
- Disk-based B-Tree with entries cache and write-back strategy. All nodes are stored as pages of fixed size. Entries are loaded to LRU-cache, while working with B-tree. Changes are stored as "dirty" and are flushed to disk by `flush()`/`close()` call or when the record is dropped from cache. There are also implemented correctness tests and jmh benchmarks

## Benchmarks results

| Benchmark | Size | Mode | Cnt | Score (ms/op) | Error (ms/op) |
|----------|------|------|-----|---------------|---------------|
| DiskBTreeBenchmarks.btreeCreation | 10 000 | avgt | 10 | 18.815 | ±0.381 |
| DiskBTreeBenchmarks.btreeCreation | 100 000 | avgt | 10 | 308.984 | ±117.337 |
| DiskBTreeBenchmarks.btreeInsert | 10 000 | avgt | 10 | 25.931 | ±0.353 |
| DiskBTreeBenchmarks.btreeInsert | 100 000 | avgt | 10 | 46.707 | ±0.948 |
| DiskBTreeBenchmarks.btreeSearch | 10 000 | avgt | 10 | 0.002 | ±0.001 |
| DiskBTreeBenchmarks.btreeSearch | 100 000 | avgt | 10 | 0.003 | ±0.001 |
| InMemoryBTreeBenchmarks.btreeCreation | 10 000 | avgt | 10 | 1.168 | ±0.047 |
| InMemoryBTreeBenchmarks.btreeCreation | 100 000 | avgt | 10 | 19.901 | ±0.707 |
| InMemoryBTreeBenchmarks.btreeInsert | 10 000 | avgt | 10 | 7.924 | ±0.748 |
| InMemoryBTreeBenchmarks.btreeInsert | 100 000 | avgt | 10 | 7.954 | ±0.927 |
| InMemoryBTreeBenchmarks.btreeSaveAndLoad | 10 000 | avgt | 10 | 6.935 | ±0.038 |
| InMemoryBTreeBenchmarks.btreeSaveAndLoad | 100 000 | avgt | 10 | 107.760 | ±33.563 |
| InMemoryBTreeBenchmarks.btreeSearch | 10 000 | avgt | 10 | 0.003 | ±0.001 |
| InMemoryBTreeBenchmarks.btreeSearch | 100 000 | avgt | 10 | 0.006 | ±0.001 |
| InMemoryBTreeBenchmarks.linearArraySearch | 10 000 | avgt | 10 | 0.241 | ±0.042 |
| InMemoryBTreeBenchmarks.linearArraySearch | 100 000 | avgt | 10 | 2.966 | ±0.319 |

Benchmarks show that creation of a tree is much faster for in-memory implementation.
B-tree search is much faster compared to linear search and the difference in duration between b-tree search for 10_000 and 100_000 is insignificant.
Disk implementation search duration is the same(and for 100_000 elements even faster) compared to in-memory because of LRU in-memory cache, DiskNode has more CPU-cache-friendly structure and files may also be cached by OS.

# B-дерево
Я реализовал две версии структуры B-дерево:
- B-дерево, хранящееся в памяти с возможностью сохранить/загрузить полное дерево на/с диска. Также реализованы тесты на корректность работы и бенчмарки jmh.
- B-дерево, хранящееся на диске, использующее LRU-кеш для записей и write-back стратегию. Все ноды хранятся в формате страниц фиксированного размера. Записи загружаются в LRU-кеш во время работы с B-деревом. Изменения хранятся как "грязные" и сохраняются на диск в рамках вызова `flush()`/`close()` методов или когда запись удаляется из кеша. Также реализованы тесты на корректность работы и бенчмарки jmh.

## Результаты бенчмарков
Со сводной таблицей можете ознакомиться в [секции](#benchmarks-results).
Бенчмарки показывают, что создание B-дерева гораздо быстрее для реализации с хранением в памяти.
Также поиск по B-дереву гораздо быстрее линейного поиска. При этом время поиска для B-дерева на 10_000 и 100_000 элементов отличается незначительно. В тестах происходит поиск 50 ключей.
Реализация с сохранением на диске показывает такое же время поиска(и даже быстрее для варианта с 100_000) элементов при сравнении с реализацией в памяти, поскольку в реализации с сохранением в памяти присутствует LRU-кеш, DiskNode имеет более подходящую структуру для кеша процессора, а также файлы могут быть кешированы ОС.