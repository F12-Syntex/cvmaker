package com.cvmaker.application.management;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cvmaker.service.ai.AiService;

public class EmailAnalysisService {

    private final AiService aiService;

    public EmailAnalysisService(AiService aiService) {
        this.aiService = aiService;
    }

    public JobApplicationData analyzeEmail(String emailId, String subject, String from, String date, String body) {
        // Extract plain text from HTML
        String cleanBody = extractTextFromHtml(body);
        String cleanSubject = extractTextFromHtml(subject);

        System.out.printf("  🔍 Extracted text length: %d characters\n", cleanBody.length());

        String prompt = buildEnhancedCategorizationPrompt(cleanSubject, from, cleanBody);

        try {
            String aiResponse = aiService.query(prompt);
            return parseEnhancedAIResponse(emailId, cleanSubject, from, date, cleanBody, aiResponse);
        } catch (Exception e) {
            System.err.println("Error in AI categorization: " + e.getMessage());
            return createFallbackData(emailId, cleanSubject, from, date, cleanBody);
        }
    }

    private String extractTextFromHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return "";
        }

        String text = htmlContent
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)<style[^>]*>.*?</style>", "")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();

        if (text.length() > 2000) {
            text = text.substring(0, 2000) + "...";
        }

        return text;
    }

    private String buildEnhancedCategorizationPrompt(String subject, String from, String body) {
        return String.format("""
        Analyze this email and determine if it's STRICTLY related to the user's PERSONAL job application process. 
            
        ONLY CONSIDER AN EMAIL JOB-RELATED IF IT MEETS ONE OF THESE SPECIFIC CRITERIA:
        1. Direct response to a job application the user has submitted (confirmation, status update, rejection, offer)
        2. Interview scheduling/confirmation for a position the user has applied to
        3. Direct recruiter outreach specifically about a job opportunity for the user (not general newsletters)
        4. Follow-up communications about the user's specific application or interview

        OR

        1. email contains "We've sent your application to X" and similar themes, as they are likely related to the user's job application
            
        EXPLICITLY IGNORE ALL OF THESE (even if they contain job-related keywords):
        - General career advice or tips
        - Job boards or career sites sending general updates or listings
        - Newsletters about career opportunities or industry insights
        - "Career opportunities" emails that aren't about a specific application
        - LinkedIn or other platform notifications about jobs
        - Networking event invitations
        - Industry webinars or educational content
        - ANY mass email or newsletter that's not about the user's specific application
            
        IF IN DOUBT, CLASSIFY AS NOT JOB-RELATED. Be extremely strict and conservative.
            
        Return a JSON response with these fields:
            
        Required fields:
        - isJobRelated: true/false (must be false unless it's DEFINITELY about the user's specific job application)
        - confidenceScore: Your confidence in this determination (0.0-1.0)
            
        If isJobRelated is true, also include:
        - category: One of [%s]
        - companyName: Company name (extract from email or content)
        - positionTitle: Job position/title mentioned
        - applicationStatus: Current status (e.g., Applied, Interview Scheduled, Rejected, Offered, etc.)
        - provider: Platform/source (LinkedIn, Indeed, AngelList, Company Website, Direct Email, etc.)
        - workLocation: Remote/Hybrid/On-site/City name (if mentioned)
        - workType: Full-time/Part-time/Contract/Internship (if mentioned)
        - salaryRange: Any salary information mentioned (e.g., "$80k-100k", "Competitive")
        - contactPerson: Name of recruiter or HR contact (if mentioned)
        - contactEmail: Contact email for follow-up (if different from sender)
        - nextSteps: What the candidate should do next (if mentioned)
        - applicationDeadline: Any deadline mentioned
        - requiredSkills: Key skills or requirements mentioned
        - rejectionReason: Specific reason for rejection (if it's a rejection email)
        - offerDetails: Salary, benefits, start date (if it's an offer email)
            
        Optional fields (if mentioned):
        - interviewDate: Any interview date mentioned
        - interviewLocation: Interview location (virtual / physical address)
        - extractedInfo: Brief summary of key information (max 100 words)
        - applicationUrl: Extract any URL related to the job application (application portal, job description page, etc.)
            
        If isJobRelated is false, simply return {"isJobRelated": false, "confidenceScore": 0.9} and ignore other fields.
            
        IMPORTANT: Only extract information that is explicitly mentioned or strongly implied in the email content. 
        Do not make assumptions or add generic information. If something is not mentioned, leave it empty.
            
        Email Details:
        Subject: %s
        From: %s
        Body (plain text): %s
            
        Return only valid JSON without any additional text or explanations.
        """,
                EmailCategory.getAllCategories(),
                subject, from, body
        );
    }

    private JobApplicationData parseEnhancedAIResponse(String emailId, String subject, String from, String date, String body, String aiResponse) {
        JobApplicationData data = new JobApplicationData(emailId, subject, from, date);

        try {
            String cleanResponse = aiResponse.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }

            String isJobRelatedStr = extractJsonValue(cleanResponse, "isJobRelated");
            boolean isJobRelated = "true".equalsIgnoreCase(isJobRelatedStr);
            data.setJobRelated(isJobRelated);

            String confidenceStr = extractJsonValue(cleanResponse, "confidenceScore");
            if (confidenceStr != null) {
                try {
                    data.setConfidenceScore(Double.parseDouble(confidenceStr));
                } catch (NumberFormatException e) {
                    data.setConfidenceScore(0.7);
                }
            } else {
                data.setConfidenceScore(0.7);
            }

            if (!isJobRelated) {
                return data;
            }

            // Basic fields
            String category = extractJsonValue(cleanResponse, "category");
            try {
                data.setCategory(EmailCategory.valueOf(category));
            } catch (IllegalArgumentException e) {
                // Use fallback categorization
            }

            data.setCompanyName(extractJsonValue(cleanResponse, "companyName"));
            data.setPositionTitle(extractJsonValue(cleanResponse, "positionTitle"));
            data.setApplicationStatus(extractJsonValue(cleanResponse, "applicationStatus"));
            data.setInterviewDate(extractJsonValue(cleanResponse, "interviewDate"));
            data.setInterviewLocation(extractJsonValue(cleanResponse, "interviewLocation"));
            data.setExtractedInfo(extractJsonValue(cleanResponse, "extractedInfo"));
            data.setApplicationUrl(extractJsonValue(cleanResponse, "applicationUrl"));

            // Enhanced fields
            data.setProvider(determineProvider(from, body, extractJsonValue(cleanResponse, "provider")));
            data.setWorkLocation(extractJsonValue(cleanResponse, "workLocation"));
            data.setWorkType(extractJsonValue(cleanResponse, "workType"));
            data.setSalaryRange(extractJsonValue(cleanResponse, "salaryRange"));
            data.setContactPerson(extractJsonValue(cleanResponse, "contactPerson"));
            data.setContactEmail(extractJsonValue(cleanResponse, "contactEmail"));
            data.setNextSteps(extractJsonValue(cleanResponse, "nextSteps"));
            data.setApplicationDeadline(extractJsonValue(cleanResponse, "applicationDeadline"));
            data.setRequiredSkills(extractJsonValue(cleanResponse, "requiredSkills"));
            data.setRejectionReason(extractJsonValue(cleanResponse, "rejectionReason"));
            data.setOfferDetails(extractJsonValue(cleanResponse, "offerDetails"));

        } catch (Exception e) {
            System.err.println("Error parsing AI response: " + e.getMessage());
            return createFallbackData(emailId, subject, from, date, body);
        }

        return data;
    }

    private String determineProvider(String from, String body, String aiProvider) {
        if (aiProvider != null && !aiProvider.trim().isEmpty()) {
            return aiProvider;
        }

        String combinedText = (from + " " + body).toLowerCase();

        if (combinedText.contains("linkedin") || from.toLowerCase().contains("linkedin")) {
            return "LinkedIn";
        } else if (combinedText.contains("indeed") || from.toLowerCase().contains("indeed")) {
            return "Indeed";
        } else if (combinedText.contains("glassdoor") || from.toLowerCase().contains("glassdoor")) {
            return "Glassdoor";
        } else if (combinedText.contains("angellist") || from.toLowerCase().contains("angellist")) {
            return "AngelList";
        } else if (combinedText.contains("ziprecruiter") || from.toLowerCase().contains("ziprecruiter")) {
            return "ZipRecruiter";
        } else if (combinedText.contains("monster") || from.toLowerCase().contains("monster")) {
            return "Monster";
        } else if (combinedText.contains("dice") || from.toLowerCase().contains("dice")) {
            return "Dice";
        } else if (from.toLowerCase().contains("noreply") || from.toLowerCase().contains("no-reply")) {
            return "Company Website";
        } else {
            return "Direct Email";
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"|\"" + key + "\"\\s*:\\s*([^,}\\]]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return value != null ? value.trim().replaceAll("\"$", "") : null;
        }
        return null;
    }

    private JobApplicationData createFallbackData(String emailId, String subject, String from, String date, String body) {
        JobApplicationData fallbackData = new JobApplicationData(emailId, subject, from, date);

        boolean isJobRelated = isJobRelatedRuleBased(subject, from, body);
        fallbackData.setJobRelated(isJobRelated);

        if (isJobRelated) {
            fallbackData.setCategory(categorizeEmailRuleBased(subject, body));
            fallbackData.setConfidenceScore(0.5);
            fallbackData.setProvider(determineProvider(from, body, null));
        }

        return fallbackData;
    }

    private boolean isJobRelatedRuleBased(String subject, String from, String body) {
        String combinedText = (subject + " " + from + " " + body).toLowerCase();

        // First, check for common non-job indicators
        String[] nonJobKeywords = {
            "newsletter", "update", "opportunity", "opportunities", "career tips",
            "network", "connect", "subscription", "weekly", "monthly",
            "social", "notification", "general", "blast", "all", "invitation",
            "community", "platform", "discover", "explore", "course", "training"
        };

        for (String keyword : nonJobKeywords) {
            if (combinedText.contains(keyword)) {
                return false;
            }
        }

        // Strong indicators of user's specific job application
        String[] specificApplicationKeywords = {
            "your application", "you applied", "your candidacy", "your resume",
            "your interview", "we received your", "thank you for applying",
            "your interest in", "moving forward with your", "regarding your application",
            "your recent application", "your submission"
        };

        for (String phrase : specificApplicationKeywords) {
            if (combinedText.contains(phrase)) {
                return true;
            }
        }

        // Explicit job application stages
        if ((combinedText.contains("interview") && (combinedText.contains("schedule") || combinedText.contains("confirm")))
                || (combinedText.contains("offer") && combinedText.contains("position"))
                || (combinedText.contains("reject") && combinedText.contains("application"))
                || (combinedText.contains("not moving forward") && combinedText.contains("application"))) {
            return true;
        }

        return false;
    }

    private EmailCategory categorizeEmailRuleBased(String subject, String body) {
        String combinedText = (subject + " " + body).toLowerCase();

        if (combinedText.contains("interview") && (combinedText.contains("invite") || combinedText.contains("schedule"))) {
            return EmailCategory.INTERVIEW_INVITATION;
        }
        if (combinedText.contains("offer") && (combinedText.contains("job") || combinedText.contains("position"))) {
            return EmailCategory.OFFER;
        }
        if (combinedText.contains("reject") || combinedText.contains("unfortunately") || combinedText.contains("not moving forward")) {
            return EmailCategory.REJECTION;
        }
        if (combinedText.contains("application received") || combinedText.contains("thank you for applying")) {
            return EmailCategory.APPLICATION_CONFIRMATION;
        }
        if (combinedText.contains("assessment") || combinedText.contains("test") || combinedText.contains("coding challenge")) {
            return EmailCategory.ASSESSMENT_REQUEST;
        }
        if (combinedText.contains("phone") && combinedText.contains("screen")) {
            return EmailCategory.SCREENING_CALL;
        }

        return EmailCategory.APPLICATION_CONFIRMATION;
    }
}
