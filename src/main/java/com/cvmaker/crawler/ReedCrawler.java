package com.cvmaker.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;

import com.cvmaker.CVGenerator;
import com.cvmaker.TemplateLoader;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.itextpdf.io.exceptions.IOException;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

public class ReedCrawler {

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    // CV Generation components
    private TemplateLoader templateLoader;
    private AiService aiService;
    private CVGenerator cvGenerator;

    // Configuration
    private static final String USER_DATA_FILE = "data/userdata.txt";
    private static final String CV_PROMPT_FILE = "data/cv_prompt.txt";
    private static final String COVER_LETTER_PROMPT_FILE = "data/cover_letter_prompt.txt";
    private static final String TEMP_JOB_FILE = "temp/temp_job_content.txt";
    private static final String OUTPUT_DIR = "temp/generated_cvs";

    // Application tracking
    private int applicationsSubmitted = 0;
    private int maxApplications = 10; // Set your desired limit
    private int jobsChecked = 0; // Track how many jobs we've checked
    private int easyApplyJobsFound = 0; // Track Easy Apply jobs found

    public ReedCrawler() {
        initializeCVGeneration();
    }

    private void initializeCVGeneration() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            this.templateLoader = new TemplateLoader(Paths.get("templates"));
            this.aiService = new AiService(LLMModel.GPT_4_1_MINI);
            this.cvGenerator = new CVGenerator(templateLoader, aiService);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setupBrowser() {
        playwright = Playwright.create();

        try {
            java.nio.file.Path userDataDir = java.nio.file.Paths.get("playwright-session");
            this.context = playwright.chromium().launchPersistentContext(
                    userDataDir,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(false)
                            .setSlowMo(1000)
                            .setViewportSize(1920, 1080)
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .setJavaScriptEnabled(true)
                            .setArgs(Arrays.asList(
                                    "--disable-blink-features=AutomationControlled",
                                    "--disable-dev-shm-usage",
                                    "--no-sandbox",
                                    "--disable-web-security"
                            ))
                            .setExtraHTTPHeaders(Map.of(
                                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                                    "Accept-Language", "en-US,en;q=0.9"
                            ))
            );

            context.addInitScript("() => {"
                    + "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                    + "delete navigator.__proto__.webdriver;"
                    + "window.chrome = { runtime: {} };"
                    + "}");

            this.page = context.newPage();
            page.setDefaultTimeout(60000);
            page.setDefaultNavigationTimeout(60000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openForLogin() {
        try {
            page.navigate("https://www.reed.co.uk/");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);

            System.out.println("Login to Reed.co.uk if needed, then press ENTER...");
            System.in.read();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processJobsAndApply() {
        try {
            // Search for software jobs
            page.navigate("https://www.reed.co.uk/");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            Locator searchInput = page.locator("input[name='keywords']").first();
            searchInput.click();
            searchInput.fill("web development");
            searchInput.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);

            System.out.println("Starting job search - looking for Easy Apply jobs only...");

            // Process jobs one by one
            while (applicationsSubmitted < maxApplications) {
                JobInfo job = findAndClickNextEasyApplyJob();

                if (job == null) {
                    System.out.println("No more Easy Apply jobs found or reached application limit");
                    break;
                }

                // Wait for job card to update with description
                page.waitForTimeout(2000);

                // Extract and print job description from the updated job card
                printJobDescriptionFromCard();

                // Extract job content and generate CV
                Path generatedCvPath = generateCVForJob(job);

                if (generatedCvPath != null && Files.exists(generatedCvPath)) {
                    // Apply for the job with the generated CV using STANDARD process
                    boolean applicationSuccess = applyForJobStandardProcess(generatedCvPath);

                    if (applicationSuccess) {
                        applicationsSubmitted++;
                        System.out.println("Successfully applied to job " + applicationsSubmitted + "/" + maxApplications);
                    }
                } else {
                    System.out.println("Failed to generate CV for job: " + job.title);
                }

                // Wait between applications to avoid being flagged
                page.waitForTimeout(5000);
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("JOB APPLICATION SESSION COMPLETED");
            System.out.println("=".repeat(60));
            System.out.println("Total jobs checked: " + jobsChecked);
            System.out.println("Easy Apply jobs found: " + easyApplyJobsFound);
            System.out.println("Applications submitted: " + applicationsSubmitted);
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JobInfo findAndClickNextEasyApplyJob() {
        try {
            // Try all possible job card selectors
            String[] jobSelectors = {
                ".job-card_jobCard__MkcJD",
                "[class*='job-card_jobCard']",
                ".job-result",
                ".card.job-card",
                ".job-card",
                "article[data-qa='job-result']",
                "[data-qa*='job']",
                ".job-result-card"
            };

            System.out.println("Looking for Easy Apply jobs...");

            for (String selector : jobSelectors) {
                try {
                    Locator elements = page.locator(selector);
                    int count = elements.count();

                    if (count > 0) {
                        System.out.println("Found " + count + " job elements with selector: " + selector);

                        // Check each job card for Easy Apply button
                        for (int i = jobsChecked; i < count; i++) {
                            try {
                                Locator element = elements.nth(i);
                                jobsChecked = i + 1; // Update counter

                                if (element.isVisible()) {
                                    // Check if this job card has an Easy Apply button
                                    if (hasEasyApplyButton(element)) {
                                        easyApplyJobsFound++;

                                        JobInfo job = extractJobInfo(element, i);

                                        System.out.println("✅ Found Easy Apply job: " + job.title + " (" + easyApplyJobsFound + " Easy Apply jobs found so far)");

                                        // Click on the job card
                                        element.scrollIntoViewIfNeeded();
                                        page.waitForTimeout(1000);

                                        element.click();
                                        page.waitForTimeout(2000); // Wait for card to update

                                        System.out.println("Successfully clicked Easy Apply job: " + job.title);
                                        return job;
                                    } else {
                                        JobInfo job = extractJobInfo(element, i);
                                        System.out.println("❌ Skipping job (no Easy Apply): " + job.title);
                                    }
                                }

                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }

                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No more Easy Apply jobs found after checking " + jobsChecked + " jobs");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean hasEasyApplyButton(Locator jobCard) {
        try {
            // Common selectors for Easy Apply buttons
            String[] easyApplySelectors = {
                "button:has-text('Easy Apply')",
                "a:has-text('Easy Apply')",
                "[data-qa*='easy-apply']",
                "[class*='easy-apply']",
                "button:has-text('Quick Apply')",
                "a:has-text('Quick Apply')",
                "[data-qa*='quick-apply']",
                "[class*='quick-apply']",
                ".easy-apply",
                ".quick-apply",
                "button[class*='Easy']",
                "a[class*='Easy']"
            };

            for (String selector : easyApplySelectors) {
                try {
                    Locator easyApplyButton = jobCard.locator(selector);
                    if (easyApplyButton.count() > 0 && easyApplyButton.first().isVisible()) {
                        System.out.println("   Found Easy Apply button with selector: " + selector);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Alternative approach: check if the card text contains "Easy Apply" or "Quick Apply"
            try {
                String cardText = jobCard.textContent();
                if (cardText != null) {
                    String lowerText = cardText.toLowerCase();
                    if (lowerText.contains("easy apply") || lowerText.contains("quick apply")) {
                        System.out.println("   Found Easy Apply text in card content");
                        return true;
                    }
                }
            } catch (Exception e) {
                // Continue with other checks
            }

            return false;

        } catch (Exception e) {
            System.out.println("Error checking for Easy Apply button: " + e.getMessage());
            return false;
        }
    }

    private JobInfo extractJobInfo(Locator element, int index) {
        JobInfo job = new JobInfo();

        try {
            String text = element.textContent();
            if (text != null && !text.trim().isEmpty()) {
                String[] lines = text.split("\n");
                job.title = lines[0].trim();
                if (lines.length > 1) {
                    job.company = lines[1].trim();
                }
            }
        } catch (Exception e) {
            job.title = "Job " + index;
            job.company = "Company";
        }

        return job;
    }

    // STANDARD APPLICATION PROCESS (not Easy Apply specific)
    private boolean applyForJobStandardProcess(Path cvPath) {
        try {
            System.out.println("Starting STANDARD job application process...");

            // Step 1: Click "Apply Now" button (standard process)
            if (!clickApplyNowButton()) {
                System.out.println("Could not find Apply Now button");
                return false;
            }

            page.waitForTimeout(2000);

            // Step 2: Click "Update" button (if exists)
            clickUpdateButton();
            page.waitForTimeout(1000);

            // Step 3: Click "Choose your own CV file"
            if (!clickChooseOwnCVButton()) {
                System.out.println("Could not find Choose your own CV file button");
                return false;
            }

            page.waitForTimeout(1000);

            // Step 4: Upload the generated CV file
            if (!uploadCVFile(cvPath)) {
                System.out.println("Failed to upload CV file");
                return false;
            }

            // Step 5: Wait for CV processing to finish
            waitForCVProcessing();

            // Step 6: Submit application
            if (!submitApplication()) {
                System.out.println("Failed to submit application");
                return false;
            }

            // Step 7: Handle confirmation dialog
            handleConfirmationDialog();

            System.out.println("Job application completed successfully!");
            return true;

        } catch (Exception e) {
            System.out.println("Error during job application: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean clickApplyNowButton() {
        String[] applySelectors = {
            "button:has-text('Apply Now')",
            "a:has-text('Apply Now')",
            "button:has-text('Apply')",
            "a:has-text('Apply')",
            "[data-qa*='apply']",
            "button[class*='apply']",
            "a[class*='apply']",
            ".apply-button",
            ".btn-apply"
        };

        for (String selector : applySelectors) {
            try {
                Locator applyButton = page.locator(selector).first();
                if (applyButton.isVisible()) {
                    System.out.println("Found Apply Now button with selector: " + selector);
                    applyButton.scrollIntoViewIfNeeded();
                    applyButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return false;
    }

    private void clickUpdateButton() {
        String[] updateSelectors = {
            "button:has-text('Update')",
            "a:has-text('Update')",
            "[data-qa*='update']",
            "button[class*='update']",
            ".update-button"
        };

        for (String selector : updateSelectors) {
            try {
                Locator updateButton = page.locator(selector).first();
                if (updateButton.isVisible()) {
                    System.out.println("Found Update button, clicking...");
                    updateButton.click();
                    return;
                }
            } catch (Exception e) {
                continue;
            }
        }

        System.out.println("No Update button found (this may be normal)");
    }

    private boolean clickChooseOwnCVButton() {
        String[] cvSelectors = {
            "button:has-text('Choose your own CV file')",
            "a:has-text('Choose your own CV file')",
            "button:has-text('Choose CV')",
            "button:has-text('Upload CV')",
            "[data-qa*='upload-cv']",
            "[data-qa*='choose-cv']",
            "button[class*='cv-upload']",
            "input[type='file'][accept*='pdf']",
            "label[for*='cv']",
            ".cv-upload-button"
        };

        for (String selector : cvSelectors) {
            try {
                Locator cvButton = page.locator(selector).first();
                if (cvButton.isVisible()) {
                    System.out.println("Found Choose CV button with selector: " + selector);
                    cvButton.scrollIntoViewIfNeeded();
                    cvButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return false;
    }

    private boolean uploadCVFile(Path cvPath) {
        try {
            // Look for file input elements
            String[] fileInputSelectors = {
                "input[type='file']",
                "input[accept*='pdf']",
                "input[name*='cv']",
                "input[id*='cv']",
                "input[class*='cv']"
            };

            for (String selector : fileInputSelectors) {
                try {
                    Locator fileInput = page.locator(selector).first();
                    if (fileInput.count() > 0) {
                        System.out.println("Found file input, uploading CV: " + cvPath.toAbsolutePath());
                        fileInput.setInputFiles(cvPath);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No file input found for CV upload");
            return false;

        } catch (Exception e) {
            System.out.println("Error uploading CV file: " + e.getMessage());
            return false;
        }
    }

    private void waitForCVProcessing() {
        try {
            System.out.println("Waiting for CV processing to complete...");

            // Wait for processing indicators to appear and disappear
            String[] processingSelectors = {
                ":has-text('CV processing')",
                ":has-text('Processing')",
                ":has-text('Uploading')",
                ".spinner",
                ".loading",
                "[class*='processing']"
            };

            // Wait for processing to start (optional)
            page.waitForTimeout(2000);

            // Wait for processing to complete
            for (String selector : processingSelectors) {
                try {
                    Locator processingElement = page.locator(selector).first();
                    if (processingElement.isVisible()) {
                        System.out.println("CV processing detected, waiting for completion...");
                        // Wait for the processing element to disappear
                        processingElement.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN).setTimeout(30000));
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Additional wait to ensure everything is ready
            page.waitForTimeout(3000);
            System.out.println("CV processing completed");

        } catch (Exception e) {
            System.out.println("Error waiting for CV processing: " + e.getMessage());
            // Continue anyway after a reasonable wait
            page.waitForTimeout(5000);
        }
    }

    private boolean submitApplication() {
        String[] submitSelectors = {
            "button:has-text('Submit Application')",
            "button:has-text('Submit')",
            "a:has-text('Submit Application')",
            "a:has-text('Submit')",
            "[data-qa*='submit']",
            "button[class*='submit']",
            ".submit-button",
            ".btn-submit"
        };

        for (String selector : submitSelectors) {
            try {
                Locator submitButton = page.locator(selector).first();
                if (submitButton.isVisible()) {
                    System.out.println("Found Submit button with selector: " + selector);
                    submitButton.scrollIntoViewIfNeeded();
                    submitButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        System.out.println("No Submit Application button found");
        return false;
    }

    private void handleConfirmationDialog() {
        try {
            page.waitForTimeout(2000);

            // Look for confirmation dialog with OK button
            String[] okSelectors = {
                "button:has-text('OK')",
                "button:has-text('Ok')",
                "button:has-text('Confirm')",
                "button:has-text('Yes')",
                "[data-qa*='confirm']",
                ".modal button:has-text('OK')",
                ".dialog button:has-text('OK')"
            };

            for (String selector : okSelectors) {
                try {
                    Locator okButton = page.locator(selector).first();
                    if (okButton.isVisible()) {
                        System.out.println("Found confirmation dialog, clicking OK...");
                        okButton.click();
                        page.waitForTimeout(2000);
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No confirmation dialog found");

        } catch (Exception e) {
            System.out.println("Error handling confirmation dialog: " + e.getMessage());
        }
    }

    private void printJobDescriptionFromCard() {
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DEBUG: JOB DESCRIPTION FROM UPDATED CARD");
            System.out.println("=".repeat(80));

            // Look for the updated job card with description
            String[] cardSelectors = {
                "article.card.job-card_jobCard__MkcJD",
                "article[class*='job-card_jobCard']",
                "article.job-card_jobCard__MkcJD",
                ".card.job-card_jobCard__MkcJD",
                "[class*='job-card_jobCard__MkcJD']",
                "article.card",
                "article[class*='job-card']"
            };

            boolean foundDescription = false;

            for (String cardSelector : cardSelectors) {
                try {
                    Locator jobCard = page.locator(cardSelector).first();
                    if (jobCard.isVisible()) {
                        String cardContent = jobCard.textContent();

                        if (cardContent != null && cardContent.length() > 200) { // Ensure it has substantial content
                            System.out.println("FOUND JOB CARD WITH SELECTOR: " + cardSelector);
                            System.out.println("CARD CONTENT LENGTH: " + cardContent.length() + " characters");

                            // Try to find description within the card
                            String[] descriptionSelectors = {
                                ".description",
                                ".job-description",
                                "[class*='description']",
                                ".job-details",
                                "[class*='details']",
                                ".content",
                                "p",
                                "div[class*='description']"
                            };

                            String description = null;
                            for (String descSelector : descriptionSelectors) {
                                try {
                                    Locator descElement = jobCard.locator(descSelector).first();
                                    if (descElement.isVisible()) {
                                        String descText = descElement.textContent();
                                        if (descText != null && descText.trim().length() > 50) {
                                            description = descText;
                                            System.out.println("FOUND DESCRIPTION WITH SELECTOR: " + descSelector);
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    continue;
                                }
                            }

                            if (description != null) {
                                System.out.println("DESCRIPTION CONTENT:");
                                System.out.println("-".repeat(40));
                                System.out.println(description.trim());
                                System.out.println("-".repeat(40));
                            } else {
                                System.out.println("NO SPECIFIC DESCRIPTION FOUND, SHOWING FULL CARD CONTENT:");
                                System.out.println("-".repeat(40));
                                // Show first 1500 characters to avoid overwhelming output
                                if (cardContent.length() > 1500) {
                                    System.out.println(cardContent.substring(0, 1500) + "...[TRUNCATED]");
                                } else {
                                    System.out.println(cardContent);
                                }
                                System.out.println("-".repeat(40));
                            }

                            foundDescription = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (!foundDescription) {
                System.out.println("NO UPDATED JOB CARD FOUND");

                // Fallback: show all article elements
                try {
                    Locator allArticles = page.locator("article");
                    int count = allArticles.count();
                    System.out.println("FOUND " + count + " ARTICLE ELEMENTS:");

                    for (int i = 0; i < Math.min(count, 3); i++) {
                        try {
                            Locator article = allArticles.nth(i);
                            String content = article.textContent();
                            System.out.println("ARTICLE " + i + " (" + content.length() + " chars): "
                                    + content.substring(0, Math.min(200, content.length())) + "...");
                        } catch (Exception e) {
                            continue;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error checking article elements: " + e.getMessage());
                }
            }

            System.out.println("=".repeat(80));
            System.out.println("END DEBUG: JOB DESCRIPTION FROM UPDATED CARD");
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.out.println("Error printing job description from card: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Path generateCVForJob(JobInfo job) {
        try {
            System.out.println("Generating CV...");

            // Extract description from the updated job card
            String jobContent = extractJobDescriptionFromCard();

            if (jobContent == null || jobContent.trim().isEmpty()) {
                System.out.println("No job content found, skipping CV generation");
                return null;
            }

            // Generate unique folder name using UUID
            String uuid = java.util.UUID.randomUUID().toString();
            String jobFolder = "temp/" + uuid;
            Path jobFolderPath = Paths.get(jobFolder);

            // Create the temp directory structure
            Files.createDirectories(jobFolderPath);

            System.out.println("Created job folder: " + jobFolderPath.toAbsolutePath());

            // Create a ConfigManager directly with the job content
            ConfigManager jobConfig = new ConfigManager(
                    page.url(), // Use the actual web page URL
                    USER_DATA_FILE,
                    CV_PROMPT_FILE,
                    COVER_LETTER_PROMPT_FILE
            );

            // Set the job description content directly
            jobConfig.setJobDescriptionContent(jobContent);

            // Generate clean job name for folder
            String cleanJobName = generateCleanJobName(job.title, job.company);

            // Generate CV directly using the CV generation methods
            generateCVDirectly(job, jobConfig, jobFolderPath.toString(), cleanJobName);

            Path cvPath = jobFolderPath.resolve("cv.pdf");
            if (Files.exists(cvPath)) {
                System.out.println("CV generated successfully in: " + cvPath.toAbsolutePath());
                return cvPath;
            } else {
                System.out.println("CV generation failed - file not found");
                return null;
            }

        } catch (Exception e) {
            System.out.println("Error generating CV: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void generateCVDirectly(JobInfo job, ConfigManager jobConfig, String outputDir, String cleanJobName) throws Exception {
        System.out.println("Generating CV for: " + job.title + " at " + job.company);

        // Load template if specified
        String referenceTemplate = null;
        if (jobConfig.getTemplateName() != null && !jobConfig.getTemplateName().isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadTex(jobConfig.getTemplateName());
            } catch (IOException e) {
                System.out.println("No reference template found, proceeding without template.");
            }
        }

        // Generate LaTeX with AI
        System.out.println("Generating CV LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCV(
                jobConfig.getUserDataContent(),
                referenceTemplate,
                jobConfig.getJobDescriptionContent(),
                jobConfig.getCvPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve("cv.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling CV to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, "cv.pdf");

        // Clean up intermediate files
        cleanupIntermediateFiles(outputDirPath, "cv");

        // Generate cover letter if enabled
        if (jobConfig.isGenerateCoverLetter()) {
            generateCoverLetterDirectly(job, jobConfig, outputDir);
        }

        System.out.println("CV generated: " + outputDirPath.resolve("cv.pdf").toAbsolutePath());
    }

    private void generateCoverLetterDirectly(JobInfo job, ConfigManager jobConfig, String outputDir) throws Exception {
        System.out.println("Generating cover letter for: " + job.title);

        // Load template
        String referenceTemplate = null;
        if (jobConfig.getTemplateName() != null && !jobConfig.getTemplateName().isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadCoverLetterTex(jobConfig.getTemplateName());
            } catch (IOException e) {
                System.out.println("No reference cover letter template found, proceeding without template.");
            }
        }

        // Generate LaTeX with AI
        System.out.println("Generating cover letter LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCoverLetter(
                jobConfig.getUserDataContent(),
                referenceTemplate,
                jobConfig.getJobDescriptionContent(),
                jobConfig.getCoverLetterPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve("cover_letter.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling cover letter to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, "cover_letter.pdf");

        // Clean up intermediate files
        cleanupIntermediateFiles(outputDirPath, "cover_letter");

        System.out.println("Cover letter generated: " + outputDirPath.resolve("cover_letter.pdf").toAbsolutePath());
    }

    private String generateCleanJobName(String jobTitle, String company) {
        String combined = (jobTitle + "_" + company)
                .replaceAll("[^a-zA-Z0-9\\-_]", "_")
                .replaceAll("_+", "_")
                .toLowerCase();

        if (combined.length() > 50) {
            combined = combined.substring(0, 50);
        }

        return combined;
    }

    private void compileLatexWithProgress(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        try {
            String texFileName = texFile.getFileName().toString();
            ProcessBuilder pb = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    texFileName
            );
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

            Process proc = pb.start();

            // Read the output to monitor progress
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            int pageCount = 0;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                // Track page processing
                if (line.contains("[") && line.matches(".*\\[\\d+.*")) {
                    pageCount++;
                    if (pageCount % 5 == 0) {
                        System.out.printf("   📄 Processing page %d...\n", pageCount);
                    }
                }
            }

            int exitCode = proc.waitFor();

            // Check if PDF was generated
            String pdfFileName = texFileName.replace(".tex", ".pdf");
            Path pdfPath = dir.resolve(pdfFileName);
            boolean pdfExists = Files.exists(pdfPath);

            if (exitCode != 0 && !pdfExists) {
                throw new RuntimeException("LaTeX compilation failed");
            }

            if (pdfExists) {
                if (!pdfFileName.equals(outputPdfName)) {
                    Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new RuntimeException("PDF file was not generated");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupIntermediateFiles(Path outputDir, String baseName) {
        String[] extensions = {".tex", ".log", ".aux", ".out", ".fdb_latexmk", ".fls", ".synctex.gz"};

        for (String ext : extensions) {
            try {
                Path file = outputDir.resolve(baseName + ext);
                if (Files.exists(file)) {
                    try {
                        Files.delete(file);
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }

    private String extractJobDescriptionFromCard() {
        try {
            // Look for the updated job card
            String[] cardSelectors = {
                "article.card.job-card_jobCard__MkcJD",
                "article[class*='job-card_jobCard']",
                "[class*='job-card_jobCard__MkcJD']",
                "article.card"
            };

            for (String cardSelector : cardSelectors) {
                try {
                    Locator jobCard = page.locator(cardSelector).first();
                    if (jobCard.isVisible()) {
                        return jobCard.textContent();
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Fallback to full page
            return page.textContent("body");

        } catch (Exception e) {
            return "";
        }
    }

    public void setMaxApplications(int maxApplications) {
        this.maxApplications = maxApplications;
    }

    public void close() {
        if (aiService != null) {
            aiService.shutdown();
        }
        if (page != null) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private static class JobInfo {

        String title = "";
        String company = "";
    }

    public static void main(String[] args) {
        ReedCrawler crawler = new ReedCrawler();

        try {
            // Set the maximum number of applications you want to submit
            crawler.setMaxApplications(10);

            crawler.setupBrowser();
            crawler.openForLogin();
            crawler.processJobsAndApply();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            crawler.close();
        }
    }
}
