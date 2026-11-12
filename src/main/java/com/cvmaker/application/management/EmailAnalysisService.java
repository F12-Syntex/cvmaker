package com.cvmaker.application.management;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cvmaker.service.ai.AiService;
import com.cvmaker.util.LlmJson;
import com.cvmaker.websearch.SearchResult;
import com.cvmaker.websearch.SearchService;
import com.fasterxml.jackson.databind.JsonNode;

public class EmailAnalysisService {

    /** Confidence recorded when the model gives none. */
    static final double DEFAULT_CONFIDENCE = 0.7;

    /** Sends a prompt to the LLM and returns its text answer. */
    private final Function<String, String> llm;
    private final SearchService searchService;

    public EmailAnalysisService(AiService aiService) {
        this.llm = aiService::query;
        this.searchService = new SearchService(ApplicationConfig.getExaApiKey());
    }

    /**
     * For tests: analyses emails with the given prompt-to-answer function instead of a
     * real {@link AiService}, so no network or API key is needed.
     */
    EmailAnalysisService(Function<String, String> llm) {
        this.llm = llm;
        this.searchService = null;
    }

    /** Where the data of an {@link AnalysisResult} came from. */
    public enum Outcome {
        /** The LLM answered and its answer was used. */
        AI_ANSWER,
        /** The LLM answered, but the answer could not be used; keyword rules filled in. */
        RULES_AFTER_BAD_ANSWER,
        /** The LLM call failed or returned nothing (network, quota, timeout); keyword rules filled in. */
        RULES_AFTER_AI_FAILURE
    }

    /**
     * The analysis of one email together with how it was produced.
     *
     * @param data    the extracted data; never null
     * @param outcome whether the data is the LLM's or a rule-based stand-in
     */
    public record AnalysisResult(JobApplicationData data, Outcome outcome) {

        /**
         * True when the LLM call itself failed, so the data is only a rule-based guess.
         * The caller should not record the email as processed: a later run can analyse it properly.
         */
        public boolean shouldRetry() {
            return outcome == Outcome.RULES_AFTER_AI_FAILURE;
        }
    }

    /**
     * Analyses an email and returns only the data. A failed AI call is not visible here;
     * use {@link #analyzeEmailDetailed} when the caller must know about it.
     */
    public JobApplicationData analyzeEmail(String emailId, String subject, String from, String date, String body) {
        return analyzeEmailDetailed(emailId, subject, from, date, body).data();
    }

    /**
     * Analyses an email and reports whether the LLM produced the result.
     * Never throws for an LLM problem: the result then carries rule-based data and an
     * {@link Outcome} that says what went wrong.
     */
    public AnalysisResult analyzeEmailDetailed(String emailId, String subject, String from, String date, String body) {
        // Extract plain text from HTML
        String cleanBody = extractTextFromHtml(body);
        String cleanSubject = extractTextFromHtml(subject);

        System.out.printf("  Extracted text length: %d characters\n", cleanBody.length());

        String prompt = buildEnhancedCategorizationPrompt(cleanSubject, from, cleanBody);

        String aiResponse;
        try {
            aiResponse = llm.apply(prompt);
        } catch (RuntimeException e) {
            System.err.println("AI call failed, using keyword rules for now: " + e.getMessage());
            return new AnalysisResult(createFallbackData(emailId, cleanSubject, from, date, cleanBody),
                    Outcome.RULES_AFTER_AI_FAILURE);
        }
        if (aiResponse == null || aiResponse.isBlank()) {
            System.err.println("AI returned an empty answer, using keyword rules for now");
            return new AnalysisResult(createFallbackData(emailId, cleanSubject, from, date, cleanBody),
                    Outcome.RULES_AFTER_AI_FAILURE);
        }

        try {
            JobApplicationData jobData = parseEnhancedAIResponse(emailId, cleanSubject, from, date, cleanBody, aiResponse);

            // Only perform web search if the email is job-related
            // if (jobData.isJobRelated() && jobData.getCompanyName() != null) {
            //     enrichWithWebSearch(jobData);
            // }

            return new AnalysisResult(jobData, Outcome.AI_ANSWER);
        } catch (RuntimeException e) {
            System.err.println("Error parsing AI response, using keyword rules: " + e.getMessage());
            return new AnalysisResult(createFallbackData(emailId, cleanSubject, from, date, cleanBody),
                    Outcome.RULES_AFTER_BAD_ANSWER);
        }
    }

