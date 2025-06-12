package com.cvmaker;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class App {

    public static void main(String[] args) {
        System.out.println("==== AI-Powered Modular CV Generator ====");

        Scanner scanner = new Scanner(System.in);

        System.out.println("\nChoose generation mode:");
        System.out.println("1. Traditional (structured JSON input)");
        System.out.println("2. AI-powered (unstructured text input)");
        System.out.print("Enter choice (1 or 2): ");

        String choice = scanner.nextLine().trim();

        try {
            TemplateLoader loader = new TemplateLoader(Paths.get("templates"));
            CVGenerator generator = new CVGenerator(loader);

            if ("1".equals(choice)) {
                generateTraditional(generator);
            } else if ("2".equals(choice)) {
                generateWithAI(scanner, generator);
            } else {
                System.out.println("Invalid choice. Using traditional mode as default.");
                generateTraditional(generator);
            }

        } catch (Exception e) {
            System.err.println("Failed to generate CV: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void generateTraditional(CVGenerator generator) throws Exception {
        // Traditional mode with hardcoded values as in your original code
        System.out.println("\n=== Traditional Mode ===");
        String dataPath = "templates\\classic\\sample_profile.json";
        String templateName = "classic";
        String outputDir = "target";
        String outputPdf = "generated_cv.pdf";

        generator.generateCV(dataPath, templateName, outputDir, outputPdf);
        System.out.println("Done! Output PDF: " + outputDir + "/" + outputPdf);
    }

    private static void generateWithAI(Scanner scanner, CVGenerator generator) throws Exception {
        System.out.println("\n=== AI-Powered Mode ===");

        // Default to userdata.txt in root directory
        String defaultTextPath = "userdata.txt";
        System.out.print("Unstructured text file [" + defaultTextPath + "]: ");
        String textPath = scanner.nextLine().trim();
        if (textPath.isEmpty()) {
            textPath = defaultTextPath;
        }

        // Check if file exists
        if (!Files.exists(Paths.get(textPath))) {
            System.err.println("Error: File '" + textPath + "' not found!");
            System.err.println("Please create the file with your personal/professional information.");
            return;
        }

        // Default to classic template
        String defaultTemplate = "classic";
        System.out.print("Template name [" + defaultTemplate + "]: ");
        String templateName = scanner.nextLine().trim();
        if (templateName.isEmpty()) {
            templateName = defaultTemplate;
        }

        System.out.print("Job description file (optional, press Enter to skip): ");
        String jobDescPath = scanner.nextLine().trim();
        if (jobDescPath.isEmpty()) {
            jobDescPath = null;
        }

        String outputDir = "target";
        String outputPdf = "ai_generated_cv.pdf";

        generator.generateCVFromText(textPath, templateName, outputDir, outputPdf, jobDescPath);
        System.out.println("Done! Output PDF: " + outputDir + "/" + outputPdf);
    }
}
