package fr.supermax_8.buildmisc;

import java.util.concurrent.atomic.AtomicLongArray;

public final class AtomicInt3DMarkSet implements Cloneable {

    private final int sizeX, sizeY, sizeZ;
    private final AtomicLongArray data;

    public AtomicInt3DMarkSet(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;

        long bits = (long) sizeX * sizeY * sizeZ;
        int longs = (int) ((bits + 63) >>> 6);

        this.data = new AtomicLongArray(longs);
    }

    private int index(int x, int y, int z) {
        return x + sizeX * (z + sizeZ * y);
    }

    /* ===================== READ ===================== */

    public boolean get(int x, int y, int z) {
        int i = index(x, y, z);
        long mask = 1L << i;
        return (data.get(i >>> 6) & mask) != 0;
    }

    /* ===================== WRITE ===================== */

    public void set(int x, int y, int z) {
        int i = index(x, y, z);
        int word = i >>> 6;
        long mask = 1L << i;

        while (true) {
            long current = data.get(word);
            if ((current & mask) != 0) return; // already set

            long updated = current | mask;
            if (data.compareAndSet(word, current, updated)) {
                return;
            }
        }
    }

    public void clear(int x, int y, int z) {
        int i = index(x, y, z);
        int word = i >>> 6;
        long mask = 1L << i;

        while (true) {
            long current = data.get(word);
            if ((current & mask) == 0) return; // already clear

            long updated = current & ~mask;
            if (data.compareAndSet(word, current, updated)) {
                return;
            }
        }
    }

    /* ===================== BULK ===================== */

    public void clearAll() {
        for (int i = 0; i < data.length(); i++) {
            data.set(i, 0L); // atomic write
        }
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    @FunctionalInterface
    public interface Int3DConsumer {
        boolean accept(int x, int y, int z);
    }

    public void forEachMarked(Int3DConsumer consumer) {
        int sx = sizeX;
        int sy = sizeY;
        int sz = sizeZ;

        int plane = sx * sz;

        for (int word = 0; word < data.length(); word++) {
            long value = data.get(word);

            while (value != 0L) {
                int bit = Long.numberOfTrailingZeros(value);
                int index = (word << 6) + bit;

                if (index >= plane * sy) {
                    return; // out of bounds padding bits
                }

                int y = index / plane;
                int rem = index - y * plane;
                int z = rem / sx;
                int x = rem - z * sx;

                if (consumer.accept(x, y, z)) return;

                value &= value - 1; // clear lowest set bit
            }
        }
    }

    public MarkedIterator iterator() {
        return new MarkedIteratorImpl();
    }

    public interface MarkedIterator {
        /**
         * Iterate up to maxSteps marked cells.
         *
         * @return true if iteration is finished
         */
        boolean step(int maxSteps, Int3DConsumer consumer);
    }

    private final class MarkedIteratorImpl implements MarkedIterator {

        private int wordIndex = 0;
        private long pendingBits = 0L;

        private final int sx = sizeX;
        private final int sy = sizeY;
        private final int sz = sizeZ;
        private final int plane = sx * sz;
        private final int maxIndex = plane * sy;

        @Override
        public boolean step(int maxSteps, Int3DConsumer consumer) {
            int processed = 0;

            while (processed < maxSteps) {

                // Load next word if needed
                while (pendingBits == 0L) {
                    if (wordIndex >= data.length()) {
                        return true; // fully finished
                    }
                    pendingBits = data.get(wordIndex++);
                }

                // Extract lowest set bit
                int bit = Long.numberOfTrailingZeros(pendingBits);
                int index = ((wordIndex - 1) << 6) + bit;

                pendingBits &= pendingBits - 1; // clear bit

                if (index >= maxIndex) {
                    return true; // padding bits
                }

                int y = index / plane;
                int rem = index - y * plane;
                int z = rem / sx;
                int x = rem - z * sx;

                processed++;

                if (consumer.accept(x, y, z)) {
                    return true; // early stop requested
                }
            }

            return false; // not finished yet
        }
    }

    @Override
    public AtomicInt3DMarkSet clone() {
        AtomicInt3DMarkSet copy = new AtomicInt3DMarkSet(sizeX, sizeY, sizeZ);

        // copy atomically
        for (int i = 0; i < data.length(); i++) {
            copy.data.set(i, data.get(i));
        }

        return copy;
    }

}