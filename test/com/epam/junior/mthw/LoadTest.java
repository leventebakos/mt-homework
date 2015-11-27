package com.epam.junior.mthw;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LoadTest {
    // These settings reliably break the test of BadRingBuffer on my laptop.
    // private static final int N_WORKERS = 4;
    // private static final int BUFFER_SIZE = 10;
    // private static final int N_ROUNDS = 10;

    /*
     * It's generally recommended to use about twice as many threads as the
     * number of CPUs, as using a lot more threads won't load the CPU more (if
     * there is no core available to run the thread, it'll just sit around
     * waiting), but using more threads than cores will force context switches.
     * Using a low number of threads may mean that no thread is ever removed
     * from the CPU.
     */
    private static final int N_WORKERS = Runtime.getRuntime()
            .availableProcessors() * 2;
    /*
     * We try to cause writers to block, but also give readers a chance to
     * block, too, as the buffer is small enough.
     */
    private static final int BUFFER_SIZE = N_WORKERS / 2;
    private static final int N_ROUNDS = 20_000;

    private static final int MAX_VALUE = 50;
    private ExecutorService workerThreads;
    private static volatile boolean interrupted;
    private RingBuffer buffer;
    private List<Consumer> consumers;
    private List<Producer> producers;

    @Before
    public void setup() {
        // buffer = new BadRingBuffer(BUFFER_SIZE);
        // buffer = new FakeBlockingQueueBasedRingBuffer(BUFFER_SIZE);
    	buffer = new GoodRingBuffer(BUFFER_SIZE);
        consumers = createConsumers();
        producers = createProducers();
        workerThreads = Executors.newFixedThreadPool(consumers.size()
                + producers.size());
    }

    @After
    public void teardown() throws InterruptedException {
        workerThreads.shutdown();
        boolean threadsTerminated = workerThreads.awaitTermination(1,
                TimeUnit.SECONDS);
        assertFalse(interrupted);
        assertTrue(threadsTerminated);
    }

    @Test
    public void test() throws InterruptedException, ExecutionException {
        /*
         * The idea is that each producer and consumer will keep track of how
         * many times it writes / reads a given value. When everything is done,
         * the TOTAL number of times any given value was read by consumers must
         * match the TOTAL number it was inserted by producers. Each producer
         * and consumer maintains its own counters locally; this means producers
         * and consumers don't need to synchronise. In general, multi-threaded
         * tests should be written in a way that prevents modifying the
         * synchronisation of the TESTED code (e.g. if the test performs
         * synchronisation, it may, as a side-effect, alter the behaviour of
         * tested code, introducing or masking errors).
         */
        List<Future<int[]>> receivedCounts = submitAll(consumers);
        List<Future<int[]>> sendCounts = submitAll(producers);
        int[] totalSentCounts = sum(sendCounts);
        int[] totalReceivedCounts = sum(receivedCounts);
        assertArrayEquals(totalSentCounts, totalReceivedCounts);
    }

    private List<Future<int[]>> submitAll(List<? extends Callable<int[]>> tasks) {
        List<Future<int[]>> futures = new ArrayList<>();
        for (Callable<int[]> task : tasks) {
            Future<int[]> future = workerThreads.submit(task);
            futures.add(future);
        }
        return futures;
    }

    private int[] sum(List<Future<int[]>> allCounts)
            throws InterruptedException, ExecutionException {
        int[] totals = new int[MAX_VALUE];
        for (Future<int[]> countsFromOne : allCounts) {
            int[] counts = countsFromOne.get();
            for (int i = 0; i < MAX_VALUE; i++) {
                totals[i] = totals[i] + counts[i];
            }
        }
        return totals;
    }

    private List<Producer> createProducers() {
        List<Producer> producers = new ArrayList<>();
        for (int i = 0; i < N_WORKERS; i++) {
            producers.add(new Producer(buffer));
        }
        return producers;
    }

    private List<Consumer> createConsumers() {
        List<Consumer> consumers = new ArrayList<>();
        for (int i = 0; i < N_WORKERS; i++) {
            consumers.add(new Consumer(buffer));
        }
        return consumers;
    }

    private static class Consumer implements Callable<int[]> {
        private final RingBuffer buffer;
        private final int[] receivedCounts = new int[MAX_VALUE];

        Consumer(RingBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int[] call() {
            for (int i = 0; i < N_ROUNDS; i++) {
                try {
                    int value = buffer.get();
                    receivedCounts[value] = receivedCounts[value] + 1;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    interrupted = true;
                    break;
                }
            }
            return receivedCounts;
        }
    }

    private static class Producer implements Callable<int[]> {
        private final RingBuffer buffer;
        private final int[] sentCounts = new int[MAX_VALUE];

        Producer(RingBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int[] call() {
            for (int i = 0; i < N_ROUNDS; i++) {
                try {
                    int value = i % MAX_VALUE;
                    buffer.put(value);
                    sentCounts[value] = sentCounts[value] + 1;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    interrupted = true;
                    break;
                }
            }
            return sentCounts;
        }
    }
}
