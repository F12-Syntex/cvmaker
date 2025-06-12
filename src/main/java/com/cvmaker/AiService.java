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
     * @param model       The OpenAI model to use
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
     * @param templateSchema   The JSON schema for the template
     * @param jobDescription   Optional job description for tailoring
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
     * @throws JSONException If there's an error accessing elements in the JSONArray
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
}