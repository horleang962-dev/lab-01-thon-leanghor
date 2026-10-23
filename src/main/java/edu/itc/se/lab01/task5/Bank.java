package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Bank {

    // --- 1. Deadlocking Version with Watchdog ---
    static final class Account {
        final int id;
        private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    // --- 4. Fix A: OrderedBank (Lock Ordering) ---
    static final class OrderedAccount {
        final int id;
        private long balance;
        OrderedAccount(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static void orderedTransfer(OrderedAccount from, OrderedAccount to, long amount) {
        if (from.id == to.id) return;
        OrderedAccount first = from.id < to.id ? from : to;
        OrderedAccount second = from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    // --- 5 & 6. Fix B: TryLockAccount & TryLockBank with Graceful Shutdown ---
    static final class TryLockAccount {
        final int id;
        private long balance;
        final ReentrantLock lock = new ReentrantLock();

        TryLockAccount(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static class TryLockResult {
        long transfersCompleted = 0;
        long retries = 0;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 1 & 3. Running Deadlock with Watchdog == =");
        runDeadlockDemo();

        System.out.println("\n=== 4. Running OrderedBank ===");
        runOrderedBankDemo();

        System.out.println("\n=== 5 & 6. Running TryLockBank with Graceful Shutdown ===");
        runTryLockShutdownDemo();
    }

    private static void runDeadlockDemo() throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        // Watchdog daemon thread to find deadlocks
        Thread watchdog = new Thread(() -> {
            var threadMxBean = ManagementFactory.getThreadMXBean();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                    long[] deadlockedIds = threadMxBean.findDeadlockedThreads();
                    if (deadlockedIds != null) {
                        var info = threadMxBean.getThreadInfo(deadlockedIds);
                        System.out.print("[watchdog] DEADLOCK detected between: ");
                        for (int i = 0; i < info.length; i++) {
                            System.out.print(info[i].getThreadName() + (i == info.length - 1 ? "" : ", "));
                        }
                        System.out.println();
                        break;
                    }
                }
            } catch (InterruptedException ignored) {}
        });
        watchdog.setDaemon(true);
        watchdog.start();

        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                // Run a few iterations before hanging into deadlock
                for (int i = 0; i < 10_000; i++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) transfer(a, b, rnd.nextInt(1, 10));
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        // Give it up to 3 seconds to deadlock
        Thread.sleep(3000);
        for (Thread t : workers) {
            t.interrupt();
        }
    }

    private static void runOrderedBankDemo() throws InterruptedException {
        OrderedAccount[] accounts = new OrderedAccount[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new OrderedAccount(i, 1_000);

        Thread[] workers = new Thread[8];
        long startTime = System.nanoTime();
        AtomicLong totalTransfers = new AtomicLong(0);

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long localCount = 0;
                for (int i = 0; i < 10_000; i++) {
                    OrderedAccount a = accounts[rnd.nextInt(accounts.length)];
                    OrderedAccount b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        orderedTransfer(a, b, rnd.nextInt(1, 10));
                        localCount++;
                    }
                }
                totalTransfers.addAndGet(localCount);
            }, "ordered-worker-" + w);
            workers[w].start();
        }

        for (Thread t : workers) t.join();
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        long total = 0;
        for (OrderedAccount a : accounts) total += a.balance();
        System.out.printf("transfers=%d total=%d (expected 4000) in %d ms%n", totalTransfers.get(), total, elapsed);
    }

    private static void runTryLockShutdownDemo() throws InterruptedException {
        TryLockAccount[] accounts = new TryLockAccount[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new TryLockAccount(i, 1_000);

        AtomicLong totalRetries = new AtomicLong(0);
        AtomicLong totalCompletedTransfers = new AtomicLong(0);

        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long localTransfers = 0;
                long localRetries = 0;

                while (!Thread.currentThread().isInterrupted()) {
                    TryLockAccount a = accounts[rnd.nextInt(accounts.length)];
                    TryLockAccount b = accounts[rnd.nextInt(accounts.length)];
                    if (a == b) continue;

                    TryLockAccount first = a.id < b.id ? a : b;
                    TryLockAccount second = a.id < b.id ? b : a;

                    boolean success = false;
                    try {
                        while (!Thread.currentThread().isInterrupted() && !success) {
                            if (first.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                                try {
                                    if (second.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                                        try {
                                            if (a.balance() >= 1) {
                                                a.withdraw(1);
                                                b.deposit(1);
                                                localTransfers++;
                                            }
                                            success = true;
                                        } finally {
                                            second.lock.unlock();
                                        }
                                    } else {
                                        localRetries++;
                                    }
                                } finally {
                                    first.lock.unlock();
                                }
                            } else {
                                localRetries++;
                            }

                            if (!success) {
                                // Random back-off on failure
                                Thread.sleep(rnd.nextInt(1, 5));
                            }
                        }
                    } catch (InterruptedException e) {
                        // Restore interrupt flag as required
                        Thread.currentThread().interrupt();
                    }
                }
                totalCompletedTransfers.addAndGet(localTransfers);
                totalRetries.addAndGet(localRetries);
                System.out.printf("%s stopped after %d transfers%n", Thread.currentThread().getName(), localTransfers);
            }, "transfer-" + w);
            workers[w].start();
        }

        // Graceful shutdown after 2 seconds
        Thread.sleep(2000);
        System.out.println("[main] interrupting 8 workers after 2000 ms");
        for (Thread t : workers) {
            t.interrupt();
        }

        // Join with 1-second timeout
        for (Thread t : workers) {
            t.join(1000);
        }

        long totalBalance = 0;
        for (TryLockAccount a : accounts) totalBalance += a.balance();
        System.out.printf("all workers stopped=true total=%d (expected 4000) retries=%d totalTransfers=%d%n",
                totalBalance, totalRetries.get(), totalCompletedTransfers.get());
    }
}