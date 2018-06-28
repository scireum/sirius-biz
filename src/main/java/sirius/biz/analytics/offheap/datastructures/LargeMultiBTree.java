/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.datastructures;

import sirius.biz.analytics.offheap.memory.Allocator;
import sirius.biz.analytics.offheap.memory.BitBuddy;
import sirius.biz.analytics.offheap.memory.LargeMemory;
import sirius.biz.analytics.offheap.memory.LargeMemoryPool;
import sirius.biz.analytics.offheap.memory.LargeResource;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LargeMultiBTree implements LargeResource {

    private final int keyLength;
    private final int blockSize;
    private LargeMemory memory;
    private final long recordLength;
    private final AtomicLong numberOfNodes = new AtomicLong();
    private final AtomicLong numberOfItems = new AtomicLong();
    private final boolean allowDuplicates;

    private static final int ROOT_NODE = 0;
    private static final long ROOT_NODE_ADDRESS = 0;
    private static final int NUM_KEYS_INDEX = 0;
    private static final int KEYS_INDEX = 4;
    private final int valuesBaseIndex;

    private class NodeInfo {
        long node;
        long address;
        int numberOfKeys;
        boolean leaf;

        NodeInfo(long node) {
            this.node = node;
            this.address = node * recordLength;
            this.numberOfKeys = memory.readInt(address + NUM_KEYS_INDEX);
            if (BitBuddy.isWrapped(numberOfKeys)) {
                this.numberOfKeys = BitBuddy.unwrap(numberOfKeys);
                this.leaf = true;
            }
        }
    }

    public LargeMultiBTree(LargeMemoryPool pool, String name, int keyLength, int blockSize, boolean allowDuplicates) {
        this(keyLength, blockSize, allowDuplicates);
        initMemory(new Allocator(pool, "LargeMultiLongIntBTree: " + name, this));
    }

    public LargeMultiBTree(Allocator allocator, int keyLength, int blockSize, boolean allowDuplicates) {
        this(keyLength, blockSize, allowDuplicates);
        initMemory(allocator);
    }

    private LargeMultiBTree(int keyLength, int blockSize, boolean allowDuplicates) {
        this.keyLength = keyLength;
        this.blockSize = blockSize;
        this.allowDuplicates = allowDuplicates;
        this.recordLength = BitBuddy.INT_BYTES
                            + keyLength * BitBuddy.LONG_BYTES * blockSize
                            + (blockSize + 1L) * BitBuddy.LONG_BYTES;
        this.valuesBaseIndex = KEYS_INDEX + keyLength * BitBuddy.LONG_BYTES * blockSize;
    }

    protected void initMemory(Allocator allocator) {
        this.memory = new LargeMemory(allocator);

        // Allocate root and mark as leaf
        this.numberOfNodes.incrementAndGet();
        this.memory.alloc(recordLength);
        this.memory.writeInt(ROOT_NODE_ADDRESS + NUM_KEYS_INDEX, BitBuddy.wrap(0));
    }

    public Optional<Long> get(long[] key) {
        long[] buffer = new long[keyLength];
        NodeInfo leafNode = getLeafNode(ROOT_NODE, key, buffer);

        if (leafNode == null) {
            return Optional.empty();
        }

        for (int i = 0; i < leafNode.numberOfKeys; i++) {
            readKey(leafNode.address, i, buffer);
            if (compareKeys(key, buffer) == 0) {
                return Optional.of(memory.readLong(leafNode.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES));
            }
        }

        return Optional.empty();
    }

    public boolean contains(long[] key) {
        long[] buffer = new long[keyLength];
        NodeInfo leafNode = getLeafNode(ROOT_NODE, key, buffer);

        if (leafNode == null) {
            return false;
        }

        for (int i = 0; i < leafNode.numberOfKeys; i++) {
            readKey(leafNode.address, i, buffer);
            if (compareKeys(key, buffer) == 0) {
                return true;
            }
        }

        return false;
    }

    public void iterate(long[] startKey, BiFunction<long[], Long, Boolean> iterator) {
        long[] buffer = new long[keyLength];
        NodeInfo leafNode = getLeafNode(ROOT_NODE, startKey, buffer);

        while (leafNode != null) {
            if (!iterateThroughKeys(leafNode, startKey, iterator, buffer)) {
                return;
            }

            long nextLeaf = memory.readLong(leafNode.address + valuesBaseIndex + blockSize * BitBuddy.LONG_BYTES);
            if (nextLeaf == 0) {
                leafNode = null;
            } else {
                leafNode = new NodeInfo(nextLeaf);
            }
        }
    }

    private boolean iterateThroughKeys(NodeInfo leafNode,
                                       long[] startKey,
                                       BiFunction<long[], Long, Boolean> iterator,
                                       long[] buffer) {
        for (int i = 0; i < leafNode.numberOfKeys; i++) {
            readKey(leafNode.address, i, buffer);
            if (compareKeys(buffer, startKey) >= 0) {
                readKey(leafNode.address, i, buffer);
                long value = memory.readLong(leafNode.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES);
                if (!iterator.apply(buffer, value)) {
                    return false;
                }
            }
        }

        return true;
    }

    private NodeInfo getLeafNode(long node, long[] key, long[] buffer) {
        NodeInfo info = new NodeInfo(node);

        if (info.leaf) {
            return info;
        }

        for (int i = 0; i < info.numberOfKeys; i++) {
            readKey(info.address, i, buffer);
            if (compareKeys(key, buffer) < 0) {
                long nextNode = memory.readLong(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES);
                return getLeafNode(nextNode, key, buffer);
            }
        }

        long nextNode = memory.readLong(info.address + valuesBaseIndex + info.numberOfKeys * BitBuddy.LONG_BYTES);
        if (nextNode == 0) {
            return null;
        } else {
            return getLeafNode(nextNode, key, buffer);
        }
    }

    private int compareKeys(long[] a, long[] b) {
        for (int i = 0; i < keyLength; i++) {
            if (a[i] < b[i]) {
                return -1;
            } else if (a[i] > b[i]) {
                return 1;
            }
        }
        return 0;
    }

    private void readKey(long baseAddress, int index, long[] buffer) {
        for (int i = 0; i < keyLength; i++) {
            buffer[i] = memory.readLong(baseAddress
                                        + KEYS_INDEX
                                        + index * keyLength * BitBuddy.LONG_BYTES
                                        + i * BitBuddy.LONG_BYTES);
        }
    }

    private void writeKey(long baseAddress, int index, long[] buffer) {
        for (int i = 0; i < keyLength; i++) {
            memory.writeLong(baseAddress
                             + KEYS_INDEX
                             + index * keyLength * BitBuddy.LONG_BYTES
                             + i * BitBuddy.LONG_BYTES, buffer[i]);
        }
    }

    public void put(long[] key, long data) {
        put(key, ignored -> data);
    }

    public void put(long[] key, Function<Long, Long> valueProvider) {
        put(new NodeInfo(ROOT_NODE), key, valueProvider);
    }

    private void put(NodeInfo info, long[] key, Function<Long, Long> valueProvider) {
        if (info.numberOfKeys == blockSize && info.node == ROOT_NODE) {
            splitRootNode();
            put(new NodeInfo(ROOT_NODE), key, valueProvider);
            return;
        }

        if (info.leaf) {
            putInLeaf(info, key, valueProvider);
            return;
        }

        long[] buffer = new long[keyLength];
        for (int i = 0; i < info.numberOfKeys; i++) {
            readKey(info.address, i, buffer);
            int compareResult = compareKeys(key, buffer);
            if (compareResult < 0) {
                long nextNode = memory.readLong(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES);
                putInChildNode(info, i, nextNode, key, valueProvider);
                return;
            }
        }

        long nextNode = memory.readLong(info.address + valuesBaseIndex + info.numberOfKeys * BitBuddy.LONG_BYTES);
        if (nextNode != 0) {
            putInChildNode(info, info.numberOfKeys, nextNode, key, valueProvider);
        } else {
            createNewChildNode(info, key, valueProvider);
        }
    }

    private void createNewChildNode(NodeInfo info, long[] key, Function<Long, Long> valueProvider) {
        long nextNodeAddr = memory.alloc(recordLength);
        long nextNode = nextNodeAddr / recordLength;
        if (info.numberOfKeys < blockSize) {
            writeKey(info.address, info.numberOfKeys, key);
            memory.writeInt(nextNodeAddr + NUM_KEYS_INDEX, info.numberOfKeys + 1);
        }

        memory.writeLong(info.address + valuesBaseIndex + info.numberOfKeys * BitBuddy.LONG_BYTES, nextNode);
        memory.writeInt(nextNodeAddr + NUM_KEYS_INDEX, BitBuddy.wrap(1));
        writeKey(nextNodeAddr, 0, key);
        memory.writeLong(nextNodeAddr + valuesBaseIndex, valueProvider.apply(null));
        numberOfItems.incrementAndGet();
        numberOfNodes.incrementAndGet();
    }

    private void putInLeaf(NodeInfo info, long[] key, Function<Long, Long> valueProvider) {
        long[] buffer = new long[keyLength];
        for (int i = 0; i < info.numberOfKeys; i++) {
            readKey(info.address, i, buffer);
            int compareResult = compareKeys(key, buffer);
            if (compareResult == 0 && !allowDuplicates) {
                long previousValue = memory.readLong(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES);
                memory.writeLong(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES,
                                 valueProvider.apply(previousValue));
                return;
            }
            if (compareResult < 0) {
                memory.transferBytes(info.address + KEYS_INDEX + i * keyLength * BitBuddy.LONG_BYTES,
                                     info.address + KEYS_INDEX + (i + 1) * keyLength * BitBuddy.LONG_BYTES,
                                     (info.numberOfKeys - i) * keyLength * BitBuddy.LONG_BYTES);
                memory.transferBytes(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES,
                                     info.address + valuesBaseIndex + (i + 1) * BitBuddy.LONG_BYTES,
                                     (info.numberOfKeys - i) * BitBuddy.LONG_BYTES);
                writeKey(info.address, i, key);
                memory.writeLong(info.address + valuesBaseIndex + i * BitBuddy.LONG_BYTES, valueProvider.apply(null));
                memory.writeInt(info.address + NUM_KEYS_INDEX, BitBuddy.wrap(info.numberOfKeys + 1));
                numberOfItems.incrementAndGet();
                return;
            }
        }

        writeKey(info.address, info.numberOfKeys, key);
        memory.writeLong(info.address + valuesBaseIndex + info.numberOfKeys * BitBuddy.LONG_BYTES,
                         valueProvider.apply(null));
        memory.writeInt(info.address + NUM_KEYS_INDEX, BitBuddy.wrap(info.numberOfKeys + 1));
        numberOfItems.incrementAndGet();
    }

    private void putInChildNode(NodeInfo parent,
                                int insertionIndex,
                                long childNode,
                                long[] key,
                                Function<Long, Long> valueProvider) {
        NodeInfo child = new NodeInfo(childNode);
        if (child.numberOfKeys < blockSize) {
            put(child, key, valueProvider);
        } else {
            splitChildNode(parent, insertionIndex, child, key, valueProvider);
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Explain("The explicit variable makes the code more readable")
    private void splitChildNode(NodeInfo parent,
                                int insertionIndex,
                                NodeInfo child,
                                long[] key,
                                Function<Long, Long> valueProvider) {
        long rightAddress = memory.alloc(recordLength);
        int right = (int) (rightAddress / recordLength);
        int middleIndex = blockSize / 2;
        int numberOfKeysInLeftNode = middleIndex;
        int numberOfKeysInRightNode = blockSize - numberOfKeysInLeftNode;
        int numberOfValuesInLeftNode = numberOfKeysInLeftNode;
        int numberOfValuesInRightNode = numberOfKeysInRightNode + 1;

        long[] middleKey = new long[keyLength];
        readKey(child.address, middleIndex, middleKey);

        if (!child.leaf) {
            numberOfValuesInLeftNode++;
            numberOfKeysInRightNode--;
        }

        memory.writeInt(child.address + NUM_KEYS_INDEX,
                        child.leaf ? BitBuddy.wrap(numberOfKeysInLeftNode) : numberOfKeysInLeftNode);
        memory.writeInt(rightAddress + NUM_KEYS_INDEX,
                        child.leaf ? BitBuddy.wrap(numberOfKeysInRightNode) : numberOfKeysInRightNode);

        memory.transferBytes(child.address
                             + KEYS_INDEX
                             + (blockSize - numberOfKeysInRightNode) * keyLength * BitBuddy.LONG_BYTES,
                             rightAddress + KEYS_INDEX,
                             numberOfKeysInRightNode * keyLength * BitBuddy.LONG_BYTES);
        memory.transferBytes(child.address
                             + valuesBaseIndex
                             + (blockSize - numberOfKeysInRightNode) * BitBuddy.LONG_BYTES,
                             rightAddress + valuesBaseIndex,
                             numberOfValuesInRightNode * BitBuddy.LONG_BYTES);

        if (child.leaf) {
            memory.writeLong(rightAddress + valuesBaseIndex + blockSize * BitBuddy.LONG_BYTES,
                             memory.readLong(child.address + valuesBaseIndex + blockSize * BitBuddy.LONG_BYTES));
        }

        memory.zero(child.address + KEYS_INDEX + numberOfKeysInLeftNode * keyLength * BitBuddy.LONG_BYTES,
                    (blockSize - numberOfKeysInLeftNode) * keyLength * BitBuddy.LONG_BYTES);
        memory.zero(child.address + valuesBaseIndex + numberOfValuesInLeftNode * BitBuddy.LONG_BYTES,
                    (blockSize - numberOfValuesInLeftNode) * BitBuddy.LONG_BYTES);
        if (child.leaf) {
            memory.writeLong(child.address + valuesBaseIndex + blockSize * BitBuddy.LONG_BYTES, right);
        }

        if (insertionIndex < parent.numberOfKeys) {
            memory.transferBytes(parent.address + KEYS_INDEX + insertionIndex * keyLength * BitBuddy.LONG_BYTES,
                                 parent.address + KEYS_INDEX + (insertionIndex + 1) * keyLength * BitBuddy.LONG_BYTES,
                                 (parent.numberOfKeys - insertionIndex) * keyLength * BitBuddy.LONG_BYTES);
        }
        writeKey(parent.address, insertionIndex, middleKey);
        memory.transferBytes(parent.address + valuesBaseIndex + insertionIndex * BitBuddy.LONG_BYTES,
                             parent.address + valuesBaseIndex + (insertionIndex + 1) * BitBuddy.LONG_BYTES,
                             (parent.numberOfKeys - insertionIndex + 1) * BitBuddy.LONG_BYTES);
        memory.writeLong(parent.address + valuesBaseIndex + insertionIndex * BitBuddy.LONG_BYTES, child.node);
        memory.writeLong(parent.address + valuesBaseIndex + (insertionIndex + 1) * BitBuddy.LONG_BYTES, right);
        memory.writeInt(parent.address + NUM_KEYS_INDEX, parent.numberOfKeys + 1);

        if (compareKeys(key, middleKey) < 0) {
            put(child, key, valueProvider);
        } else {
            put(new NodeInfo(right), key, valueProvider);
        }
        numberOfNodes.incrementAndGet();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Explain("The explicit variable makes the code more readable")
    private void splitRootNode() {
        long leftAddress = memory.alloc(recordLength);
        int left = (int) (leftAddress / recordLength);
        long rightAddress = memory.alloc(recordLength);
        int right = (int) (rightAddress / recordLength);
        boolean wasLeaf = BitBuddy.isWrapped(memory.readInt(ROOT_NODE_ADDRESS + NUM_KEYS_INDEX));

        int middleIndex = blockSize / 2;
        int numberOfKeysInLeftNode = middleIndex;
        int numberOfKeysInRightNode = blockSize - numberOfKeysInLeftNode;
        int numberOfValuesInLeftNode = numberOfKeysInLeftNode;
        int numberOfValuesInRightNode = numberOfKeysInRightNode + 1;

        if (!wasLeaf) {
            numberOfValuesInLeftNode++;
            numberOfKeysInRightNode--;
        }

        memory.writeInt(leftAddress + NUM_KEYS_INDEX,
                        wasLeaf ? BitBuddy.wrap(numberOfKeysInLeftNode) : numberOfKeysInLeftNode);
        memory.writeInt(rightAddress + NUM_KEYS_INDEX,
                        wasLeaf ? BitBuddy.wrap(numberOfKeysInRightNode) : numberOfKeysInRightNode);

        memory.transferBytes(ROOT_NODE_ADDRESS + KEYS_INDEX,
                             leftAddress + KEYS_INDEX,
                             numberOfKeysInLeftNode * keyLength * BitBuddy.LONG_BYTES);
        memory.transferBytes(ROOT_NODE_ADDRESS + valuesBaseIndex,
                             leftAddress + valuesBaseIndex,
                             numberOfValuesInLeftNode * BitBuddy.LONG_BYTES);

        if (wasLeaf) {
            memory.writeLong(leftAddress + valuesBaseIndex + blockSize * BitBuddy.LONG_BYTES, right);
        }

        memory.transferBytes(ROOT_NODE_ADDRESS
                             + KEYS_INDEX
                             + (blockSize - numberOfKeysInRightNode) * keyLength * BitBuddy.LONG_BYTES,
                             rightAddress + KEYS_INDEX,
                             numberOfKeysInRightNode * keyLength * BitBuddy.LONG_BYTES);
        memory.transferBytes(ROOT_NODE_ADDRESS
                             + valuesBaseIndex
                             + (blockSize - numberOfKeysInRightNode) * BitBuddy.LONG_BYTES,
                             rightAddress + valuesBaseIndex,
                             numberOfValuesInRightNode * BitBuddy.LONG_BYTES);

        memory.writeInt(ROOT_NODE_ADDRESS + NUM_KEYS_INDEX, 1);
        for (int j = 0; j < keyLength; j++) {
            int keyOffset = middleIndex * keyLength * BitBuddy.LONG_BYTES + j * keyLength * BitBuddy.LONG_BYTES;
            memory.writeLong(ROOT_NODE_ADDRESS + KEYS_INDEX + j * keyLength * BitBuddy.LONG_BYTES,
                             memory.readLong(ROOT_NODE_ADDRESS + KEYS_INDEX + keyOffset));
        }

        memory.zero(ROOT_NODE_ADDRESS + KEYS_INDEX + keyLength * BitBuddy.LONG_BYTES,
                    (blockSize - 1) * keyLength * BitBuddy.LONG_BYTES);
        memory.zero(ROOT_NODE_ADDRESS + valuesBaseIndex + 2 * BitBuddy.LONG_BYTES,
                    (blockSize - 1) * BitBuddy.LONG_BYTES);

        memory.writeLong(ROOT_NODE_ADDRESS + valuesBaseIndex, left);
        memory.writeLong(ROOT_NODE_ADDRESS + valuesBaseIndex + BitBuddy.LONG_BYTES, right);
        numberOfNodes.addAndGet(2);
    }

    @Override
    public String toString() {
        return Strings.apply("%s items in %s nodes", numberOfItems.get(), numberOfNodes.get());
    }

    @Override
    public void release() {
        memory.release();
    }
}
