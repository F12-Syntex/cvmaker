package com.cvmaker;

import java.nio.file.Paths;

public class App {

    public static void main(String[] args) {
        System.out.println("==== Modular CV Generator ====");

        // Ask user for info location and template
        System.out.print("Raw profile data file (JSON): ");
        String dataPath = "templates\\classic\\sample_profile.json";

        System.out.print("Template name (e.g., classic): ");
        String templateName = "classic";

        // Output PDF location
        String outputDir = "target";
        String outputPdf = "generated_cv.pdf";

        try {
            TemplateLoader loader = new TemplateLoader(Paths.get("templates"));
            CVGenerator generator = new CVGenerator(loader);

            generator.generateCV(
                    dataPath,
                    templateName,
                    outputDir,
                    outputPdf
            );

            System.out.println("Done! Output PDF: " + outputDir + "/" + outputPdf);
        } catch (Exception e) {
            System.err.println("Failed to generate CV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
