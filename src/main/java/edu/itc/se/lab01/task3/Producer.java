package edu.itc.se.lab01.task3;

import java.util.Random;

public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int startId;
    private final int count;

    public Producer(OrderQueue queue, int startId, int count) {
        this.queue = queue;
        this.startId = startId;
        this.count = count;
    }

    @Override
    public void run() {
        Random rand = new Random();
        try {
            for (int i = 0; i < count; i++) {
                int id = startId + i;
                Order order = new Order(id, "item-" + id, rand.nextInt(5) + 1);
                queue.put(order);
                Thread.sleep(rand.nextInt(21)); // random 0-20 ms pause
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}