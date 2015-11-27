package com.epam.junior.mthw;

public class GoodRingBuffer implements RingBuffer {

	private static final Object lock = new Object();
	private final int[] buffer;
	private volatile long readCount;
    private volatile long writeCount;
	
	public GoodRingBuffer(int size) {
		buffer = new int[size];
	}
	
	@Override
	public int get() throws InterruptedException {
		synchronized (lock) {
			lock.notifyAll();
			while (readCount >= writeCount) {
				lock.wait();
	        }
			
			int readIndex = (int) (readCount % buffer.length);
	        readCount++;
	        return buffer[readIndex];
		}
	}

	@Override
	public void put(int value) throws InterruptedException {
		synchronized (lock) {
			lock.notifyAll();
			while (writeCount >= readCount + buffer.length) {
				lock.wait();
			}
			
			int writeIndex = (int) (writeCount % buffer.length);
	        buffer[writeIndex] = value;
	        writeCount++;
		}
	}

}
