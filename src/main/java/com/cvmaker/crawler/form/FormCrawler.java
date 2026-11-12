package com.cvmaker.crawler.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.LabelResolver;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class FormCrawler extends AbstractJobCrawler {

    public FormCrawler() throws Exception { super(new CrawlerConfig()); }
    public FormCrawler(CrawlerConfig crawlerConfig) throws Exception { super(crawlerConfig); }

    @Override
    public String getCrawlerName() { return "Form Crawler"; }

    @Override
    public void setupBrowser() { super.setupBrowser(); }

    @Override
    public void processJobsAndApply() {
        try {
            System.out.println("\n=== Job Application Form Scanner ===");
            System.out.println("Navigate to the application form, then press Enter to scan...");
            System.in.read();

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(300);

            scanJobApplicationForm(page);

            System.out.println("\nDone. Press Enter to close.");
            System.in.read();

        } catch (Exception e) {
            System.out.println("Error during form crawl: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void scanJobApplicationForm(Page page) {
        System.out.println("\nURL: " + page.url());
        System.out.println("Title: " + page.title());
        System.out.println();

        List<Field> fields = collectFields(page);
        Map<String, List<Field>> sections = groupFields(fields);

        printSection("Applicant Information", sections.getOrDefault("applicant", List.of()));
        printSection("Eligibility", sections.getOrDefault("eligibility", List.of()));
        printSection("Documents", sections.getOrDefault("documents", List.of()));
        printSection("Consents & Declarations", sections.getOrDefault("consents", List.of()));
        printSection("Application Questions", sections.getOrDefault("questions", List.of()));
        printSection("Actions", sections.getOrDefault("actions", List.of()));
        printSection("Other", sections.getOrDefault("other", List.of()));
    }

    // Collect

    private List<Field> collectFields(Page page) {
        List<Field> results = new ArrayList<>();
        collectBySelector(page, "input:not([type='hidden'])", results);
        collectBySelector(page, "textarea", results);
        collectBySelector(page, "select", results);
        collectBySelector(page, "button, input[type='submit'], input[type='button']", results);
        return mergeRadioGroups(page, results);
    }

    private void collectBySelector(Page page, String selector, List<Field> out) {
        Locator els = page.locator(selector);
        int n = els.count();
        for (int i = 0; i < n; i++) {
            Locator el = els.nth(i);
            if (!isVisibleSafe(el)) continue;

            String tag = lower(safeEval(el, "el => el.tagName"));
            String type = lower(nz(el.getAttribute("type")));
            String name = nz(el.getAttribute("name"));
            String id = nz(el.getAttribute("id"));
            String label = bestLabel(el, id);
            String key = chooseKey(name, id);

            Field f = new Field(tag, type, key, label, el);

            switch (tag) {
                case "input" -> {
                    switch (type) {
                        case "checkbox" -> f.value = el.isChecked() ? "true" : "false";
                        case "radio" -> f.isRadio = true; // handled later
                        case "file" -> f.value = "(file)";
                        case "submit", "button" -> {
                            f.tag = "button";
                            f.value = trimmed(el.textContent(), el.getAttribute("value"));
                        }
                        default -> f.value = preferInputValue(el, el.getAttribute("value"), el.getAttribute("placeholder"));
                    }
                }
                case "textarea" -> f.value = preferInputValue(el, null, el.getAttribute("placeholder"));
                case "select" -> {
                    f.value = selectedOptionText(el);
                    f.options = selectOptions(el);
                }
                case "button" -> f.value = trimmed(el.textContent(), el.getAttribute("value"));
            }

            out.add(f);
        }
    }

    // Improved radio grouping and labeling

    private List<Field> mergeRadioGroups(Page page, List<Field> fields) {
        Map<String, List<Field>> groups = new LinkedHashMap<>();
        List<Field> others = new ArrayList<>();

        for (Field f : fields) {
            if (f.isRadio) {
                String groupKey = radioGroupKey(f);
                groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(f);
            } else {
                others.add(f);
            }
        }

        for (Map.Entry<String, List<Field>> e : groups.entrySet()) {
            List<Field> radios = e.getValue();
            if (radios.isEmpty()) continue;

            // Determine a proper question label from the container/legend/headings
            Locator container = closestRadioGroupContainer(radios.get(0).element);
            String groupLabel = radioGroupLabel(page, container, radios);

            Field agg = new Field("radio-group", "radio", e.getKey(), groupLabel, container);
            agg.radioChoices = new ArrayList<>();
            for (Field r : radios) {
                String choiceText = radioChoiceText(r.element);
                boolean selected = isCheckedSafe(r.element);
                agg.radioChoices.add(new Choice(isEmpty(choiceText) ? "(option)" : choiceText, selected));
            }
            agg.value = selectedChoiceText(agg.radioChoices);
            others.add(agg);
        }

        return others;
    }

    private String radioGroupKey(Field f) {
        // Prefer name, fallback to nearest group container id, else unique path
        String name = nz(f.element.getAttribute("name"));
        if (!isEmpty(name)) return "name:" + name;

        Locator container = closestRadioGroupContainer(f.element);
        String cid = container != null ? nz(container.getAttribute("id")) : "";
        if (!isEmpty(cid)) return "container:" + cid;

        // last fallback: x/y position bucket to keep radios together (best-effort)
        String key = f.element.evaluate("el => { const r = el.getBoundingClientRect(); return Math.round(r.top/10)+'-'+Math.round(r.left/10); }").toString();
        return "pos:" + key;
    }

    private Locator closestRadioGroupContainer(Locator radio) {
        // fieldset radiogroup, role="radiogroup", role="group", or nearest form section
        try {
            String[] candidates = new String[] {
                "fieldset",
                "*[@role='radiogroup']",
                "*[@role='group']",
                "div",
                "section",
                "li",
                "article",
                "form"
            };
            for (String sel : candidates) {
                Locator c = radio.locator("xpath=ancestor::" + sel + "[1]");
                if (c != null && c.count() > 0) return c.first();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private String radioGroupLabel(Page page, Locator container, List<Field> radios) {
        // 1) fieldset > legend
        if (container != null) {
            try {
                Locator legend = container.locator("legend").first();
                if (legend != null && legend.count() > 0) {
                    String t = trimmed(legend.textContent());
                    if (!isEmpty(t)) return t;
                }
            } catch (Exception ignore) {}
        }

        // 2) aria-labelledby on container
        String labelledBy = container != null ? nz(container.getAttribute("aria-labelledby")) : "";
        if (!isEmpty(labelledBy)) {
            String t = joinedTextByIds(page, labelledBy);
            if (!isEmpty(t)) return t;
        }

        // 3) nearest heading above group
        if (container != null) {
            String[] hs = { "h1","h2","h3","h4","h5","h6" };
            for (String h : hs) {
                try {
                    Locator heading = container.locator("xpath=preceding::" + h + "[1]");
                    if (heading != null && heading.count() > 0) {
                        String t = trimmed(heading.first().textContent());
                        if (!isEmpty(t) && t.length() < 200) return t;
                    }
                } catch (Exception ignore) {}
            }
        }

        // 4) first radio’s own enclosing label block prompt
        String lbl = radioChoiceQuestionFromStructure(radios.get(0).element);
        if (!isEmpty(lbl)) return lbl;

        // 5) fallback to any radio label (previous behavior)
        for (Field r : radios) {
            String l = bestLabel(r.element, nz(r.element.getAttribute("id")));
            if (!isEmpty(l)) return l;
        }

        return "(question)";
    }

    private String radioChoiceQuestionFromStructure(Locator radio) {
        // Look for text block immediately above the radios within the same container
        try {
            Locator block = radio.locator("xpath=ancestor::*[self::div or self::section or self::fieldset][1]");
            if (block != null && block.count() > 0) {
                Locator prompt = block.first().locator("xpath=.//legend|.//p|.//label[not(@for)]|.//span|.//div").first();
                if (prompt != null && prompt.count() > 0) {
                    String t = trimmed(prompt.textContent());
                    if (!isEmpty(t) && t.length() > 0 && t.length() < 200) return t;
                }
            }
        } catch (Exception ignore) {}
        return "";
    }

    private String joinedTextByIds(Page page, String ids) {
        return LabelResolver.textByIds(page, ids);
    }

    private String radioChoiceText(Locator radio) {
        String id = nz(radio.getAttribute("id"));

        // label[for=id]
        String lbl = bestLabel(radio, id);
        if (!isEmpty(lbl)) return lbl;

        // aria-label
        String aria = nz(radio.getAttribute("aria-label"));
        if (!isEmpty(aria)) return aria;

        // aria-labelledby
        String labelledBy = nz(radio.getAttribute("aria-labelledby"));
        if (!isEmpty(labelledBy)) {
            String t = joinedTextByIds(radio.page(), labelledBy);
            if (!isEmpty(t)) return t;
        }

        // nextSibling text
        try {
            String sib = (String) radio.evaluate("el => (el.nextSibling && el.nextSibling.textContent) ? el.nextSibling.textContent : ''");
            if (!isEmpty(sib)) return sib.trim();
        } catch (Exception ignore) {}

        // parent text (small scoped)
        try {
            Locator parent = radio.locator("xpath=parent::*[self::label or self::span]");
            if (parent != null && parent.count() > 0) {
                String t = trimmed(parent.first().textContent());
                if (!isEmpty(t)) return t;
            }
        } catch (Exception ignore) {}

        // value fallback
        String val = nz(radio.getAttribute("value"));
        if (!isEmpty(val)) return val;

        return "(option)";
    }

    private boolean isCheckedSafe(Locator el) { try { return el.isChecked(); } catch (Exception e) { return false; } }

    // Grouping + printing

    private Map<String, List<Field>> groupFields(List<Field> fields) {
        Map<String, List<Field>> map = new LinkedHashMap<>();
        map.put("applicant", new ArrayList<>());
        map.put("eligibility", new ArrayList<>());
        map.put("documents", new ArrayList<>());
        map.put("consents", new ArrayList<>());
        map.put("questions", new ArrayList<>());
        map.put("actions", new ArrayList<>());
        map.put("other", new ArrayList<>());

        for (Field f : fields) {
            String nice = FieldClassifier.niceName(f.tag, f.key, f.label, f.value);
            FieldClassifier.Section section = FieldClassifier.classify(f.tag, f.type, f.key, f.label);
            map.get(section.key()).add(f.withNice(nice));
        }
        return map;
    }

    private void printSection(String title, List<Field> fields) {
        if (fields == null || fields.isEmpty()) return;
        System.out.println("=== " + title + " ===");
        for (Field f : fields) {
            if ("select".equals(f.tag)) {
                System.out.println("[" + typeLabel(f) + "] " + f.niceName + " -> " + show(f.value));
                if (!f.options.isEmpty()) {
                    System.out.println("  options:");
                    for (Choice c : f.options) {
                        System.out.println("   - " + (c.selected ? "[x] " : "[ ] ") + c.text);
                    }
                }
            } else if ("radio-group".equals(f.tag)) {
                System.out.println("[radio] " + f.niceName + " -> " + show(f.value));
                if (!f.radioChoices.isEmpty()) {
                    System.out.println("  choices:");
                    for (Choice c : f.radioChoices) {
                        System.out.println("   - " + (c.selected ? "[x] " : "[ ] ") + c.text);
                    }
                }
            } else if ("checkbox".equals(f.type)) {
                System.out.println("[" + typeLabel(f) + "] " + f.niceName + " -> " + ("true".equalsIgnoreCase(f.value) ? "[x]" : "[ ]"));
            } else {
                System.out.println("[" + typeLabel(f) + "] " + f.niceName + " -> " + show(f.value));
            }
        }
        System.out.println();
    }

    // Value helpers

    private String selectedOptionText(Locator select) {
        try {
            Object idx = select.evaluate("el => el.selectedIndex");
            int i = (idx instanceof Number) ? ((Number) idx).intValue() : -1;
            Locator options = select.locator("option");
            if (i >= 0 && i < options.count()) {
                String t = trimmed(options.nth(i).textContent(), options.nth(i).getAttribute("value"));
                return t.isEmpty() ? "(none)" : t;
            }
        } catch (Exception ignore) {}
        return "";
    }

    private List<Choice> selectOptions(Locator select) {
        List<Choice> list = new ArrayList<>();
        try {
            Locator options = select.locator("option");
            int n = options.count();
            Object idx = select.evaluate("el => el.selectedIndex");
            int sel = (idx instanceof Number) ? ((Number) idx).intValue() : -1;
            for (int i = 0; i < n; i++) {
                String t = trimmed(options.nth(i).textContent(), options.nth(i).getAttribute("value"));
                list.add(new Choice(t.isEmpty() ? "(empty)" : t, i == sel));
            }
        } catch (Exception ignore) {}
        return list;
    }

    private String preferInputValue(Locator el, String attrValue, String placeholder) {
        String v = safeInputValue(el);
        if (!isEmpty(v)) return v;
        if (!isEmpty(attrValue)) return attrValue;
        if (!isEmpty(placeholder)) return placeholder;
        return "";
    }

    // Small utils

    private boolean isVisibleSafe(Locator el) { try { return el.isVisible(); } catch (Exception e) { return false; } }
    private String safeInputValue(Locator el) { try { return nz(el.inputValue()); } catch (Exception e) { return ""; } }
    private String safeEval(Locator el, String js) { try { Object r = el.evaluate(js); return r == null ? "" : String.valueOf(r); } catch (Exception e) { return ""; } }
    private String chooseKey(String name, String id) { return !isEmpty(name) ? name : (!isEmpty(id) ? id : ""); }
    private String bestLabel(Locator el, String id) {
        return LabelResolver.findLabel(el, id);
    }

    private String trimmed(String... xs) {
        for (String x : xs) {
            if (!isEmpty(x)) {
                String t = x.replace("\n", " ").replace("\r", " ").trim();
                if (!t.isEmpty()) return t;
            }
        }
        return "";
    }

    private String lower(String s) { return s == null ? "" : s.toLowerCase(); }
    private String nz(String s) { return s == null ? "" : s; }
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    // Added missing helpers

    private String selectedChoiceText(List<Choice> choices) {
        if (choices == null) return "";
        for (Choice c : choices) {
            if (c.selected) return c.text == null ? "" : c.text;
        }
        return "";
    }

    // Models

    private static class Field {
        String tag, type, key, label, niceName, value;
        Locator element;
        boolean isRadio = false;
        List<Choice> options = new ArrayList<>();
        List<Choice> radioChoices = new ArrayList<>();
        Field(String tag, String type, String key, String label, Locator element) {
            this.tag = tag; this.type = type; this.key = key; this.label = label; this.element = element;
        }
        Field withNice(String n) { this.niceName = n; return this; }
    }
    private static class Choice {
        String text; boolean selected;
        Choice(String text, boolean selected) { this.text = text; this.selected = selected; }
    }

    private String typeLabel(Field f) {
        if ("input".equals(f.tag)) return "input(" + (isEmpty(f.type) ? "text" : f.type) + ")";
        return f.tag;
    }
    private String show(String v) { return isEmpty(v) ? "(empty)" : v; }
}