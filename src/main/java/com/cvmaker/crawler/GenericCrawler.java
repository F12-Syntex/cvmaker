package com.cvmaker.crawler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;

/**
 * AI-powered generic form filling crawler. Automatically analyzes and fills any
 * application form using AI.
 */
public class GenericCrawler extends AbstractJobCrawler {

    private PageVisualizer visualizer;
    private AiService aiService;
    private Map<String, String> formContext;
    private static final int MAX_RETRIES = 3;

    public GenericCrawler() throws Exception {
        super();
        initializeAI();
    }

    public GenericCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
        initializeAI();
    }

    private void initializeAI() {
        this.aiService = new AiService(LLMModel.GPT_4_1_MINI, 0.7); // Higher temperature for more creative responses
        this.formContext = new HashMap<>();
    }

    @Override
    public String getCrawlerName() {
        return "AI-Powered Generic Form Filler";
    }

    @Override
    public void processJobsAndApply() {
        try {
            System.out.println("\n🤖 AI-Powered Form Filler Ready!");
            System.out.println("Navigate to the form you want to fill and press Enter...");

            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            System.out.println("🔍 Analyzing form structure and context...");
            analyzeFormWithAI();
            fillFormAutomatically();

        } catch (Exception e) {
            System.out.println("❌ Error during form processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void analyzeFormWithAI() {
        try {
            visualizer.visualizeAction("Analyzing form context with AI");

            // Extract form structure and context
            String formStructure = extractFormStructure();
            String pageContext = extractPageContext();

            // Prepare AI analysis prompt
            String analysisPrompt = createAnalysisPrompt(formStructure, pageContext);

            System.out.println("🧠 Asking AI to analyze the form...");
            String aiAnalysis = aiService.query(analysisPrompt);

            // Parse AI response and store context
            parseAndStoreAIAnalysis(aiAnalysis);

            System.out.println("✅ Form analysis complete!");

        } catch (Exception e) {
            System.out.println("❌ Error during AI analysis: " + e.getMessage());
        }
    }

    private String extractFormStructure() {
        StringBuilder structure = new StringBuilder();

        try {
            // Extract all form fields and their attributes
            Locator formFields = page.locator("input, select, textarea");
            int fieldCount = formFields.count();

            structure.append("Form Fields Found:\n");

            for (int i = 0; i < fieldCount; i++) {
                Locator field = formFields.nth(i);
                if (field.isVisible()) {
                    Map<String, String> fieldInfo = extractFieldInfo(field);
                    structure.append(formatFieldInfo(fieldInfo)).append("\n");
                }
            }

            // Extract form sections and headers
            Locator sections = page.locator("form h1, form h2, form h3, form legend, form fieldset");
            int sectionCount = sections.count();

            if (sectionCount > 0) {
                structure.append("\nForm Sections:\n");
                for (int i = 0; i < sectionCount; i++) {
                    structure.append("- ").append(sections.nth(i).textContent()).append("\n");
                }
            }

        } catch (Exception e) {
            System.out.println("Warning: Error extracting form structure: " + e.getMessage());
        }

        return structure.toString();
    }

    private Map<String, String> extractFieldInfo(Locator field) {
        Map<String, String> info = new HashMap<>();
        try {
            String type = field.getAttribute("type");
            String name = field.getAttribute("name");
            String id = field.getAttribute("id");
            String placeholder = field.getAttribute("placeholder");
            String required = field.getAttribute("required");
            String label = findAssociatedLabel(field);

            info.put("type", type != null ? type : "text");
            info.put("name", name != null ? name : "");
            info.put("id", id != null ? id : "");
            info.put("placeholder", placeholder != null ? placeholder : "");
            info.put("required", required != null ? "true" : "false");
            info.put("label", label != null ? label : "");

        } catch (Exception e) {
            // Continue with partial info
        }
        return info;
    }

    private String findAssociatedLabel(Locator field) {
        try {
            String id = field.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                Locator label = page.locator("label[for='" + id + "']");
                if (label.count() > 0) {
                    return label.first().textContent();
                }
            }

            // Try finding label by proximity
            Locator nearestLabel = field.locator("xpath=./preceding::label[1]");
            if (nearestLabel.count() > 0) {
                return nearestLabel.first().textContent();
            }
        } catch (Exception e) {
            // Continue without label
        }
        return null;
    }

    private String extractPageContext() {
        StringBuilder context = new StringBuilder();

        try {
            // Get page title and URL
            context.append("Page Title: ").append(page.title()).append("\n");
            context.append("URL: ").append(page.url()).append("\n\n");

            // Extract job description if present
            String[] jobDescSelectors = {
                "[class*='job-description']",
                "[class*='description']",
                "article",
                ".job-details",
                "#job-details"
            };

            for (String selector : jobDescSelectors) {
                try {
                    Locator desc = page.locator(selector).first();
                    if (desc != null && desc.isVisible()) {
                        context.append("Job Description:\n")
                                .append(desc.textContent())
                                .append("\n\n");
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

        } catch (Exception e) {
            System.out.println("Warning: Error extracting page context: " + e.getMessage());
        }

        return context.toString();
    }

    private String createAnalysisPrompt(String formStructure, String pageContext) {
        return String.format(
                """
            Analyze this job application form and provide appropriate values for each field.
            
            Page Context:
            %s
            
            Form Structure:
            %s
            
            Please provide a JSON response with:
            1. Field values for each input field
            2. Analysis of required vs optional fields
            3. Recommended approach for any complex fields
            4. Special handling instructions for any unusual fields
            5. Suggested responses for any text areas or essay questions
            
            Format the response as valid JSON with clear field mappings.
            """,
                pageContext, formStructure
        );
    }

    private void parseAndStoreAIAnalysis(String aiAnalysis) {
        try {
            // Extract JSON from AI response
            String jsonStr = aiAnalysis.substring(
                    aiAnalysis.indexOf("{"),
                    aiAnalysis.lastIndexOf("}") + 1
            );

            // Parse and store the field mappings
            Map<String, Object> analysisMap = new ObjectMapper().readValue(jsonStr, Map.class);

            // Store the analyzed values
            formContext.clear();
            for (Map.Entry<String, Object> entry : analysisMap.entrySet()) {
                if (entry.getValue() != null) {
                    formContext.put(entry.getKey(), entry.getValue().toString());
                }
            }

        } catch (Exception e) {
            System.out.println("Warning: Error parsing AI analysis: " + e.getMessage());
        }
    }

    private void fillFormAutomatically() {
        try {
            System.out.println("🤖 Starting automatic form filling...");
            visualizer.visualizeAction("Starting automatic form filling");

            // Find all form fields
            List<FormField> fields = identifyFormFields();

            int successCount = 0;
            int totalFields = 0;

            // Process each field
            for (FormField field : fields) {
                if (field.isVisible() && !field.isReadOnly()) {
                    totalFields++;
                    if (fillFieldWithAI(field)) {
                        successCount++;
                    }
                }
            }

            System.out.printf("✅ Form filling complete! Filled %d out of %d fields successfully.\n",
                    successCount, totalFields);

            System.out.println("\nPlease review the filled form and press Enter to submit...");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            if (submitForm()) {
                System.out.println("✅ Form submitted successfully!");
            } else {
                System.out.println("❌ Form submission failed. Please submit manually.");
            }

        } catch (Exception e) {
            System.out.println("❌ Error during form filling: " + e.getMessage());
        }
    }

    private boolean fillFieldWithAI(FormField field) {
        try {
            // Get AI-suggested value for this field
            String value = getAIValueForField(field);

            if (value == null || value.isEmpty()) {
                return false;
            }

            // Fill the field based on its type
            switch (field.getType().toLowerCase()) {
                case "text":
                case "email":
                case "tel":
                case "number":
                    field.getElement().fill(value);
                    break;

                case "select":
                    handleSelectField(field, value);
                    break;

                case "textarea":
                    field.getElement().fill(value);
                    break;

                case "radio":
                    handleRadioField(field, value);
                    break;

                case "checkbox":
                    handleCheckboxField(field, value);
                    break;

                default:
                    return false;
            }

            // Verify the field was filled
            return verifyFieldValue(field, value);

        } catch (Exception e) {
            System.out.println("Warning: Error filling field " + field.getIdentifier() + ": " + e.getMessage());
            return false;
        }
    }

    private String getAIValueForField(FormField field) {
        try {
            // Create field-specific prompt
            String fieldPrompt = String.format(
                    """
                Suggest an appropriate value for this form field:
                Type: %s
                Label: %s
                Identifier: %s
                Required: %s
                Current Context: %s
                
                Provide only the value, no explanation.
                """,
                    field.getType(),
                    field.getLabel(),
                    field.getIdentifier(),
                    field.isRequired(),
                    formContext.toString()
            );

            // Get AI suggestion
            String suggestion = aiService.query(fieldPrompt);

            // Clean and validate the suggestion
            return cleanAISuggestion(suggestion, field.getType());

        } catch (Exception e) {
            System.out.println("Warning: Error getting AI value for field: " + e.getMessage());
            return null;
        }
    }

    private String cleanAISuggestion(String suggestion, String fieldType) {
        if (suggestion == null || suggestion.isEmpty()) {
            return null;
        }

        // Remove any markdown formatting
        suggestion = suggestion.replaceAll("```.*?```", "").trim();

        // Clean based on field type
        switch (fieldType.toLowerCase()) {
            case "email":
                return suggestion.matches(".*@.*\\..*") ? suggestion : null;
            case "tel":
                return suggestion.replaceAll("[^0-9+\\-()]", "");
            case "number":
                return suggestion.replaceAll("[^0-9\\-.]", "");
            default:
                return suggestion;
        }
    }

    private boolean submitForm() {
        try {
            String[] submitSelectors = {
                "button[type='submit']",
                "input[type='submit']",
                "button:has-text('Submit')",
                "button:has-text('Apply')",
                ".submit-button",
                "#submit-button"
            };

            for (String selector : submitSelectors) {
                try {
                    Locator submitButton = page.locator(selector).first();
                    if (submitButton != null && submitButton.isVisible()) {
                        submitButton.click();
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void handleSelectField(FormField field, String value) {
        try {
            Locator select = field.getElement();

            // Try exact match first
            try {
                select.selectOption(value);
                return;
            } catch (Exception e) {
                // Continue to other methods
            }

            // Try case-insensitive match
            Locator options = select.locator("option");
            int count = options.count();

            for (int i = 0; i < count; i++) {
                String optionText = options.nth(i).textContent().toLowerCase();
                String optionValue = options.nth(i).getAttribute("value");
                if (optionText.contains(value.toLowerCase()) && optionValue != null) {
                    select.selectOption(optionValue);
                    return;
                }
            }

            // If no match found, select first non-empty option
            for (int i = 0; i < count; i++) {
                String optionValue = options.nth(i).getAttribute("value");
                if (optionValue != null && !optionValue.isEmpty()) {
                    select.selectOption(optionValue);
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("Warning: Error handling select field: " + e.getMessage());
        }
    }

    private void handleRadioField(FormField field, String value) {
        try {
            String name = field.getName();
            if (name == null || name.isEmpty()) {
                return;
            }

            // Find all radio buttons in the same group
            Locator radioGroup = field.getElement().page().locator("input[type='radio'][name='" + name + "']");
            int count = radioGroup.count();

            for (int i = 0; i < count; i++) {
                Locator radio = radioGroup.nth(i);
                String radioValue = radio.getAttribute("value");
                String radioLabel = findAssociatedLabel(radio);

                if ((radioValue != null && radioValue.toLowerCase().contains(value.toLowerCase()))
                        || (radioLabel != null && radioLabel.toLowerCase().contains(value.toLowerCase()))) {
                    radio.check();
                    return;
                }
            }

            // If no match found, select first option
            if (count > 0) {
                radioGroup.first().check();
            }
        } catch (Exception e) {
            System.out.println("Warning: Error handling radio field: " + e.getMessage());
        }
    }

    private void handleCheckboxField(FormField field, String value) {
        try {
            boolean shouldCheck = Boolean.parseBoolean(value)
                    || value.toLowerCase().contains("yes")
                    || value.toLowerCase().contains("true")
                    || value.toLowerCase().contains("accept");

            if (shouldCheck) {
                field.getElement().check();
            } else {
                field.getElement().uncheck();
            }
        } catch (Exception e) {
            System.out.println("Warning: Error handling checkbox field: " + e.getMessage());
        }
    }

    private boolean verifyFieldValue(FormField field, String expectedValue) {
        try {
            String actualValue = field.getElement().inputValue();
            return actualValue != null && !actualValue.isEmpty();
        } catch (Exception e) {
            return false;

        }
    }

    private List<FormField> identifyFormFields() {
        List<FormField> fields = new ArrayList<>();

        try {
            // Find all interactive form elements
            String[] selectors = {
                "input:not([type='hidden'])",
                "select",
                "textarea",
                "button[type='submit']"
            };

            for (String selector : selectors) {
                Locator elements = page.locator(selector);
                int count = elements.count();

                for (int i = 0; i < count; i++) {
                    try {
                        Locator element = elements.nth(i);
                        if (element.isVisible()) {
                            FormField field = new FormField(element);

                            // Only add if it's a valid field
                            if (isValidFormField(field)) {
                                fields.add(field);
                                if (crawlerConfig.isDebugMode()) {
                                    System.out.println("Found field: " + formatFieldInfo(extractFieldInfo(element)));
                                }
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }

            // Sort fields by their position on the page
            Collections.sort(fields, (a, b) -> {
                try {
                    return Integer.compare(
                            getElementPosition(a.getElement()),
                            getElementPosition(b.getElement())
                    );
                } catch (Exception e) {
                    return 0;
                }
            });

        } catch (Exception e) {
            System.out.println("Error identifying form fields: " + e.getMessage());
        }

        return fields;
    }

    private boolean isValidFormField(FormField field) {
        if (field == null || field.getElement() == null) {
            return false;
        }

        try {
            // Check if the field is actually interactive
            if (!field.isVisible()) {
                return false;
            }

            // Exclude hidden or non-interactive elements
            String type = field.getType().toLowerCase();
            if (type.equals("hidden") || type.equals("submit") || type.equals("reset")) {
                return false;
            }

            // Ensure the field has some form of identifier
            String identifier = field.getIdentifier();
            return identifier != null && !identifier.isEmpty() && !identifier.equals("unnamed_field");

        } catch (Exception e) {
            return false;
        }
    }

    private int getElementPosition(Locator element) {
        try {
            // Get element position relative to viewport
            BoundingBox box = element.boundingBox();
            return box != null ? (int) box.y : Integer.MAX_VALUE;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private String formatFieldInfo(Map<String, String> fieldInfo) {
        StringBuilder formatted = new StringBuilder();

        // Format the field information in a readable way
        formatted.append("Field: {");

        if (fieldInfo.containsKey("type")) {
            formatted.append("\n  Type: ").append(fieldInfo.get("type"));
        }

        if (fieldInfo.containsKey("name")) {
            formatted.append("\n  Name: ").append(fieldInfo.get("name"));
        }

        if (fieldInfo.containsKey("id")) {
            formatted.append("\n  ID: ").append(fieldInfo.get("id"));
        }

        if (fieldInfo.containsKey("label")) {
            formatted.append("\n  Label: ").append(fieldInfo.get("label"));
        }

        if (fieldInfo.containsKey("placeholder")) {
            formatted.append("\n  Placeholder: ").append(fieldInfo.get("placeholder"));
        }

        if (fieldInfo.containsKey("required")) {
            formatted.append("\n  Required: ").append(fieldInfo.get("required"));
        }

        formatted.append("\n}");
        return formatted.toString();
    }

    private Map<String, String> extractFieldAttributes(Locator element) {
        Map<String, String> attributes = new HashMap<>();

        try {
            // Common attributes to extract
            String[] commonAttributes = {
                "type", "name", "id", "placeholder", "value", "required",
                "readonly", "disabled", "maxlength", "min", "max", "pattern"
            };

            for (String attr : commonAttributes) {
                String value = element.getAttribute(attr);
                if (value != null) {
                    attributes.put(attr, value);
                }
            }

            // Get element tag name
            String tagName = element.evaluate("node => node.tagName").toString().toLowerCase();
            attributes.put("tagName", tagName);

            // Get associated label text
            String label = findAssociatedLabel(element);
            if (label != null) {
                attributes.put("label", label);
            }

            // For select elements, get options
            if (tagName.equals("select")) {
                List<String> options = new ArrayList<>();
                Locator optionElements = element.locator("option");
                int optionCount = optionElements.count();

                for (int i = 0; i < optionCount; i++) {
                    String optionText = optionElements.nth(i).textContent();
                    if (optionText != null && !optionText.trim().isEmpty()) {
                        options.add(optionText.trim());
                    }
                }

                if (!options.isEmpty()) {
                    attributes.put("options", String.join("|", options));
                }
            }

        } catch (Exception e) {
            System.out.println("Warning: Error extracting field attributes: " + e.getMessage());
        }

        return attributes;
    }

    private boolean isInputField(FormField field) {
        if (field == null || field.getType() == null) {
            return false;
        }

        String type = field.getType().toLowerCase();
        return type.equals("text") || type.equals("email") || type.equals("tel")
                || type.equals("number") || type.equals("password") || type.equals("url")
                || type.equals("search") || type.equals("date") || type.equals("datetime-local")
                || type.equals("month") || type.equals("week") || type.equals("time")
                || type.equals("color");
    }

    private boolean isSelectionField(FormField field) {
        if (field == null || field.getType() == null) {
            return false;
        }

        String type = field.getType().toLowerCase();
        return type.equals("select") || type.equals("radio") || type.equals("checkbox");
    }

    private boolean isTextArea(FormField field) {
        if (field == null || field.getElement() == null) {
            return false;
        }

        try {
            String tagName = field.getElement().evaluate("node => node.tagName").toString().toLowerCase();
            return tagName.equals("textarea");
        } catch (Exception e) {
            return false;
        }
    }
}
