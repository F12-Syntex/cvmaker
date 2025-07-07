package com.cvmaker.application.management;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.cvmaker.service.ai.AiService;

public class JobApplicationResearchService {

    private final AiService aiService;
    private static final int RATE_LIMIT_DELAY_MS = 1000; // Delay between AI calls to avoid rate limiting

    public JobApplicationResearchService(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Enhances a job application with additional researched information
     * @param jobData The job application data to enhance
     * @return The enhanced job application data
     */
    public JobApplicationData enhanceWithResearch(JobApplicationData jobData) {
        if (!jobData.isJobRelated() || jobData.getCompanyName() == null || jobData.getCompanyName().isEmpty()) {
            return jobData; // Skip non-job related or applications without company names
        }

        System.out.printf("\n🔍 Researching additional information for %s at %s...\n", 
                jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "position", 
                jobData.getCompanyName());

        try {
            // Research company information
            String companyInfo = researchCompany(jobData.getCompanyName());
            
            // Research position requirements if we have a position title
            String positionInfo = "";
            if (jobData.getPositionTitle() != null && !jobData.getPositionTitle().isEmpty()) {
                positionInfo = researchPosition(jobData.getPositionTitle(), jobData.getCompanyName());
            }
            
            // Research hiring contacts and follow-up strategies
            String contactInfo = researchContactInformation(jobData.getCompanyName(), 
                    jobData.getPositionTitle(), 
                    jobData.getFromEmail());
            
            // Research application URL and process
            String applicationInfo = researchApplicationProcess(jobData.getCompanyName(),
                    jobData.getPositionTitle(),
                    jobData.getApplicationUrl());
            
            // Research hiring strategies specific to this company
            String hiringStrategyInfo = researchHiringStrategy(jobData.getCompanyName(),
                    jobData.getPositionTitle(),
                    jobData.getCategory() != null ? jobData.getCategory().getDisplayName() : "");

            // Extract and set additional information fields
            updateJobDataWithResearch(jobData, companyInfo, positionInfo, contactInfo, applicationInfo, hiringStrategyInfo);
            
            System.out.println("  ✅ Research completed successfully");
            return jobData;
        } catch (Exception e) {
            System.err.println("  ❌ Error during research: " + e.getMessage());
            return jobData;
        }
    }

    /**
     * Research information about a company
     */
    private String researchCompany(String companyName) throws InterruptedException {
        String prompt = String.format(
            "Research the company \"%s\" and provide the following information in JSON format:\n" +
            "- industry: The primary industry of the company\n" +
            "- size: Approximate size of the company (small, medium, large, enterprise)\n" +
            "- founded: When the company was founded\n" +
            "- headquarters: Company headquarters location\n" +
            "- description: A brief description of what the company does\n" +
            "- products: Main products or services offered\n" +
            "- culture: Company culture and values if known\n" +
            "- competitors: Major competitors\n" +
            "- interviewProcess: Typical interview process at this company if known\n" +
            "- hiringChallenges: Known challenges in their hiring process\n" +
            "\n" +
            "Respond ONLY with a JSON object containing these fields. If information is not known, use null or empty string.",
            companyName
        );

        TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        return aiService.query(prompt);
    }

    /**
     * Research information about a position
     */
    private String researchPosition(String positionTitle, String companyName) throws InterruptedException {
        String prompt = String.format(
            "Research the position \"%s\" at company \"%s\" (or similar positions if company-specific information is unavailable).\n" +
            "Provide the following information in JSON format:\n" +
            "- typicalSalaryRange: Typical salary range for this position\n" +
            "- requiredSkills: Key skills typically required for this position (comma-separated list)\n" +
            "- responsibilities: Key responsibilities for this position (comma-separated list)\n" +
            "- qualifications: Typical qualifications or education required\n" +
            "- careerPath: Possible career progression from this position\n" +
            "- workEnvironment: Typical work environment (remote, hybrid, on-site, etc.)\n" +
            "- keySuccessFactors: What makes someone successful in this role\n" +
            "- commonInterviewQuestions: Typical interview questions for this position\n" +
            "- portfolioTips: Tips for showcasing relevant work/projects\n" +
            "\n" +
            "Respond ONLY with a JSON object containing these fields. If information is not known, use null or empty string.",
            positionTitle, companyName
        );

        TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        return aiService.query(prompt);
    }
    
    /**
     * Research hiring contact information
     */
    private String researchContactInformation(String companyName, String positionTitle, String fromEmail) throws InterruptedException {
        String prompt = String.format(
            "Research potential hiring contacts at \"%s\" for the \"%s\" position.\n" +
            "Based on the email \"%s\" and your knowledge, provide the following in JSON format:\n" +
            "- hiringManagerName: Potential hiring manager name if known\n" +
            "- hiringManagerTitle: Title of the hiring manager if known\n" +
            "- hiringManagerEmail: Email of hiring manager if available\n" +
            "- recruiterName: Name of recruiter or HR contact if different\n" +
            "- recruiterEmail: Email of recruiter if available\n" +
            "- followUpContact: Best person to follow up with (recruiter, hiring manager, etc.)\n" +
            "- followUpEmail: Email address for following up\n" +
            "- followUpTiming: Recommended timing for follow-up (e.g., '1 week after application')\n" +
            "- linkedInProfiles: Relevant LinkedIn profiles to connect with\n" +
            "\n" +
            "Respond ONLY with a JSON object. If information is not known, use null or empty string. Make educated guesses based on email patterns if exact info isn't known.",
            companyName, positionTitle, fromEmail
        );

        TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        return aiService.query(prompt);
    }
    
    /**
     * Research application process and URL
     */
    private String researchApplicationProcess(String companyName, String positionTitle, String currentUrl) throws InterruptedException {
        String prompt = String.format(
            "Research the application process at \"%s\" for \"%s\" positions.\n" +
            "The current application URL is \"%s\" (if any).\n" +
            "Provide the following in JSON format:\n" +
            "- applicationUrl: Better or official application URL if available\n" +
            "- careersSiteUrl: URL to the company's careers page\n" +
            "- applicationTips: Tips for completing their application process\n" +
            "- applicationDeadline: Application deadline if known\n" +
            "- applicationSteps: Typical steps in their application process\n" +
            "- applicationTracking: How to track application status\n" +
            "\n" +
            "Respond ONLY with a JSON object. If information is not known, use null or empty string.",
            companyName, positionTitle, currentUrl != null ? currentUrl : ""
        );

        TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        return aiService.query(prompt);
    }
    
    /**
     * Research company-specific hiring strategy
     */
    private String researchHiringStrategy(String companyName, String positionTitle, String applicationStage) throws InterruptedException {
        String prompt = String.format(
            "Research specific strategies to get hired at \"%s\" for a \"%s\" position.\n" +
            "The current application stage appears to be \"%s\".\n" +
            "Provide the following in JSON format:\n" +
            "- keyValueProposition: What this company specifically values in candidates\n" +
            "- standOutTips: How to stand out from other applicants specifically at this company\n" +
            "- cultureFit: Cultural aspects to emphasize for this company\n" +
            "- followUpStrategy: Effective follow-up approach for this company\n" +
            "- interviewPrep: Specific interview preparation tips for this company\n" +
            "- negotiationTips: Salary/benefits negotiation tips for this company\n" +
            "- commonMistakes: Common mistakes candidates make with this company\n" +
            "- successStories: Brief examples of successful hiring stories if known\n" +
            "\n" +
            "Respond ONLY with a JSON object. If information is not known, use null or empty string.",
            companyName, positionTitle, applicationStage
        );

        TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);
        return aiService.query(prompt);
    }

