package com.cvmaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.cvmaker.JobDataFetcher.JobData;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.service.ai.AiService;

public class CVGenerator {

    /** How long one LaTeX run may take before it is killed. */
    public static final Duration DEFAULT_LATEX_TIMEOUT = Duration.ofSeconds(120);

    /** How many trailing lines of compiler output are kept and printed on failure. */
    static final int LOG_TAIL_LINES = 40;

    private final TemplateLoader templateLoader;
    private final AiService aiService;
    private final JobDataFetcher jobDataFetcher;
    private final ConfigManager config;

    /** The kinds of document this generator produces. */
    private enum DocumentKind {
        CV("CV"),
        COVER_LETTER("cover letter");

        private final String label;

        DocumentKind(String label) {
            this.label = label;
        }
    }

    public CVGenerator(ConfigManager config) {
        this.config = config;
        this.templateLoader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
        this.aiService = new AiService(config.getAiModel(), config.getAiTemperature());
        this.jobDataFetcher = new JobDataFetcher();
    }

    /**
     * Main entry point - generates everything based on config
     */
    public void generate() throws Exception {
        System.out.println("Starting CV generation...");

        if (config.hasJobUrl()) {
            generateFromJobUrl();
        } else {
            generateFromText();
        }

        System.out.println("Generation completed successfully!");
    }

    /**
     * Generate CV and cover letter from job URL
     */
    private void generateFromJobUrl() throws Exception {
        System.out.println("Generating from job URL: " + config.getJobUrl());

        // Fetch job data
        JobData jobData = jobDataFetcher.fetchJobData(config.getJobUrl());
        System.out.println("Fetched job data: " + jobData.getJobTitle() + " at " + jobData.getCompanyName());

        // Update config with job description
        config.setJobDescriptionContent(jobData.getJobDescription());

        // Create output directory structure
        String outputDir = createOutputDirectory(jobData.getJobName());
        System.out.println("Output directory: " + Paths.get(outputDir).toAbsolutePath());

        generateDocuments(outputDir);

        System.out.println("Generation completed for: " + jobData.getJobTitle() + " at " + jobData.getCompanyName());
    }

    /**
     * Generate CV and cover letter from text files
     */
    private void generateFromText() throws Exception {
        System.out.println("Generating from configuration files...");

        String outputDir = createOutputDirectory();
        generateDocuments(outputDir);
    }

    /**
     * Generate the CV and, if enabled, the cover letter into the given directory.
     */
    private void generateDocuments(String outputDir) throws Exception {
        generateDocument(DocumentKind.CV, outputDir);

        if (config.isGenerateCoverLetter()) {
            generateDocument(DocumentKind.COVER_LETTER, outputDir);
        }
    }

    /**
     * Generate one document: ask the AI for LaTeX, save it, compile it and clean up.
     */
    private void generateDocument(DocumentKind kind, String outputDir) throws Exception {
        boolean coverLetter = kind == DocumentKind.COVER_LETTER;
        String defaultPdfName = coverLetter ? "cover_letter.pdf" : "cv.pdf";
        String defaultTexName = coverLetter ? "cover_letter.tex" : "cv.tex";
        String pdfName = sanitizeFileName(
                coverLetter ? config.getCoverLetterPdfName() : config.getOutputPdfName(), defaultPdfName);
        String texName = sanitizeFileName(
                coverLetter ? config.getCoverLetterTexFilename() : config.getCvTexFilename(), defaultTexName);

        System.out.println("Generating " + kind.label + "...");

        String referenceTemplate = loadTemplate(config.getTemplateName(), coverLetter);

        System.out.println("Generating " + kind.label + " LaTeX with AI...");
        String generatedLatex = coverLetter
                ? aiService.generateDirectLatexCoverLetter(
                        config.getUserDataContent(),
                        referenceTemplate,
                        config.getJobDescriptionContent(),
                        config.getCoverLetterPromptContent())
                : aiService.generateDirectLatexCV(
                        config.getUserDataContent(),
                        referenceTemplate,
                        config.getJobDescriptionContent(),
                        config.getCvPromptContent());

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve(texName);
        Files.writeString(texOutputPath, generatedLatex);

        if (config.isSaveGeneratedLatex()) {
            System.out.println("LaTeX saved: " + texOutputPath.toAbsolutePath());
        }

        System.out.println("Compiling " + kind.label + " to PDF...");
        // On failure this throws and leaves the .tex and .log in place for debugging
        newLatexCompiler().compile(outputDirPath, texOutputPath, pdfName);

        // Clean up intermediate files if not in debug mode
        if (!config.isSaveGeneratedLatex()) {
            cleanupIntermediateFiles(outputDirPath, getBaseName(texOutputPath), config.getCleanupExtensions());
        }

        System.out.println(capitalize(kind.label) + " generated: " + outputDirPath.resolve(pdfName).toAbsolutePath());
    }

    private LatexCompiler newLatexCompiler() {
        return new LatexCompiler(
                config.getLatexCompiler(),
                config.getLatexCompilerArgs(),
                DEFAULT_LATEX_TIMEOUT,
                config.getProgressReportInterval(),
                new ProcessCommandRunner());
    }

