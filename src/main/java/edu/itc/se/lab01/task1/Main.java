package edu.itc.se.lab01.task1;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo = new Countdown("apollo");
        Thread gemini = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        Thread[] threads = {apollo, gemini, mercury, artemis};

        if (sequential) {
            for (Thread t : threads) {
                t.run(); // Executes sequentially on the main thread
            }
            System.out.println("Mission control: all launched");
        } else {
            for (Thread t : threads) {
                t.start(); // Executes concurrently
            }
            System.out.println("Mission control: all launched");

            for (Thread t : threads) {
                t.join();
            }
            System.out.println("Mission control: all landed");

            for (Thread t : threads) {
                System.out.println(t.getName() + "  virtual=" + t.isVirtual() + "  state=" + t.getState());
            }
        }
    }
}
