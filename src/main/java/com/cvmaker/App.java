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
        CVGenerator generator = new CVGenerator(config);

        generator.generate();

        long endTime = System.currentTimeMillis();

        generator.shutdown();

        System.out.println();
        System.out.println("=== Generation Complete ===");
        System.out.println("Total time: " + formatDuration(endTime - startTime));
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