    /**
     * Update job data with researched information
     */
    private void updateJobDataWithResearch(JobApplicationData jobData, 
                                         String companyInfo, 
                                         String positionInfo, 
                                         String contactInfo,
                                         String applicationInfo,
                                         String hiringStrategyInfo) {
        // Extract information from company research
        String industry = extractJsonValue(companyInfo, "industry");
        String companySize = extractJsonValue(companyInfo, "size");
        String companyDescription = extractJsonValue(companyInfo, "description");
        String culture = extractJsonValue(companyInfo, "culture");
        String interviewProcess = extractJsonValue(companyInfo, "interviewProcess");
        
        // Extract information from position research
        String salaryRange = extractJsonValue(positionInfo, "typicalSalaryRange");
        String requiredSkills = extractJsonValue(positionInfo, "requiredSkills");
        String workEnvironment = extractJsonValue(positionInfo, "workEnvironment");
        String interviewQuestions = extractJsonValue(positionInfo, "commonInterviewQuestions");
        
        // Extract contact information
        String hiringManagerName = extractJsonValue(contactInfo, "hiringManagerName");
        String hiringManagerEmail = extractJsonValue(contactInfo, "hiringManagerEmail");
        String recruiterName = extractJsonValue(contactInfo, "recruiterName");
        String recruiterEmail = extractJsonValue(contactInfo, "recruiterEmail");
        String followUpContact = extractJsonValue(contactInfo, "followUpContact");
        String followUpEmail = extractJsonValue(contactInfo, "followUpEmail");
        String followUpTiming = extractJsonValue(contactInfo, "followUpTiming");
        
        // Extract application information
        String betterApplicationUrl = extractJsonValue(applicationInfo, "applicationUrl");
        String careersSiteUrl = extractJsonValue(applicationInfo, "careersSiteUrl");
        String applicationTips = extractJsonValue(applicationInfo, "applicationTips");
        String applicationDeadline = extractJsonValue(applicationInfo, "applicationDeadline");
        
        // Extract hiring strategy information
        String standOutTips = extractJsonValue(hiringStrategyInfo, "standOutTips");
        String followUpStrategy = extractJsonValue(hiringStrategyInfo, "followUpStrategy");
        String interviewPrep = extractJsonValue(hiringStrategyInfo, "interviewPrep");
        
        // Set contact person if we found a hiring manager or recruiter
        if (jobData.getContactPerson() == null || jobData.getContactPerson().isEmpty()) {
            if (hiringManagerName != null && !hiringManagerName.isEmpty()) {
                jobData.setContactPerson(hiringManagerName + " (Hiring Manager)");
            } else if (recruiterName != null && !recruiterName.isEmpty()) {
                jobData.setContactPerson(recruiterName + " (Recruiter)");
            }
        }
        
        // Set contact email
        if (jobData.getContactEmail() == null || jobData.getContactEmail().isEmpty()) {
            if (hiringManagerEmail != null && !hiringManagerEmail.isEmpty()) {
                jobData.setContactEmail(hiringManagerEmail);
            } else if (recruiterEmail != null && !recruiterEmail.isEmpty()) {
                jobData.setContactEmail(recruiterEmail);
            } else if (followUpEmail != null && !followUpEmail.isEmpty()) {
                jobData.setContactEmail(followUpEmail);
            }
        }
        
        // Update application URL if we found a better one
        if (betterApplicationUrl != null && !betterApplicationUrl.isEmpty() && 
            (jobData.getApplicationUrl() == null || jobData.getApplicationUrl().isEmpty())) {
            jobData.setApplicationUrl(betterApplicationUrl);
        }
        
        // Update application deadline
        if (applicationDeadline != null && !applicationDeadline.isEmpty() && 
            (jobData.getApplicationDeadline() == null || jobData.getApplicationDeadline().isEmpty())) {
            jobData.setApplicationDeadline(applicationDeadline);
        }
        
        // Update salary range
        if (salaryRange != null && !salaryRange.isEmpty() &&
            (jobData.getSalaryRange() == null || jobData.getSalaryRange().isEmpty())) {
            jobData.setSalaryRange(salaryRange);
        }
        
        // Update required skills
        if (requiredSkills != null && !requiredSkills.isEmpty() &&
            (jobData.getRequiredSkills() == null || jobData.getRequiredSkills().isEmpty())) {
            jobData.setRequiredSkills(requiredSkills);
        }
        
        // Update work location
        if (workEnvironment != null && !workEnvironment.isEmpty() &&
            (jobData.getWorkLocation() == null || jobData.getWorkLocation().isEmpty())) {
            jobData.setWorkLocation(workEnvironment);
        }
        
        // Update next steps with follow-up information
        if (followUpStrategy != null && !followUpStrategy.isEmpty()) {
            String currentNextSteps = jobData.getNextSteps();
            String followUpInfo = "Follow-up: " + followUpStrategy;
            if (followUpTiming != null && !followUpTiming.isEmpty()) {
                followUpInfo += " (" + followUpTiming + ")";
            }
            
            if (currentNextSteps != null && !currentNextSteps.isEmpty()) {
                jobData.setNextSteps(currentNextSteps + "\n\n" + followUpInfo);
            } else {
                jobData.setNextSteps(followUpInfo);
            }
        }
        
        // Create a comprehensive research summary
        StringBuilder researchSummary = new StringBuilder();
        
        // Company information section
        if (companyDescription != null && !companyDescription.isEmpty()) {
            researchSummary.append("COMPANY: ").append(companyDescription).append("\n\n");
        }
        
        if (industry != null && !industry.isEmpty() || companySize != null && !companySize.isEmpty()) {
            researchSummary.append("PROFILE: ");
            if (industry != null && !industry.isEmpty()) {
                researchSummary.append(industry);
            }
            if (companySize != null && !companySize.isEmpty()) {
                if (industry != null && !industry.isEmpty()) {
                    researchSummary.append(", ");
                }
                researchSummary.append(companySize).append(" size");
            }
            researchSummary.append("\n\n");
        }
        
        // Application tips section
        if (applicationTips != null && !applicationTips.isEmpty()) {
            researchSummary.append("APPLICATION TIPS: ").append(applicationTips).append("\n\n");
        }
        
        // Interview preparation section
        StringBuilder interviewSection = new StringBuilder();
        if (interviewProcess != null && !interviewProcess.isEmpty()) {
            interviewSection.append("Process: ").append(interviewProcess).append("\n");
        }
        if (interviewPrep != null && !interviewPrep.isEmpty()) {
            interviewSection.append("Preparation: ").append(interviewPrep).append("\n");
        }
        if (interviewQuestions != null && !interviewQuestions.isEmpty()) {
            interviewSection.append("Common Questions: ").append(interviewQuestions);
        }
        
        if (interviewSection.length() > 0) {
            researchSummary.append("INTERVIEW: ").append(interviewSection).append("\n\n");
        }
        
        // Stand out tips section
        if (standOutTips != null && !standOutTips.isEmpty()) {
            researchSummary.append("STAND OUT: ").append(standOutTips).append("\n\n");
        }
        
        // Contact information section
        StringBuilder contactSection = new StringBuilder();
        if (hiringManagerName != null && !hiringManagerName.isEmpty()) {
            contactSection.append("Hiring Manager: ").append(hiringManagerName);
            if (hiringManagerEmail != null && !hiringManagerEmail.isEmpty()) {
                contactSection.append(" (").append(hiringManagerEmail).append(")");
            }
            contactSection.append("\n");
        }
        
        if (recruiterName != null && !recruiterName.isEmpty()) {
            contactSection.append("Recruiter: ").append(recruiterName);
            if (recruiterEmail != null && !recruiterEmail.isEmpty()) {
                contactSection.append(" (").append(recruiterEmail).append(")");
            }
            contactSection.append("\n");
        }
        
        if (followUpContact != null && !followUpContact.isEmpty()) {
            contactSection.append("Follow-up with: ").append(followUpContact);
            if (followUpEmail != null && !followUpEmail.isEmpty()) {
                contactSection.append(" (").append(followUpEmail).append(")");
            }
        }
        
        if (contactSection.length() > 0) {
            researchSummary.append("CONTACTS: \n").append(contactSection).append("\n\n");
        }
        
        // URLs section
        StringBuilder urlSection = new StringBuilder();
        if (betterApplicationUrl != null && !betterApplicationUrl.isEmpty()) {
            urlSection.append("Application: ").append(betterApplicationUrl).append("\n");
        }
        if (careersSiteUrl != null && !careersSiteUrl.isEmpty()) {
            urlSection.append("Careers Page: ").append(careersSiteUrl);
        }
        
        if (urlSection.length() > 0) {
            researchSummary.append("LINKS: \n").append(urlSection);
        }
        
        // Append research summary to existing info
        if (researchSummary.length() > 0) {
            String currentInfo = jobData.getExtractedInfo();
            if (currentInfo != null && !currentInfo.isEmpty()) {
                jobData.setExtractedInfo(currentInfo + "\n\n===== RESEARCH =====\n" + researchSummary.toString());
            } else {
                jobData.setExtractedInfo("===== RESEARCH =====\n" + researchSummary.toString());
            }
        }
    }

