package edu.itc.se.lab01.task1;

public class CountdownTask implements Runnable{
    @Override
    public void run(){
        String name = Thread.currentThread().getName();
        System.out.println(name + ": 3");

        try{
            Thread.sleep(200);
            System.out.println(name + ": 2");
            Thread.sleep(200);
            System.out.println(name + ": 1");
            Thread.sleep(200);
            System.out.println(name + ": liftoff!");

        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}

