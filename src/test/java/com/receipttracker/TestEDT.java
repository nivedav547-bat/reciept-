package com.receipttracker;
import javax.swing.SwingUtilities;
public class TestEDT {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("UNCAUGHT ON THREAD " + t.getName() + ":");
            e.printStackTrace();
        });
        System.out.println("Calling ReceiptTracker.main...");
        ReceiptTracker.main(args);
        try {
            Thread.sleep(1000);
        } catch (Exception e) {}
        System.out.println("Done sleeping.");
    }
}
