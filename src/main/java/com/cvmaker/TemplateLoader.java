package com.cvmaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemplateLoader {

    private final Path templatesRoot;

    public TemplateLoader(Path templatesRoot) {
        this.templatesRoot = templatesRoot;
    }

    public String loadTex(String templateName) throws IOException {
        Path texPath = templatesRoot.resolve(templateName).resolve("template.tex");
        return Files.readString(texPath);
    }

    public String loadCoverLetterTex(String templateName) throws IOException {
        Path texPath = templatesRoot.resolve(templateName).resolve("cover_letter_template.tex");
        if (!Files.exists(texPath)) {
            // Fall back to a generic cover letter template path
            texPath = templatesRoot.resolve("cover_letter_template.tex");
        }
        return Files.readString(texPath);
    }
}