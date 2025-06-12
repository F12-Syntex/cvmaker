package com.cvmaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class CVGenerator {

    private final TemplateLoader templateLoader;

    public CVGenerator(TemplateLoader loader) {
        this.templateLoader = loader;
    }

    public void generateCV(String userDataJsonPath, String templateName, String outputDir, String outputPdfName) throws Exception {
        // 1. Load template
        String templateTex = templateLoader.loadTex(templateName);

        // 2. Load user data
        String userDataString = Files.readString(Paths.get(userDataJsonPath));
        JSONObject userData = new JSONObject(userDataString);

        // 3. Fill template placeholders
        String filledTex = fillTemplate(templateTex, userData);

        // 4. Write .tex file
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
        Files.writeString(texOutputPath, filledTex);

        // 5. Compile LaTeX to PDF
        compileLatex(outputDirPath, texOutputPath, outputPdfName);
    }

    private String fillTemplate(String templateTex, JSONObject userData) {
        String result = templateTex;

        // Replace all string fields (flat)
        for (String key : userData.keySet()) {
            Object value = userData.get(key);
            if (value instanceof String) {
                result = result.replace("{{" + key + "}}", escapeLatex((String) value));
            }
        }

        // Handle arrays (objects and strings)
        Pattern sectionPattern = Pattern.compile("\\{\\{#(\\w+)}}([\\s\\S]*?)\\{\\{/\\1}}");
        Matcher matcher = sectionPattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String sectionKey = matcher.group(1);
            String sectionTemplate = matcher.group(2);

            if (userData.has(sectionKey) && userData.get(sectionKey) instanceof JSONArray) {
                JSONArray arr = userData.getJSONArray(sectionKey);
                StringBuilder sectionFilled = new StringBuilder();

                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);

                    if (item instanceof JSONObject) {
                        // Array of objects: replace keys
                        String itemStr = sectionTemplate;
                        JSONObject itemObj = (JSONObject) item;
                        for (String k : itemObj.keySet()) {
                            itemStr = itemStr.replace("{{" + k + "}}", escapeLatex(itemObj.optString(k, "")));
                        }
                        sectionFilled.append(itemStr);
                    } else {
                        // Array of strings: replace {{.}}
                        String itemStr = sectionTemplate.replace("{{.}}", escapeLatex(item.toString()));
                        sectionFilled.append(itemStr);
                    }
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(sectionFilled.toString()));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String escapeLatex(String str) {
        // Basic escaping for LaTeX special chars
        return str.replace("\\", "\\textbackslash{}")
                .replace("$", "\\$")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
    }

    private void compileLatex(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        // Run pdflatex (must be installed)
        ProcessBuilder pb = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",
                "-output-directory", dir.toString(),
                texFile.toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        proc.waitFor();
        // Optionally, move the PDF to the desired name
        Path pdfPath = dir.resolve(texFile.getFileName().toString().replace(".tex", ".pdf"));
        if (!pdfPath.getFileName().toString().equals(outputPdfName)) {
            Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
