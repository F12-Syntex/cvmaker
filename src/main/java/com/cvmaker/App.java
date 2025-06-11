package com.cvmaker;

import java.util.Arrays;
import java.util.List;

public class App {

    public static void main(String[] args) {
        System.out.println("Creating Modular LaTeX CV...");
        
        try {
            // Create sample CV data
            CVData cvData = createSampleCVData();
            
            // Generate CV
            CVGenerator generator = new CVGenerator();
            generator.generateCV(cvData);
            
            System.out.println("CV generated successfully!");
        } catch (Exception e) {
            System.err.println("Error creating CV: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static CVData createSampleCVData() {
        CVData cvData = new CVData();
        
        // Personal Information
        cvData.setFullName("JOHN SMITH");
        cvData.setEmail("john.smith@email.com");
        cvData.setPhone("+1 (555) 123-4567");
        cvData.setLocation("San Francisco, CA");
        cvData.setLinkedin("linkedin.com/in/johnsmith");
        
        // Experience
        List<CVData.Experience> experiences = Arrays.asList(
            new CVData.Experience(
                "Senior Software Engineer",
                "TechCorp Solutions",
                "Jan 2021 -- Present",
                Arrays.asList(
                    "Lead development of microservices architecture serving 2M+ users, reduced latency 40%",
                    "Mentor 3 developers, established code review standards, delivered 15+ features ahead of schedule",
                    "Technologies: Java, Spring Boot, PostgreSQL, AWS, Docker"
                )
            ),
            new CVData.Experience(
                "Software Engineer",
                "InnovateIT Inc.",
                "Jun 2019 -- Dec 2020",
                Arrays.asList(
                    "Developed RESTful APIs and React applications, achieved 95% test coverage",
                    "Implemented caching strategies improving response times by 30%",
                    "Technologies: Java, React, PostgreSQL, Redis"
                )
            ),
            new CVData.Experience(
                "Software Developer",
                "StartupXYZ",
                "Aug 2018 -- May 2019",
                Arrays.asList(
                    "Full-stack development, bug fixes, and feature implementation in agile environment"
                )
            )
        );
        cvData.setExperiences(experiences);
        
        // Education
        List<CVData.Education> educations = Arrays.asList(
            new CVData.Education(
                "Bachelor of Science, Computer Science",
                "University of California, Berkeley",
                "May 2018 | GPA: 3.8"
            )
        );
        cvData.setEducations(educations);
        
        // Skills
        List<CVData.SkillCategory> skills = Arrays.asList(
            new CVData.SkillCategory("Languages", "Java, Python, JavaScript, TypeScript, SQL"),
            new CVData.SkillCategory("Frameworks", "Spring Boot, React, Angular, Node.js"),
            new CVData.SkillCategory("Technologies", "AWS, Docker, PostgreSQL, Redis, Git, Jenkins")
        );
        cvData.setSkillCategories(skills);
        
        // Certifications
        List<String> certifications = Arrays.asList(
            "AWS Solutions Architect Associate (2023)",
            "Oracle Java SE 11 Developer (2022)",
            "Scrum Master (2021)"
        );
        cvData.setCertifications(certifications);
        
        return cvData;
    }
}