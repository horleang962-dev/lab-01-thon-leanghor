package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        long startTime = System.nanoTime();
        OrderQueue queue = new BoundedBuffer(5);
        AtomicInteger processedCount = new AtomicInteger(0);

        int numProducers = 2;
        int ordersPerProducer = 50;
        int numConsumers = 3;

        Thread[] producers = new Thread[numProducers];
        for (int i = 0; i < numProducers; i++) {
            producers[i] = new Thread(new Producer(queue, i * ordersPerProducer + 1, ordersPerProducer));
            producers[i].start();
        }

        Thread[] consumers = new Thread[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            consumers[i] = new Thread(new Consumer(queue, "consumer-" + (i + 1), processedCount));
            consumers[i].start();
        }

        // Wait for all producers to finish
        for (Thread p : producers) {
            p.join();
        }

        // Send poison pills (one per consumer)
        for (int i = 0; i < numConsumers; i++) {
            queue.put(new Order(-1, "POISON", 0));
        }

        // Wait for all consumers to finish
        for (Thread c : consumers) {
            c.join();
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        int total = processedCount.get();
        System.out.printf("Total processed: %d (expected 100) in %d ms%n", total, elapsedMs);
    }
}