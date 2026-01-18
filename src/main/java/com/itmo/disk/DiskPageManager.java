package com.itmo.disk;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

final class DiskPageManager implements Closeable {

    // Header layout (page 0):
    private static final int BtreeHeader = 0x42545245; // 'BTRE'
    private static final int VERSION = 1;

    private final FileChannel channel;
    private final int degree;
    private final int pageSize;
    private int rootPageId;
    private int nextPageId;
    private int freeListHead;

    private final int cachePages;

    private final LinkedHashMap<Integer, DiskNode> cache;

    static DiskPageManager createNew(Path file, int degree, int cachePages) throws IOException {
        if (degree < 2) {
            throw new IllegalArgumentException("degree must be >= 2");
        }

        var ch = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

        var pm = new DiskPageManager(ch, degree, cachePages);

        pm.rootPageId = -1;
        pm.nextPageId = 1;      // page 0 reserved for header
        pm.freeListHead = -1;

        pm.writeHeader();
        return pm;
    }

    static DiskPageManager open(Path file, int cachePages) throws IOException {
        var channel = FileChannel.open(file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

        var header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(0);
        channel.read(header);
        header.flip();

        var fileHeader = header.getInt();
        var version = header.getInt();

        if (fileHeader != BtreeHeader) {
            throw new IOException("Not a DiskBTree file (bad fileHeader)");
        }

        if (version != VERSION) {
            throw new IOException("Unsupported DiskBTree version: " + version);
        }

        var degree = header.getInt();
        var pageSize = header.getInt();
        var root = header.getInt();
        var next = header.getInt();
        var free = header.getInt();

        var pageManager = new DiskPageManager(channel, degree, cachePages);
        if (pageManager.pageSize != pageSize) {
            throw new IOException("PageSize mismatch: file=" + pageSize + ", impl=" + pageManager.pageSize);
        }
        pageManager.rootPageId = root;
        pageManager.nextPageId = next;
        pageManager.freeListHead = free;

        return pageManager;
    }

    private DiskPageManager(FileChannel ch, int degree, int cachePages) {
        this.channel = ch;
        this.degree = degree;
        this.cachePages = Math.max(16, cachePages);

        // Fixed-size node page (ints only), LITTLE_ENDIAN for speed
        // node layout:
        // byte isLeaf, byte pad1, short pad2, int keyCount,
        // int[maxKeys] keys, int[maxKeys] values, int[maxChildren] children
        var maxKeys = 2 * degree - 1;
        var maxChildren = 2 * degree;

        var bytes = 1 + 1 + 2 + 4 +
                4 * maxKeys +
                4 * maxKeys +
                4 * maxChildren;

        this.pageSize = ((bytes + 4095) / 4096) * 4096;

        // LRU-cache by access
        this.cache = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, DiskNode> eldest) {
                if (size() <= DiskPageManager.this.cachePages) {
                    return false;
                }
                try {
                    flushNode(eldest.getValue());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        };
    }

    int getDegree() {
        return degree;
    }

    int getPageSize() {
        return pageSize;
    }

    int getRootPageId() {
        return rootPageId;
    }

    void setRootPageId(int id) throws IOException {
        rootPageId = id;
        writeHeader();
    }

    int allocateNodePage() throws IOException {
        if (freeListHead != -1) {
            var id = freeListHead;
            var free = readNode(id);
            // first child slot used as "next free"
            freeListHead = free.children[0];
            writeHeader();

            var node = new DiskNode(id, degree);
            node.isLeaf = true;
            node.keyCount = 0;
            node.markDirty();
            putToCache(node);
            flushNode(node);
            return id;
        }

        var id = nextPageId++;
        writeHeader();

        var node = new DiskNode(id, degree);
        node.isLeaf = true;
        node.keyCount = 0;
        node.markDirty();
        putToCache(node);
        flushNode(node);

        return id;
    }

    void freeNodePage(int pageId) throws IOException {
        // Put page into free list: store freeListHead in children[0]
        var node = new DiskNode(pageId, degree);
        node.isLeaf = true;
        node.keyCount = 0;
        node.children[0] = freeListHead;
        node.markDirty();
        flushNode(node);

        freeListHead = pageId;
        writeHeader();

        cache.remove(pageId);
    }

    DiskNode get(int pageId) throws IOException {
        var cached = cache.get(pageId);
        if (cached != null) {
            return cached;
        }

        var n = readNode(pageId);
        putToCache(n);

        return n;
    }

    private void putToCache(DiskNode n) {
        cache.put(n.pageId, n);
    }

    private DiskNode readNode(int pageId) throws IOException {
        var node = new DiskNode(pageId, degree);

        var buffer = ByteBuffer.allocate(pageSize).order(ByteOrder.LITTLE_ENDIAN);
        channel.position((long) pageId * pageSize);
        var read = channel.read(buffer);
        if (read <= 0) {
            return node;
        }
        buffer.flip();

        var leaf = buffer.get();
        buffer.get(); // pad
        buffer.getShort(); // pad
        node.isLeaf = leaf != 0;
        node.keyCount = buffer.getInt();

        for (var i = 0; i < node.maxKeys; i++) {
            node.keys[i] = buffer.getInt();
        }
        for (var i = 0; i < node.maxKeys; i++) {
            node.values[i] = buffer.getInt();
        }
        for (var i = 0; i < node.maxChildren; i++) {
            node.children[i] = buffer.getInt();
        }

        node.dirty = false;
        return node;
    }

    void flushNode(DiskNode node) throws IOException {
        if (!node.dirty) {
            return;
        }

        var buffer = ByteBuffer.allocate(pageSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) (node.isLeaf ? 1 : 0));
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
        buffer.putInt(node.keyCount);

        for (var i = 0; i < node.maxKeys; i++) {
            buffer.putInt(node.keys[i]);
        }
        for (var i = 0; i < node.maxKeys; i++) {
            buffer.putInt(node.values[i]);
        }
        for (var i = 0; i < node.maxChildren; i++) {
            buffer.putInt(node.children[i]);
        }

        buffer.flip();
        channel.position((long) node.pageId * pageSize);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }

        node.dirty = false;
    }

    void flush() throws IOException {
        writeHeader();
        for (var n : cache.values()) {
            flushNode(n);
        }

        channel.force(false);
    }

    private void writeHeader() throws IOException {
        var header = ByteBuffer.allocate(pageSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(BtreeHeader);
        header.putInt(VERSION);
        header.putInt(degree);
        header.putInt(pageSize);
        header.putInt(rootPageId);
        header.putInt(nextPageId);
        header.putInt(freeListHead);
        header.flip();

        channel.position(0);
        while (header.hasRemaining()) {
            channel.write(header);
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        channel.close();
    }
}
