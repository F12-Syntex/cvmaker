package com.cvmaker;

import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class AiService {

    private final OpenAIClient client;
    private ChatModel model;
    private double temperature;

    /**
     * Creates a new OpenAI client with default settings
     */
    public AiService() {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
    }

    /**
     * Creates a new OpenAI client with custom settings
     *
     * @param model The OpenAI model to use
     * @param temperature The temperature setting (0-1)
     */
    public AiService(ChatModel model, double temperature) {
        this();
        this.model = model;
        this.temperature = temperature;
    }

    /**
     * Creates a new OpenAI client with a custom client
     *
     * @param client The pre-configured OpenAI client
     */
    public AiService(OpenAIClient client) {
        this.client = client;
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
    }

    /**
     * Set the model to use for queries
     *
     * @param model The OpenAI model
     * @return This OpenAI instance for method chaining
     */
    public AiService setModel(ChatModel model) {
        this.model = model;
        return this;
    }

    /**
     * Set the temperature for queries
     *
     * @param temperature The temperature value (0-1)
     * @return This OpenAI instance for method chaining
     */
    public AiService setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * Send a query to the OpenAI API and get the response
     *
     * @param prompt The prompt to send
     * @return The response text from the AI
     * @throws RuntimeException If there's an error with the API request
     */
    public String query(String prompt) {
        try {
            // Create parameters for the ChatCompletion request
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(model)
                    .temperature(temperature)
                    .build();

            // Execute the API call
            ChatCompletion completion = client.chat().completions().create(params);

            // Extract and log token usage
            if (completion.usage() != null) {
                System.out.println("Token usage:");
                System.out.println("  Prompt tokens: " + completion.usage().get().promptTokens());
                System.out.println("  Completion tokens: " + completion.usage().get().completionTokens());
                System.out.println("  Total tokens: " + completion.usage().get().totalTokens());
            }

            // Extract the response content
            Optional<String> responseContent = completion.choices().get(0).message().content();

            if (!responseContent.isPresent()) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            return responseContent.get();
        } catch (Exception e) {
            throw new RuntimeException("Error while querying OpenAI API", e);
        }
    }

    /**
     * Extract and organize CV data from unstructured text
     *
     * @param unstructuredText The raw user input text
     * @param templateSchema The JSON schema for the template
     * @param jobDescription Optional job description for tailoring
     * @return JSONObject containing structured CV data
     */
    public JSONObject extractCVData(String unstructuredText, String templateSchema, String jobDescription) {
        try {
            String prompt = buildCVExtractionPrompt(unstructuredText, templateSchema, jobDescription);
            String response = query(prompt);

            // Clean and parse the JSON response
            String cleanedResponse = extractJsonFromResponse(response);
            return new JSONObject(cleanedResponse);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process CV data with AI: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a complete LaTeX template based on user requirements and sample
     * data
     *
     * @param templateRequirements Description of what the template should look
     * like
     * @param sampleData Sample JSON data to structure the template around
     * @param templateStyle Style preferences (modern, classic, minimal, etc.)
     * @return Complete LaTeX template string
     */
    public String generateLatexTemplate(String templateRequirements, JSONObject sampleData, String templateStyle) {
        try {
            String prompt = buildTemplateGenerationPrompt(templateRequirements, sampleData, templateStyle);
            String response = query(prompt);

            // Extract LaTeX code from response
            return extractLatexFromResponse(response);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Enhance an existing LaTeX template with AI suggestions
     *
     * @param existingTemplate Current LaTeX template
     * @param enhancementRequests What improvements to make
     * @param sampleData Sample data to test against
     * @return Enhanced LaTeX template
     */
    public String enhanceLatexTemplate(String existingTemplate, String enhancementRequests, JSONObject sampleData) {
        try {
            String prompt = buildTemplateEnhancementPrompt(existingTemplate, enhancementRequests, sampleData);
            String response = query(prompt);

            return extractLatexFromResponse(response);

        } catch (Exception e) {
            throw new RuntimeException("Failed to enhance LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a template schema (JSON) that defines the structure for a LaTeX
     * template
     *
     * @param templateRequirements Description of the CV structure needed
     * @param includeOptionalFields Whether to include optional fields
     * @return JSONObject representing the template schema
     */
    public JSONObject generateTemplateSchema(String templateRequirements, boolean includeOptionalFields) {
        try {
            String prompt = buildSchemaGenerationPrompt(templateRequirements, includeOptionalFields);
            String response = query(prompt);

            String cleanedResponse = extractJsonFromResponse(response);
            return new JSONObject(cleanedResponse);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate template schema: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze a LaTeX template and suggest improvements
     *
     * @param latexTemplate The template to analyze
     * @param analysisType Type of analysis (readability, design, structure,
     * etc.)
     * @return Analysis results and suggestions
     */
    public String analyzeLatexTemplate(String latexTemplate, String analysisType) {
        try {
            String prompt = buildTemplateAnalysisPrompt(latexTemplate, analysisType);
            return query(prompt);

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Build the prompt for CV data extraction
     */
    private String buildCVExtractionPrompt(String unstructuredText, String templateSchema, String jobDescription) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert CV/resume writer and data extraction specialist. ");
        prompt.append("Extract relevant professional information from the unstructured text and organize it ");
        prompt.append("according to the provided JSON schema. Return ONLY valid JSON that matches the schema exactly.\n\n");

        prompt.append("TEMPLATE SCHEMA:\n")
                .append(templateSchema)
                .append("\n\n");

        prompt.append("UNSTRUCTURED INPUT TEXT:\n")
                .append(unstructuredText)
                .append("\n\n");

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("TARGET JOB DESCRIPTION (tailor the CV for this role):\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Extract all relevant professional information from the input text\n");
        prompt.append("2. Organize it according to the provided schema\n");
        prompt.append("3. If a job description is provided, emphasize relevant skills and experiences\n");
        prompt.append("4. Use professional language and formatting\n");
        prompt.append("5. Fill in all required fields, use empty strings/arrays for missing information\n");
        prompt.append("6. Ensure dates are in consistent format (YYYY-MM or YYYY)\n");
        prompt.append("7. Return ONLY the JSON object, no additional text or markdown\n\n");
        prompt.append("JSON OUTPUT:");

        return prompt.toString();
    }

    /**
     * Build the prompt for LaTeX template generation
     */
    private String buildTemplateGenerationPrompt(String requirements, JSONObject sampleData, String style) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert LaTeX developer and CV/resume designer. ");
        prompt.append("Create a complete, professional LaTeX template for a CV/resume based on the requirements provided.\n\n");

        prompt.append("REQUIREMENTS:\n")
                .append(requirements)
                .append("\n\n");

        prompt.append("STYLE PREFERENCE: ").append(style != null ? style : "professional").append("\n\n");

        if (sampleData != null) {
            prompt.append("SAMPLE DATA STRUCTURE (design template to work with this structure):\n")
                    .append(sampleData.toString(2))
                    .append("\n\n");
        }

        prompt.append("TEMPLATE REQUIREMENTS:\n");
        prompt.append("1. Use standard LaTeX document class (article or similar)\n");
        prompt.append("2. Include necessary packages for formatting, fonts, and layout\n");
        prompt.append("3. Create placeholder variables using {{variable_name}} syntax\n");
        prompt.append("4. For arrays/lists, use {{#section_name}} content {{/section_name}} syntax\n");
        prompt.append("5. Within array sections, use {{field_name}} for object properties\n");
        prompt.append("6. Use {{.}} for simple string arrays\n");
        prompt.append("7. Include proper spacing, margins, and professional formatting\n");
        prompt.append("8. Add comments to explain major sections\n");
        prompt.append("9. Ensure the template compiles with standard LaTeX distributions\n");
        prompt.append("10. Make it ATS-friendly while maintaining good visual design\n\n");

        prompt.append("Return ONLY the complete LaTeX template code, no explanations or markdown formatting.\n\n");
        prompt.append("LATEX TEMPLATE:");

        return prompt.toString();
    }

    /**
     * Build the prompt for template enhancement
     */
    private String buildTemplateEnhancementPrompt(String existingTemplate, String enhancements, JSONObject sampleData) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert LaTeX developer. Enhance the provided LaTeX CV template based on the requirements.\n\n");

        prompt.append("CURRENT TEMPLATE:\n")
                .append(existingTemplate)
                .append("\n\n");

        prompt.append("ENHANCEMENT REQUESTS:\n")
                .append(enhancements)
                .append("\n\n");

        if (sampleData != null) {
            prompt.append("SAMPLE DATA FOR TESTING:\n")
                    .append(sampleData.toString(2))
                    .append("\n\n");
        }

        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Maintain all existing placeholder syntax ({{variable_name}})\n");
        prompt.append("2. Keep the template compatible with the data structure\n");
        prompt.append("3. Improve the requested aspects while preserving functionality\n");
        prompt.append("4. Ensure the enhanced template still compiles correctly\n");
        prompt.append("5. Add comments for new sections or major changes\n");
        prompt.append("6. Maintain professional appearance and ATS compatibility\n\n");

        prompt.append("Return ONLY the enhanced LaTeX template code.\n\n");
        prompt.append("ENHANCED LATEX TEMPLATE:");

        return prompt.toString();
    }

    /**
     * Build the prompt for schema generation
     */
    private String buildSchemaGenerationPrompt(String requirements, boolean includeOptional) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a data structure expert. Create a JSON schema for a CV/resume template based on the requirements.\n\n");

        prompt.append("REQUIREMENTS:\n")
                .append(requirements)
                .append("\n\n");

        prompt.append("SCHEMA REQUIREMENTS:\n");
        prompt.append("1. Create a flat JSON object structure with string and array fields\n");
        prompt.append("2. Use descriptive field names that work as LaTeX placeholders\n");
        prompt.append("3. For work experience, education, etc., use arrays of objects\n");
        prompt.append("4. Include standard CV sections: personal info, summary, experience, education, skills\n");
        prompt.append("5. Add specialized sections based on requirements\n");
        if (includeOptional) {
            prompt.append("6. Include optional fields for comprehensive data capture\n");
        }
        prompt.append("7. Use consistent naming conventions (camelCase or snake_case)\n");
        prompt.append("8. Ensure field names are LaTeX-safe (no special characters)\n\n");

        prompt.append("Return ONLY the JSON schema object, no explanations.\n\n");
        prompt.append("JSON SCHEMA:");

        return prompt.toString();
    }

    /**
     * Build the prompt for template analysis
     */
    private String buildTemplateAnalysisPrompt(String template, String analysisType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a LaTeX and CV design expert. Analyze the provided template and provide detailed feedback.\n\n");

        prompt.append("TEMPLATE TO ANALYZE:\n")
                .append(template)
                .append("\n\n");

        prompt.append("ANALYSIS TYPE: ").append(analysisType).append("\n\n");

        prompt.append("Please analyze the template for:\n");
        prompt.append("1. Code quality and LaTeX best practices\n");
        prompt.append("2. Visual design and professional appearance\n");
        prompt.append("3. ATS compatibility and readability\n");
        prompt.append("4. Template structure and maintainability\n");
        prompt.append("5. Potential compilation issues\n");
        prompt.append("6. Suggestions for improvement\n\n");

        prompt.append("Provide specific, actionable feedback with examples where possible.\n\n");
        prompt.append("ANALYSIS:");

        return prompt.toString();
    }

    /**
     * Extract JSON from the AI response, removing any markdown or extra text
     */
    private String extractJsonFromResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // Find the first { and last } to extract JSON
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned.trim();
    }

    /**
     * Attempt to parse a response that should contain a JSON array
     *
     * @param response The response from the OpenAI API
     * @return A JSONArray parsed from the response or null if parsing fails
     */
    public static JSONArray parseJsonArrayResponse(String response) {
        try {
            String contentTrimmed = response.trim();
            int startIndex = contentTrimmed.indexOf('[');
            int endIndex = contentTrimmed.lastIndexOf(']');

            if (startIndex != -1 && endIndex != -1) {
                String jsonArrayString = contentTrimmed.substring(startIndex, endIndex + 1);
                return new JSONArray(jsonArrayString);
            }
        } catch (JSONException e) {
            System.err.println("Failed to parse response as JSON array: " + e.getMessage());
        }
        return null;
    }

    /**
     * Utility method to convert a JSONArray to a String array
     *
     * @param jsonArray The JSONArray to convert
     * @return A String array containing the elements of the JSONArray
     * @throws JSONException If there's an error accessing elements in the
     * JSONArray
     */
    public static String[] jsonArrayToStringArray(JSONArray jsonArray) throws JSONException {
        if (jsonArray == null) {
            return new String[0];
        }

        String[] result = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            result[i] = jsonArray.getString(i);
        }
        return result;
    }

    public String generateDirectLatexCV(String unstructuredText, String referenceTemplate, String jobDescription) {
        try {
            String prompt = buildDirectLatexGenerationPrompt(unstructuredText, referenceTemplate, jobDescription);
            String response = query(prompt);
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX CV: " + e.getMessage(), e);
        }
    }

    private String buildDirectLatexGenerationPrompt(String unstructuredText, String referenceTemplate, String jobDescription) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert CV/resume writer and LaTeX developer. ");
        prompt.append("Create a complete, professional LaTeX CV document based on the unstructured CV text provided. ");

        if (referenceTemplate != null) {
            prompt.append("Use the reference template as inspiration for style and structure, but feel free to enhance and modify it as needed. ");
        } else {
            prompt.append("Create a clean, professional, and modern design. ");
        }

        prompt.append("Return ONLY a complete, compilable LaTeX document that includes all necessary packages and definitions.\n\n");

        prompt.append("UNSTRUCTURED CV TEXT:\n")
                .append(unstructuredText)
                .append("\n\n");

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("TARGET JOB DESCRIPTION (tailor the CV for this role):\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        if (referenceTemplate != null) {
            prompt.append("REFERENCE TEMPLATE:\n")
                    .append(referenceTemplate)
                    .append("\n\n");
        }

        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Extract all relevant professional information from the input text\n");
        prompt.append("2. Create a complete, compilable LaTeX document that presents this information professionally\n");
        if (referenceTemplate != null) {
            prompt.append("3. You may use the reference template as inspiration, but feel free to improve it\n");
        } else {
            prompt.append("3. Create a clean, modern, and professional design\n");
        }
        prompt.append("4. Include all necessary LaTeX packages and styling\n");
        prompt.append("5. If a job description is provided, emphasize relevant skills and experiences\n");
        prompt.append("6. Use professional language and formatting\n");
        prompt.append("7. Ensure dates are in consistent format\n");
        prompt.append("8. Return ONLY the complete LaTeX document, no additional text or markdown\n\n");

        prompt.append("COMPLETE LATEX CV:");

        return prompt.toString();
    }

    private String extractLatexFromResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```latex") || cleaned.startsWith("```tex")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
