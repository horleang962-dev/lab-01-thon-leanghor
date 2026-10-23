package edu.itc.se.lab01.task3;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final OrderQueue queue;
    private final String name;
    private final AtomicInteger processedCount;

    public Consumer(OrderQueue queue, String name, AtomicInteger processedCount) {
        this.queue = queue;
        this.name = name;
        this.processedCount = processedCount;
    }

    @Override
    public void run() {
        Random rand = new Random();
        try {
            while (true) {
                Order order = queue.take();
                // Poison pill check (id == -1)
                if (order.id() == -1) {
                    System.out.println(name + " received poison pill, stopping");
                    break;
                }
                Thread.sleep(rand.nextInt(26) + 5); // process sleep 5-30 ms
                System.out.println(name + " processed " + order);
                processedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}