    /**
     * Helper method to extract values from JSON response
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        
        try {
            // Simple regex-based JSON value extraction
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"|\"" + key + "\"\\s*:\\s*([^,}\\s]+)";
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = r.matcher(json);
            
            if (m.find()) {
                String value = m.group(1) != null ? m.group(1) : m.group(2);
                return value != null ? value.trim() : null;
            }
        } catch (Exception e) {
            System.err.println("Error extracting JSON value: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Batch process a list of job applications to enhance them with research
     * @param applications List of job applications to enhance
     * @param maxCount Maximum number of applications to process (to limit API calls)
     */
    public void batchEnhanceApplications(List<JobApplicationData> applications, int maxCount) {
        System.out.println("\n=== Enhancing Job Applications with AI Research ===");
        
        int count = 0;
        int totalProcessed = 0;
        
        for (JobApplicationData app : applications) {
            if (count >= maxCount) {
                break;
            }
            
            // Only research applications that are job-related and have a company name
            if (app.isJobRelated() && app.getCompanyName() != null && !app.getCompanyName().isEmpty()) {
                // Skip if already has research data
                if (app.getExtractedInfo() != null && app.getExtractedInfo().contains("RESEARCH")) {
                    System.out.printf("  🔄 Skipping already researched application: %s at %s\n", 
                            app.getPositionTitle(), app.getCompanyName());
                    continue;
                }
                
                try {
                    enhanceWithResearch(app);
                    count++;
                    totalProcessed++;
                    
                    // Add a delay between processing to avoid rate limits
                    Thread.sleep(2000);
                } catch (Exception e) {
                    System.err.println("  ❌ Error enhancing application: " + e.getMessage());
                }
            }
        }
        
        System.out.printf("\n✅ Enhanced %d/%d job applications with additional research\n", 
                totalProcessed, Math.min(maxCount, applications.size()));
    }
}