package com.cvmaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;

public class TemplateLoader {

    private final Path templatesRoot;

    public TemplateLoader(Path templatesRoot) {
        this.templatesRoot = templatesRoot;
    }

    public String loadTex(String templateName) throws IOException {
        Path texPath = templatesRoot.resolve(templateName).resolve("template.tex");
        return Files.readString(texPath);
    }

    public JSONObject loadTemplateJson(String templateName) throws IOException {
        Path jsonPath = templatesRoot.resolve(templateName).resolve("template.json");
        String jsonString = Files.readString(jsonPath);
        return new JSONObject(jsonString);
    }
}
