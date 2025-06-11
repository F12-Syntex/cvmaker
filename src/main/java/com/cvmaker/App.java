package com.cvmaker;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

public class App {

    public static void main(String[] args) {
        System.out.println("Starting CV Maker...");

        try {
            createSimpleCV();
            System.out.println("CV created successfully!");
        } catch (Exception e) {
            System.err.println("Error creating CV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createSimpleCV() throws FileNotFoundException, IOException {
        // Create a PdfWriter
        PdfWriter writer = new PdfWriter("sample_cv.pdf");

        // Create a PdfDocument
        PdfDocument pdfDoc = new PdfDocument(writer);

        // Create a Document
        Document document = new Document(pdfDoc);

        // Add some basic content
        PdfFont titleFont = PdfFontFactory.createFont();
        PdfFont normalFont = PdfFontFactory.createFont();

        // Title
        Paragraph title = new Paragraph("CURRICULUM VITAE")
                .setFont(titleFont)
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        // Add some space
        document.add(new Paragraph("\n"));

        // Personal Information Section
        Paragraph personalInfo = new Paragraph("PERSONAL INFORMATION")
                .setFont(titleFont)
                .setFontSize(14)
                .setBold();
        document.add(personalInfo);

        Paragraph name = new Paragraph("Name: [Your Name Here]")
                .setFont(normalFont)
                .setFontSize(12);
        document.add(name);

        Paragraph email = new Paragraph("Email: [your.email@example.com]")
                .setFont(normalFont)
                .setFontSize(12);
        document.add(email);

        Paragraph phone = new Paragraph("Phone: [Your Phone Number]")
                .setFont(normalFont)
                .setFontSize(12);
        document.add(phone);

        // Add some space
        document.add(new Paragraph("\n"));

        // Professional Summary Section
        Paragraph summaryTitle = new Paragraph("PROFESSIONAL SUMMARY")
                .setFont(titleFont)
                .setFontSize(14)
                .setBold();
        document.add(summaryTitle);

        Paragraph summary = new Paragraph("This is where the AI-generated professional summary will go. "
                + "It will be tailored based on the user's experience, skills, and career objectives.")
                .setFont(normalFont)
                .setFontSize(12);
        document.add(summary);

        // Close the document
        document.close();
    }
}
