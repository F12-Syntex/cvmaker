package com.cvmaker;

import com.cvmaker.configuration.ConfigManager;

public class App {

    public static void main(String[] args) {
        try {
            generateFromConfig();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void generateFromConfig() throws Exception {
        System.out.println("=== CV Generator - Config File Mode ===");

        long startTime = System.currentTimeMillis();

        ConfigManager config = new ConfigManager();
        runGeneration(new CVGenerator(config));

        long endTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("=== Generation Complete ===");
        System.out.println("Total time: " + formatDuration(endTime - startTime));
    }

    /**
     * Run one generation and always shut the generator down afterwards, also
     * when generation fails: its AI executor thread is not a daemon thread and
     * would otherwise keep running.
     */
    static void runGeneration(CVGenerator generator) throws Exception {
        try {
            generator.generate();
        } finally {
            generator.shutdown();
        }
    }

    /**
     * Format duration in human-readable format
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        } else if (milliseconds < 60000) {
            return String.format("%.1fs", milliseconds / 1000.0);
        } else {
            long minutes = milliseconds / 60000;
            long seconds = (milliseconds % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