    private void enrichWithWebSearch(JobApplicationData jobData) {
        try {
            // Search for job application page
            String applicationQuery = String.format("site:careers.* OR site:jobs.* \"%s\" \"%s\" apply",
                    jobData.getCompanyName(),
                    jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "");

            List<SearchResult> applicationResults = searchService.search(applicationQuery);

            // Search for hiring manager/recruiter contact
            String contactQuery = String.format("\"%s\" recruiter OR \"talent acquisition\" OR \"hiring manager\" email contact",
                    jobData.getCompanyName());

            List<SearchResult> contactResults = searchService.search(contactQuery);

            //ask ai for the most relevant results
            if (applicationResults.isEmpty() && contactResults.isEmpty()) {
                System.out.println("No relevant results found for " + jobData.getCompanyName());
                return;
            }

            System.out.println("Found " + applicationResults.size() + " application results and " + contactResults.size() + " contact results for " + jobData.getCompanyName());

            //use ai to determine the most correct results, and set them
            String applicationText = applicationResults.isEmpty() ? "" : applicationResults.get(0).getSnippet();
            String contactText = contactResults.isEmpty() ? "" : contactResults.get(0).getSnippet();

            String enrichmentPrompt = String.format("""
            Analyze the following information and extract relevant details for job application enrichment:
            Application Information: %s
            Contact Information: %s
            Return a JSON response with the following fields:
            - applicationUrl: URL of the job application page (if found)
            - contactPerson: Name of the recruiter or hiring manager (if found)
            - contactEmail: Email address of the recruiter or hiring manager (if found)
            - extractedInfo: Brief summary of key information (max 100 words)
            """, applicationText, contactText);
            String enrichmentResponse = llm.apply(enrichmentPrompt);
            System.out.println("Enrichment AI Response: " + enrichmentResponse);
            // Parse the enrichment response
            JsonNode enrichment = LlmJson.extractObject(enrichmentResponse).orElse(null);
            String applicationUrl = LlmJson.text(enrichment, "applicationUrl");
            String contactPerson = LlmJson.text(enrichment, "contactPerson");
            String contactEmail = LlmJson.text(enrichment, "contactEmail");
            String extractedInfo = LlmJson.text(enrichment, "extractedInfo");
            // Set the enriched data in the job application data
            if (applicationUrl != null && !applicationUrl.isEmpty()) {
                jobData.setApplicationUrl(applicationUrl);
            }
            if (contactPerson != null && !contactPerson.isEmpty()) {
                jobData.setContactPerson(contactPerson);
            }
            if (contactEmail != null && !contactEmail.isEmpty()) {
                jobData.setContactEmail(contactEmail);
            }
            if (extractedInfo != null && !extractedInfo.isEmpty()) {
                jobData.setExtractedInfo(extractedInfo);
            }

        } catch (IOException e) {
            System.err.println("Error in web search enrichment: " + e.getMessage());
        }
    }

    private String extractEmailFromText(String text) {
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
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
        - Opt-in forms or general job alerts
            
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

    /** Builds the data from the LLM answer. Throws a RuntimeException when the answer cannot be used. */
    private JobApplicationData parseEnhancedAIResponse(String emailId, String subject, String from, String date, String body, String aiResponse) {
        JobApplicationData data = new JobApplicationData(emailId, subject, from, date);

        JsonNode json = LlmJson.extractObject(aiResponse)
                .orElseThrow(() -> new IllegalArgumentException("the answer holds no JSON object"));

        Boolean isJobRelated = LlmJson.bool(json, "isJobRelated");
        if (isJobRelated == null) {
            throw new IllegalArgumentException("the answer has no usable isJobRelated field");
        }
        data.setJobRelated(isJobRelated);
        data.setConfidenceScore(confidenceFrom(LlmJson.number(json, "confidenceScore")));

        if (!isJobRelated) {
            return data;
        }

        // Basic fields
        // The prompt asks for a display name ("Interview Invitation"); models also
        // answer with the enum name or nothing. An unknown answer must not discard
        // the other extracted fields, so fall back to the rule-based category.
        data.setCategory(EmailCategory.fromLabel(LlmJson.text(json, "category"))
                .orElseGet(() -> EmailRuleClassifier.categorize(subject, body)));

        data.setCompanyName(LlmJson.text(json, "companyName"));
        data.setPositionTitle(LlmJson.text(json, "positionTitle"));
        data.setApplicationStatus(LlmJson.text(json, "applicationStatus"));
        data.setInterviewDate(LlmJson.text(json, "interviewDate"));
        data.setInterviewLocation(LlmJson.text(json, "interviewLocation"));
        data.setExtractedInfo(LlmJson.text(json, "extractedInfo"));
        data.setApplicationUrl(LlmJson.text(json, "applicationUrl"));

        // Enhanced fields
        data.setProvider(determineProvider(from, body, LlmJson.text(json, "provider")));
        data.setWorkLocation(LlmJson.text(json, "workLocation"));
        data.setWorkType(LlmJson.text(json, "workType"));
        data.setSalaryRange(LlmJson.text(json, "salaryRange"));
        data.setContactPerson(LlmJson.text(json, "contactPerson"));
        data.setContactEmail(LlmJson.text(json, "contactEmail"));
        data.setNextSteps(LlmJson.text(json, "nextSteps"));
        data.setApplicationDeadline(LlmJson.text(json, "applicationDeadline"));
        data.setRequiredSkills(LlmJson.text(json, "requiredSkills"));
        data.setRejectionReason(LlmJson.text(json, "rejectionReason"));
        data.setOfferDetails(LlmJson.text(json, "offerDetails"));

        return data;
    }

    /**
     * Turns the model's confidence into a value from 0 to 1. A missing value becomes
     * {@link #DEFAULT_CONFIDENCE}; a percentage such as 90 becomes 0.9.
     */
    static double confidenceFrom(Double raw) {
        if (raw == null) {
            return DEFAULT_CONFIDENCE;
        }
        double value = raw > 1.0 && raw <= 100.0 ? raw / 100.0 : raw;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String determineProvider(String from, String body, String aiProvider) {
        if (aiProvider != null && !aiProvider.trim().isEmpty()) {
            return aiProvider;
        }
        return EmailRuleClassifier.provider(from, body);
    }

    private JobApplicationData createFallbackData(String emailId, String subject, String from, String date, String body) {
        JobApplicationData fallbackData = new JobApplicationData(emailId, subject, from, date);

        boolean isJobRelated = EmailRuleClassifier.isJobRelated(subject, from, body);
        fallbackData.setJobRelated(isJobRelated);

        if (isJobRelated) {
            fallbackData.setCategory(EmailRuleClassifier.categorize(subject, body));
            fallbackData.setConfidenceScore(0.5);
            fallbackData.setProvider(determineProvider(from, body, null));
        }

        return fallbackData;
    }
}
