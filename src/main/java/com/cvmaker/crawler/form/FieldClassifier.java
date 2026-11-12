package com.cvmaker.crawler.form;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure rules that sort a form field into a section of a job application form and give it a
 * readable name. No Playwright here, so every rule is unit-tested.
 *
 * <p>Keywords match whole words, not substrings: keys and labels are split into words at case
 * changes ({@code txtFirstName}), digits and punctuation ({@code ctl00$Main$e-mail}), and a
 * keyword matches a run of consecutive words, written together or apart ("first name" matches
 * {@code firstname}, {@code FirstName} and {@code first_name}; "email" matches {@code e-mail}).
 * A final plural "s" is allowed. So "tel" no longer matches "hotel", "git" no longer matches
 * "digit", and "name" alone only counts as the applicant's name when it is not part of another
 * noun such as "company name".
 */
public final class FieldClassifier {

    /** Sections of an application form, in the order the crawler prints them. */
    public enum Section {
        APPLICANT("applicant"),
        ELIGIBILITY("eligibility"),
        DOCUMENTS("documents"),
        CONSENTS("consents"),
        QUESTIONS("questions"),
        ACTIONS("actions"),
        OTHER("other");

        private final String key;

        Section(String key) {
            this.key = key;
        }

        /** The lower-case key used by {@code FormCrawler}'s section map. */
        public String key() {
            return key;
        }
    }

    private static final Set<String> ACTION_TYPES = Set.of("submit", "button", "reset", "image");

    private static final String[] APPLICANT = {
        "first name", "forename", "given name", "middle name", "surname", "last name", "family name",
        "full name", "email", "phone", "telephone", "tel", "mobile", "linkedin", "github", "gitlab",
        "git", "city", "town", "postcode", "post code", "postal", "zip", "address"
    };
    private static final String[] ELIGIBILITY = {
        "eligibility", "eligible", "right to work", "work permit", "visa", "sponsorship",
        "security clearance", "notice period", "availability", "salary expectation", "expected salary"
    };
    private static final String[] DOCUMENTS = {
        "cv", "resume", "cover letter", "portfolio", "upload", "document", "attachment"
    };
    private static final String[] CONSENTS = {
        "terms", "conditions", "privacy", "consent", "gdpr", "data protection", "agree"
    };
    private static final String[] QUESTIONS = {
        "question", "why", "motivation", "explain", "experience", "summary", "statement", "supporting"
    };
    /** Words that may stand right before a bare "name" and still mean the applicant's name. */
    private static final Set<String> NAME_QUALIFIERS = Set.of(
        "your", "full", "legal", "preferred", "applicant", "candidate", "my",
        "txt", "tb", "tbx", "inp", "input", "fld", "field", "box", "control", "ctrl");

    private FieldClassifier() {
    }

    /**
     * Decide which section a field belongs to.
     *
     * @param tag   lower-case tag ("input", "select", "textarea", "button", "radio-group")
     * @param type  lower-case type attribute ("text", "checkbox", "file", ...), may be empty
     * @param key   the field's name or id, may be empty
     * @param label the field's label text, may be empty
     * @return the section; never null
     */
    public static Section classify(String tag, String type, String key, String label) {
        String t = lower(tag);
        String ty = lower(type);
        List<String> words = words(nz(key) + " " + nz(label));

        if (t.equals("button") || ACTION_TYPES.contains(ty)) {
            return Section.ACTIONS;
        }
        if (ty.equals("file")) {
            return Section.DOCUMENTS;
        }
        if (ty.equals("checkbox") && matchesAny(words, CONSENTS)) {
            return Section.CONSENTS;
        }
        if (matchesAny(words, APPLICANT) || isApplicantName(key) || isApplicantName(label)) {
            return Section.APPLICANT;
        }
        if (matchesAny(words, ELIGIBILITY)) {
            return Section.ELIGIBILITY;
        }
        if (matchesAny(words, DOCUMENTS)) {
            return Section.DOCUMENTS;
        }
        if (t.equals("textarea") || matchesAny(words, QUESTIONS)) {
            return Section.QUESTIONS;
        }
        return Section.OTHER;
    }

    /**
     * A readable name for a field: its label, else a humanised key (ASP.NET control prefixes and
     * suffixes stripped), else a button's text, else "(unnamed field)".
     */
    public static String niceName(String tag, String key, String label, String value) {
        String l = collapse(label);
        if (!l.isEmpty()) {
            return l;
        }
        if (!nz(key).isBlank()) {
            String[] parts = key.split("\\$");
            String last = parts.length == 0 ? "" : parts[parts.length - 1]
                    .replaceAll("(TextBoxControl|DropDownListControl|RadioButtonListControl|CheckBoxControl)$", "")
                    .replaceAll("^SingleLineTextBox", "")
                    .replaceAll("^DropDownList", "")
                    .replaceAll("^RadioButtonList", "")
                    .replaceAll("^AsynchronousFileUpload", "");
            String human = humanize(last);
            if (!human.isEmpty()) {
                return human;
            }
        }
        if ("button".equals(lower(tag))) {
            String v = collapse(value);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "(unnamed field)";
    }

    /**
     * Turn a key into words: drop GUIDs and hash-like runs (6 or more hex characters standing
     * alone and containing a digit, so real words such as "facade" survive), then split camel
     * case, letters from digits, and underscores.
     */
    public static String humanize(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.replaceAll("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}", " ")
                .replaceAll("(?<![A-Za-z0-9])(?=[A-Fa-f]*[0-9])[A-Fa-f0-9]{6,}(?![A-Za-z0-9])", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("_+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Split text into lower-case words at case changes, letter/digit changes and any character
     * that is not a letter or digit. Accents are removed first ("résumé" becomes "resume").
     */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String spaced = plain
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2")
                .toLowerCase(Locale.ROOT);
        for (String w : spaced.split("[^a-z0-9]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Whether a run of consecutive words, joined without spaces, equals the keyword joined
     * without spaces (optionally followed by a plural "s").
     */
    static boolean matches(List<String> words, String keyword) {
        String target = String.join("", words(keyword));
        if (target.isEmpty()) {
            return false;
        }
        for (int start = 0; start < words.size(); start++) {
            StringBuilder run = new StringBuilder();
            for (int end = start; end < words.size(); end++) {
                run.append(words.get(end));
                if (run.length() > target.length() + 1) {
                    break;
                }
                String r = run.toString();
                if (r.equals(target) || r.equals(target + "s")) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean matchesAny(List<String> words, String... keywords) {
        for (String k : keywords) {
            if (matches(words, k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A bare "name" means the applicant's name only when nothing, or a qualifier such as "your"
     * or a control prefix such as "txt", stands right before it ("Name", "txtName", "Your name"),
     * not when another noun does ("Company name", "ReferenceName").
     */
    static boolean isApplicantName(String text) {
        List<String> w = words(text);
        for (int i = 0; i < w.size(); i++) {
            if (w.get(i).equals("name") && (i == 0 || NAME_QUALIFIERS.contains(w.get(i - 1))
                    || w.get(i - 1).matches("[0-9]+"))) {
                return true;
            }
        }
        return false;
    }

    private static String collapse(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