    /**
     * Load template with error handling
     */
    private String loadTemplate(String templateName, boolean isCoverLetter) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return null;
        }

        try {
            if (isCoverLetter) {
                return templateLoader.loadCoverLetterTex(templateName);
            } else {
                return templateLoader.loadTex(templateName);
            }
        } catch (IOException e) {
            String templateType = isCoverLetter ? "cover letter template" : "template";
            System.out.println("Warning: No " + templateType + " found for '" + templateName + "', proceeding without template.");
            return null;
        }
    }

    /**
     * Create output directory
     */
    private String createOutputDirectory() throws IOException {
        return createOutputDirectory(null);
    }

    private String createOutputDirectory(String jobName) throws IOException {
        String outputDir;
        if (jobName != null && !jobName.trim().isEmpty()) {
            outputDir = Paths.get(config.getOutputDirectory(), sanitizeFileName(jobName, "job")).toString();
        } else {
            outputDir = config.getOutputDirectory();
        }

        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        return outputDir;
    }

    /**
     * Get base name from file path
     */
    static String getBaseName(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Delete intermediate LaTeX files named {@code baseName + ext} for each extension. Extensions
     * are trimmed (the config splits on commas without trimming). Failures are reported, not thrown.
     *
     * @return the number of files that could not be deleted
     */
    static int cleanupIntermediateFiles(Path outputDir, String baseName, List<String> extensions) {
        int failures = 0;
        if (extensions == null) {
            return 0;
        }
        for (String rawExt : extensions) {
            if (rawExt == null || rawExt.trim().isEmpty()) {
                continue;
            }
            Path file = outputDir.resolve(baseName + rawExt.trim());
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                failures++;
                System.err.println("Warning: could not delete intermediate file " + file + ": " + e);
            }
        }
        return failures;
    }

    /**
     * Reduce a configured or scraped name to a single safe path segment: any directory part is
     * dropped, characters that are illegal on common file systems become '_', and leading dots are
     * removed so the result cannot be "." or "..". Returns {@code fallback} if nothing usable is left.
     */
    static String sanitizeFileName(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String trimmed = name.trim();
        // Keep only the last path segment, for either separator
        int lastSep = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (lastSep >= 0) {
            trimmed = trimmed.substring(lastSep + 1);
        }
        String cleaned = trimmed
                .replaceAll("[\\p{Cntrl}<>:\"|?*]", "_")
                .replaceAll("^\\.+", "")
                .trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /**
     * The PDF name the LaTeX compiler writes for a .tex file: only a trailing ".tex" suffix is
     * replaced, so "my.tex.notes.tex" gives "my.tex.notes.pdf" and "cv" gives "cv.pdf".
     */
    static String pdfFileNameFor(String texFileName) {
        String base = texFileName;
        if (base.toLowerCase(Locale.ROOT).endsWith(".tex")) {
            base = base.substring(0, base.length() - ".tex".length());
        }
        return base + ".pdf";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Getters
    public ConfigManager getConfig() {
        return config;
    }

    public AiService getAiService() {
        return aiService;
    }

    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }

    public JobDataFetcher getJobDataFetcher() {
        return jobDataFetcher;
    }

    public void shutdown() {
        if (aiService != null) {
            aiService.shutdown();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Process seam: running the LaTeX compiler
    // ------------------------------------------------------------------------------------------

    /** Outcome of running an external command. */
    record CommandResult(int exitCode, boolean timedOut, List<String> outputTail) {
    }

    /** Runs an external command; the seam that lets tests replace the real process. */
    @FunctionalInterface
    interface CommandRunner {
        /**
         * Run {@code command} in {@code workDir}, passing each output line (stdout and stderr merged)
         * to {@code lineListener}, and give up after {@code timeout}.
         */
        CommandResult run(List<String> command, Path workDir, Duration timeout, Consumer<String> lineListener)
                throws IOException, InterruptedException;
    }

    /**
     * Real {@link CommandRunner}: starts the process, drains its merged output on a separate thread
     * (so a chatty process can never block on a full pipe), waits with a timeout and kills the
     * process tree if it overruns. Only the last {@code tailLines} lines are kept in memory.
     */
    static final class ProcessCommandRunner implements CommandRunner {
        private static final long DRAIN_JOIN_MILLIS = 5_000;
        private final int tailLines;

        ProcessCommandRunner() {
            this(LOG_TAIL_LINES);
        }

        ProcessCommandRunner(int tailLines) {
            this.tailLines = Math.max(1, tailLines);
        }

        @Override
        public CommandResult run(List<String> command, Path workDir, Duration timeout, Consumer<String> lineListener)
                throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }

            Process proc = pb.start();
            proc.getOutputStream().close(); // the compiler must never wait for input

            Deque<String> tail = new ArrayDeque<>();
            Thread drainer = new Thread(() -> drain(proc.getInputStream(), tail, lineListener),
                    "latex-output-drainer");
            drainer.setDaemon(true);
            drainer.start();

            boolean finished;
            try {
                finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                destroyTree(proc);
                throw e;
            }
            if (!finished) {
                destroyTree(proc);
                proc.waitFor(DRAIN_JOIN_MILLIS, TimeUnit.MILLISECONDS);
            }

            drainer.join(DRAIN_JOIN_MILLIS);
            if (drainer.isAlive()) {
                // A grandchild may still hold the pipe; closing it ends the read
                proc.getInputStream().close();
                drainer.join(DRAIN_JOIN_MILLIS);
            }

            List<String> tailCopy;
            synchronized (tail) {
                tailCopy = new ArrayList<>(tail);
            }
            int exitCode = finished ? proc.exitValue() : -1;
            return new CommandResult(exitCode, !finished, Collections.unmodifiableList(tailCopy));
        }

        private void drain(InputStream in, Deque<String> tail, Consumer<String> lineListener) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (tail) {
                        tail.addLast(line);
                        if (tail.size() > tailLines) {
                            tail.removeFirst();
                        }
                    }
                    if (lineListener != null) {
                        lineListener.accept(line);
                    }
                }
            } catch (IOException e) {
                // Stream closed because the process was killed; nothing more to read
            }
        }

        private static void destroyTree(Process proc) {
            proc.descendants().forEach(ProcessHandle::destroyForcibly);
            proc.destroyForcibly();
        }
    }

    /** Thrown when the LaTeX compiler fails, times out or produces no PDF. */
    public static class LatexCompilationException extends RuntimeException {
        private final List<String> logTail;

        public LatexCompilationException(String message, List<String> logTail) {
            super(message);
            this.logTail = logTail == null ? List.of() : List.copyOf(logTail);
        }

        /** The last lines of compiler output. */
        public List<String> getLogTail() {
            return logTail;
        }
    }

    /**
     * Compiles a .tex file to PDF with the configured compiler, then renames the PDF to the requested
     * output name. On failure the last lines of the compiler log are always printed.
     */
    static final class LatexCompiler {
        private final String compiler;
        private final List<String> compilerArgs;
        private final Duration timeout;
        private final int progressReportInterval;
        private final CommandRunner runner;

        LatexCompiler(String compiler, List<String> compilerArgs, Duration timeout, int progressReportInterval,
                CommandRunner runner) {
            this.compiler = compiler;
            this.compilerArgs = compilerArgs == null ? List.of() : compilerArgs;
            this.timeout = timeout;
            this.progressReportInterval = progressReportInterval;
            this.runner = runner;
        }

        /** The full command line: compiler, non-blank args, then the .tex file name. */
        List<String> buildCommand(String texFileName) {
            List<String> command = new ArrayList<>();
            command.add(compiler);
            for (String arg : compilerArgs) {
                if (arg != null && !arg.trim().isEmpty()) {
                    command.add(arg.trim());
                }
            }
            command.add(texFileName);
            return command;
        }

        /**
         * @return the path of the final PDF ({@code dir/outputPdfName})
         */
        Path compile(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
            String texFileName = texFile.getFileName().toString();
            String pdfFileName = pdfFileNameFor(texFileName);
            Path pdfPath = dir.resolve(pdfFileName);

            // A PDF left over from an earlier run must not be mistaken for this run's output
            Files.deleteIfExists(pdfPath);

            int[] pageCount = {0};
            Consumer<String> progress = line -> {
                if (progressReportInterval > 0 && line.matches(".*\\[\\d+.*")) {
                    pageCount[0]++;
                    if (pageCount[0] % progressReportInterval == 0) {
                        System.out.printf("   Processing page %d...%n", pageCount[0]);
                    }
                }
            };

            CommandResult result = runner.run(buildCommand(texFileName), dir, timeout, progress);

            if (result.timedOut()) {
                printLogTail(result);
                throw new LatexCompilationException(
                        "LaTeX compilation timed out after " + timeout.toSeconds() + "s and was stopped",
                        result.outputTail());
            }

            boolean pdfExists = Files.exists(pdfPath);
            if (!pdfExists) {
                printLogTail(result);
                String reason = result.exitCode() != 0
                        ? "LaTeX compilation failed (exit code " + result.exitCode() + ")"
                        : "PDF file was not generated";
                throw new LatexCompilationException(reason, result.outputTail());
            }

            if (result.exitCode() != 0) {
                // pdflatex in nonstopmode exits non-zero on recoverable errors but still writes a PDF
                System.out.println("Warning: LaTeX exited with code " + result.exitCode()
                        + " but produced a PDF; check the output below.");
                printLogTail(result);
            }

            Path target = dir.resolve(outputPdfName);
            if (!pdfFileName.equals(outputPdfName)) {
                Files.move(pdfPath, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }

        private static void printLogTail(CommandResult result) {
            System.out.println("LaTeX compilation output (last " + result.outputTail().size() + " lines):");
            for (String line : result.outputTail()) {
                System.out.println("   " + line);
            }
        }
    }
}
