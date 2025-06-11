package com.cvmaker;

import java.util.List;

public class CVData {

    // Personal Information
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String linkedin;

    // Experience
    private List<Experience> experiences;

    // Education
    private List<Education> educations;

    // Skills
    private List<SkillCategory> skillCategories;

    // Certifications
    private List<String> certifications;

    // Constructors
    public CVData() {
    }

    // Getters and Setters
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLinkedin() {
        return linkedin;
    }

    public void setLinkedin(String linkedin) {
        this.linkedin = linkedin;
    }

    public List<Experience> getExperiences() {
        return experiences;
    }

    public void setExperiences(List<Experience> experiences) {
        this.experiences = experiences;
    }

    public List<Education> getEducations() {
        return educations;
    }

    public void setEducations(List<Education> educations) {
        this.educations = educations;
    }

    public List<SkillCategory> getSkillCategories() {
        return skillCategories;
    }

    public void setSkillCategories(List<SkillCategory> skillCategories) {
        this.skillCategories = skillCategories;
    }

    public List<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<String> certifications) {
        this.certifications = certifications;
    }

    // Inner classes
    public static class Experience {

        private String jobTitle;
        private String company;
        private String duration;
        private List<String> responsibilities;

        public Experience(String jobTitle, String company, String duration, List<String> responsibilities) {
            this.jobTitle = jobTitle;
            this.company = company;
            this.duration = duration;
            this.responsibilities = responsibilities;
        }

        // Getters
        public String getJobTitle() {
            return jobTitle;
        }

        public String getCompany() {
            return company;
        }

        public String getDuration() {
            return duration;
        }

        public List<String> getResponsibilities() {
            return responsibilities;
        }
    }

    public static class Education {

        private String degree;
        private String institution;
        private String details;

        public Education(String degree, String institution, String details) {
            this.degree = degree;
            this.institution = institution;
            this.details = details;
        }

        // Getters
        public String getDegree() {
            return degree;
        }

        public String getInstitution() {
            return institution;
        }

        public String getDetails() {
            return details;
        }
    }

    public static class SkillCategory {

        private String categoryName;
        private String skills;

        public SkillCategory(String categoryName, String skills) {
            this.categoryName = categoryName;
            this.skills = skills;
        }

        // Getters
        public String getCategoryName() {
            return categoryName;
        }

        public String getSkills() {
            return skills;
        }
    }
}
