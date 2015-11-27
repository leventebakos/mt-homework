package com.epam.junior.mthw;

// Bad synchronisation, to show that the tests will fail
// with broken code.
public class BadRingBuffer implements RingBuffer {
    private final int[] buffer;
    private volatile long readCount;
    private volatile long writeCount;

    public BadRingBuffer(int size) {
        buffer = new int[size];
    }

    @Override
    public int get() throws InterruptedException {
        while (readCount >= writeCount) {
            // wait until writes catch up; all data that had been written
            // has already been read
        }
        int readIndex = (int) (readCount % buffer.length);
        readCount++;
        return buffer[readIndex];
    }

    @Override
    public void put(int value) throws InterruptedException {
        while (writeCount >= readCount + buffer.length) {
            // wait until reads catch up to avoid overwriting data
        }
        int writeIndex = (int) (writeCount % buffer.length);
        buffer[writeIndex] = value;
        writeCount++;
    }
}
