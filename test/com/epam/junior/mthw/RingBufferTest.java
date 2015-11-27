package com.epam.junior.mthw;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.epam.junior.mthw.RepeatRule.Repeat;

public class RingBufferTest {
    private final static int REPETITIONS = 100;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    private static final int BUFFER_SIZE = 4;
    private static final int DUMMY_VALUE = 0;
    private RingBuffer buffer;
    private Timer timer;

    private volatile boolean backgroundTaskFailed;

    @Before
    public void setup() {
//        buffer = new FakeBlockingQueueBasedRingBuffer(BUFFER_SIZE);
    	buffer = new GoodRingBuffer(BUFFER_SIZE);
        timer = new Timer();
    }

    @After
    public void teardown() {
        timer.cancel();
        if (backgroundTaskFailed) {
            fail();
        }
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testGetBlocksIfEmptyAndProceedsAfterPut() {
        long scheduledPutTime = schedule(new PutTask(DUMMY_VALUE), 100);
        getFromBuffer();
        assertTrue(System.currentTimeMillis() >= scheduledPutTime);
    }

    private long schedule(TimerTask task, long delayMillis) {
        long scheduledRunMillis = System.currentTimeMillis() + delayMillis;
        Date scheduledRunTime = new Date(scheduledRunMillis);
        timer.schedule(task, scheduledRunTime);
        return scheduledRunMillis;
    }

    private int getFromBuffer() {
        int value;
        try {
            value = buffer.get();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return value;
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testPutBlocksIfFullAndProceedsAfterGet() {
        fillBuffer();
        AtomicInteger gotValueHolder = new AtomicInteger();
        long scheduledGetTime = schedule(new GetTask(gotValueHolder), 100);
        putRandomIntoBuffer();
        assertTrue(System.currentTimeMillis() >= scheduledGetTime);
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testGetReceivesValuePutAfterInvoked() {
        int putValue = randomInt();
        schedule(new PutTask(putValue), 50);
        int value = getFromBuffer();
        assertTrue(value == putValue);
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testGetDoesNotBlockIfNotEmpty() {
        long start = System.currentTimeMillis();
        putRandomIntoBuffer();
        getFromBuffer();
        assertTrue(System.currentTimeMillis() <= start + 50);
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testPutDoesNotBlockIfNotFull() {
        fillBuffer();
        getFromBuffer();
        long start = System.currentTimeMillis();
        putRandomIntoBuffer();
        assertTrue(System.currentTimeMillis() <= start + 50);
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testGetReceivesValuePutBeforeInvoked() {
        int putValue = putRandomIntoBuffer();
        int gotValue = getFromBuffer();
        assertTrue(gotValue == putValue);
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testGetCanBeInterruptedAfterCalled() {
        Thread testThread = Thread.currentThread();
        long scheduledInterruptionMillis = schedule(new InterruptingTask(
                testThread), 100);
        try {
            buffer.get();
            fail("get() was not interrupted");
        } catch (InterruptedException e) {
            assertTrue(System.currentTimeMillis() >= scheduledInterruptionMillis);
        }
    }

    @Test
    @Repeat(times = REPETITIONS)
    public void testPutCanBeInterruptedAfterCalled() {
        fillBuffer();
        Thread testThread = Thread.currentThread();
        long scheduledInterruptionMillis = schedule(new InterruptingTask(
                testThread), 100);
        try {
            buffer.put(DUMMY_VALUE);
            fail("put() was not interrupted");
        } catch (InterruptedException e) {
            assertTrue(System.currentTimeMillis() >= scheduledInterruptionMillis);
        }
    }

    private void fillBuffer() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            putIntoBuffer(i);
        }
    }

    private int putRandomIntoBuffer() {
        return putIntoBuffer(randomInt());
    }

    private int putIntoBuffer(int value) {
        try {
            buffer.put(value);
        } catch (InterruptedException e) {
            throw new AssertionError("e");
        }
        return value;
    }

    private int randomInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    private class PutTask extends TimerTask {
        private final int value;

        PutTask(int value) {
            this.value = value;
        }

        @Override
        public void run() {
            try {
                buffer.put(value);
            } catch (InterruptedException e) {
                backgroundTaskFailed = true;
            }
        }
    }

    private class GetTask extends TimerTask {
        private final AtomicInteger gotValueHolder;

        GetTask(AtomicInteger gotValueHolder) {
            this.gotValueHolder = gotValueHolder;
        }

        @Override
        public void run() {
            try {
                int gotValue = buffer.get();
                gotValueHolder.set(gotValue);
            } catch (InterruptedException e) {
                backgroundTaskFailed = true;
            }
        }
    }

    private static class InterruptingTask extends TimerTask {
        private final Thread threadToInterrupt;

        public InterruptingTask(Thread threadToInterrupt) {
            this.threadToInterrupt = threadToInterrupt;
        }

        @Override
        public void run() {
            threadToInterrupt.interrupt();
        }
    }
}
