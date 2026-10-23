package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// 1. CPU-bound Task for Prime counting
class PrimeCounter implements Callable<Long> {
    private final long from, to;

    public PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) count++;
        }
        return count;
    }
}

// 2. I/O-bound Task (Simulated download with failures and timeout)
class FakeDownload implements Callable<String> {
    private final String url;
    private final int index;

    public FakeDownload(String url, int index) {
        this.url = url;
        this.index = index;
    }

    @Override
    public String call() throws Exception {
        // Every 50th task throws an IOException ("boom")
        if (index > 0 && index % 50 == 0) {
            throw new IOException("boom");
        }
        // Make one specific task (e.g., index 250) sleep for 5 seconds to test timeout
        if (index == 250) {
            Thread.sleep(5000);
        } else {
            Thread.sleep(200);
        }
        return url + " -> " + url.length();
    }
}

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) if (n % d == 0) { prime = false; break; }
            if (prime) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000;
        final long CHUNKS = 16;

        // --- CPU-bound Part ---
        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);

        t0 = System.nanoTime();
        int cores = Runtime.getRuntime().availableProcessors();
        long totalPrimes = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(cores)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunkSize;
                long to = (i == CHUNKS - 1) ? LIMIT : (from + chunkSize);
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            for (Future<Long> f : futures) {
                totalPrimes += f.get();
            }
            System.out.printf("pool (%d threads): %d primes in %d ms%n", cores, totalPrimes, (System.nanoTime() - t0) / 1_000_000);
        }

        // --- I/O-bound Part: Fixed Platform Thread Pool (20 threads) ---
        runIoBenchmark("fixed(20) platform threads", Executors.newFixedThreadPool(20));

        // --- I/O-bound Part: Virtual Thread Per Task Executor ---
        runIoBenchmark("virtual threads", Executors.newVirtualThreadPerTaskExecutor());
    }

    private static void runIoBenchmark(String label, ExecutorService executor) {
        int totalTasks = 500;
        long t0 = System.nanoTime();
        int ok = 0;
        int failed = 0;
        int timedOut = 0;

        try (executor) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= totalTasks; i++) {
                futures.add(executor.submit(new FakeDownload("https://example.com/file-" + i, i)));
            }

            for (int i = 0; i < futures.size(); i++) {
                Future<String> f = futures.get(i);
                try {
                    // Task index 250 is configured to sleep 5s, we test it with 1 second timeout
                    if (i == 249) {
                        f.get(1, TimeUnit.SECONDS);
                    } else {
                        f.get();
                    }
                    ok++;
                } catch (TimeoutException e) {
                    f.cancel(true);
                    timedOut++;
                } catch (ExecutionException e) {
                    failed++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("downloads on %-30s : ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, elapsedMs);
    }
}