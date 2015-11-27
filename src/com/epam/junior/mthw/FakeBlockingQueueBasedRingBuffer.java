package com.epam.junior.mthw;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * This is not a ring buffer as such, but will come in useful to write the
 * tests.
 * 
 * @author kofa
 */
public class FakeBlockingQueueBasedRingBuffer implements RingBuffer {
    private final ArrayBlockingQueue<Integer> queue;

    public FakeBlockingQueueBasedRingBuffer(int size) {
        queue = new ArrayBlockingQueue<Integer>(size);
    }

    @Override
    public int get() throws InterruptedException {
        return queue.take();
    }

    @Override
    public void put(int value) throws InterruptedException {
        queue.put(value);
    }
}
