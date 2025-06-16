package com.cvmaker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Progress tracking utility for CV generation operations
 */
public class ProgressTracker {
    
    private final String operationName;
    private final int totalSteps;
    private final AtomicInteger currentStep;
    private final long startTime;
    private boolean completed;
    
    // ANSI color codes for better visibility
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    
    public ProgressTracker(String operationName, int totalSteps) {
        this.operationName = operationName;
        this.totalSteps = totalSteps;
        this.currentStep = new AtomicInteger(0);
        this.startTime = System.currentTimeMillis();
        this.completed = false;
        
        printHeader();
    }
    
    /**
     * Advance to the next step with a description
     */
    public void nextStep(String stepDescription) {
        if (completed) return;
        
        int step = currentStep.incrementAndGet();
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Calculate progress
        double progress = (double) step / totalSteps;
        int progressBarLength = 40;
        int filledLength = (int) (progress * progressBarLength);
        
        // Build progress bar
        StringBuilder progressBar = new StringBuilder();
        progressBar.append("[");
        for (int i = 0; i < progressBarLength; i++) {
            if (i < filledLength) {
                progressBar.append("█");
            } else {
                progressBar.append("░");
            }
        }
        progressBar.append("]");
        
        // Calculate estimated time remaining
        String timeEstimate = "";
        if (step > 1) {
            long avgTimePerStep = elapsed / step;
            long remainingSteps = totalSteps - step;
            long estimatedRemaining = avgTimePerStep * remainingSteps;
            timeEstimate = " | ETA: " + formatDuration(estimatedRemaining);
        }
        
        // Print progress line
        System.out.printf("\r%s%s Step %d/%d %s %.1f%% - %s%s | Elapsed: %s%s%s",
            CYAN, BOLD, step, totalSteps, progressBar.toString(), 
            progress * 100, stepDescription, timeEstimate,
            formatDuration(elapsed), RESET, 
            step < totalSteps ? "" : "\n");
        
        if (step >= totalSteps) {
            complete();
        }
    }
    
    /**
     * Mark a step as starting (for long-running operations)
     */
    public void startStep(String stepDescription) {
        if (completed) return;
        
        int step = currentStep.get() + 1;
        System.out.printf("\n%s⏳ Step %d/%d: %s%s\n", 
            YELLOW, step, totalSteps, stepDescription, RESET);
    }
    
    /**
     * Complete the current step (call after startStep)
     */
    public void completeStep(String completionMessage) {
        nextStep(completionMessage);
    }
    
    /**
     * Add a sub-progress indicator for AI operations
     */
    public void showAIProgress(String aiOperation) {
        System.out.printf("%s🤖 AI Processing: %s...%s\n", BLUE, aiOperation, RESET);
        
        // Show a simple spinner for AI operations
        showSpinner("Processing with AI", 3000); // 3 second spinner
    }
    
    /**
     * Show a spinner for indeterminate progress
     */
    private void showSpinner(String message, int durationMs) {
        char[] spinner = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
        long startTime = System.currentTimeMillis();
        int i = 0;
        
        System.out.print("   ");
        while (System.currentTimeMillis() - startTime < durationMs) {
            System.out.printf("\r   %s%c %s...%s", CYAN, spinner[i], message, RESET);
            i = (i + 1) % spinner.length;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.printf("\r   %s✓ %s completed%s\n", GREEN, message, RESET);
    }
    
    /**
     * Show progress for LaTeX compilation with real-time updates
     */
    public void showLatexProgress() {
        System.out.printf("%s📄 Compiling LaTeX to PDF...%s\n", BLUE, RESET);
        
        // Simulate LaTeX compilation progress
        String[] latexSteps = {
            "Reading template",
            "Processing packages",
            "Typesetting document",
            "Generating PDF"
        };
        
        for (int i = 0; i < latexSteps.length; i++) {
            System.out.printf("   %s[%d/%d]%s %s...\n", 
                CYAN, i + 1, latexSteps.length, RESET, latexSteps[i]);
            try {
                Thread.sleep(500); // Small delay to show progress
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Complete the entire operation
     */
    public void complete() {
        if (completed) return;
        
        completed = true;
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("\n%s%s✅ %s completed successfully!%s\n", 
            GREEN, BOLD, operationName, RESET);
        System.out.printf("%sTotal time: %s%s\n\n", 
            GREEN, formatDuration(totalTime), RESET);
    }
    
    /**
     * Handle operation failure
     */
    public void fail(String errorMessage) {
        if (completed) return;
        
        completed = true;
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("\n❌ %s failed after %s\n", 
            operationName, formatDuration(totalTime));
        System.out.printf("Error: %s\n\n", errorMessage);
    }
    
    /**
     * Print operation header
     */
    private void printHeader() {
        System.out.printf("\n%s%s=== %s ===%s\n", 
            BOLD, BLUE, operationName.toUpperCase(), RESET);
        System.out.printf("%sStarted at: %s%s\n", 
            CYAN, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), RESET);
        System.out.printf("%sTotal steps: %d%s\n\n", 
            CYAN, totalSteps, RESET);
    }
    
    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long milliseconds) {
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
    
    /**
     * Get current progress percentage
     */
    public double getProgressPercentage() {
        return (double) currentStep.get() / totalSteps * 100;
    }
    
    /**
     * Get estimated completion time
     */
    public long getEstimatedCompletionTime() {
        if (currentStep.get() == 0) return -1;
        
        long elapsed = System.currentTimeMillis() - startTime;
        long avgTimePerStep = elapsed / currentStep.get();
        long remainingSteps = totalSteps - currentStep.get();
        
        return System.currentTimeMillis() + (avgTimePerStep * remainingSteps);
    }
    
    /**
     * Create a progress tracker for AI-powered CV generation
     */
    public static ProgressTracker forAIGeneration() {
        return new ProgressTracker("AI-Powered CV Generation", 6);
    }
    
    /**
     * Create a progress tracker for traditional CV generation
     */
    public static ProgressTracker forTraditionalGeneration() {
        return new ProgressTracker("Traditional CV Generation", 4);
    }
    
    /**
     * Create a progress tracker for template creation
     */
    public static ProgressTracker forTemplateCreation() {
        return new ProgressTracker("AI Template Creation", 5);
    }
    
    /**
     * Create a progress tracker with custom parameters
     */
    public static ProgressTracker create(String operationName, int totalSteps) {
        return new ProgressTracker(operationName, totalSteps);
    }
}