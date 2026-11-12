package com.cvmaker.crawler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the name of the output folder for one job's generated CV.
 *
 * <p>The name has the form {@code Job-<title>_<company>_<hash>}. Title and
 * company keep it readable; the hash keeps two different jobs with the same
 * title and company apart. The same job always gets the same name, so running
 * it again replaces its own CV and nobody else's.
 *
 * <p>The result contains only {@code A-Z a-z 0-9 _ -}, so it is safe as a
 * single path segment whatever the job site put into the title.
 */
public final class JobFolderNamer {

    static final String PREFIX = "Job-";
    static final String UNTITLED = "untitled";
    static final int MAX_TITLE_WORDS = 5;
    static final int MAX_COMPANY_WORDS = 4;
    static final int MAX_PART_LENGTH = 40;
    static final int HASH_LENGTH = 8;

    private JobFolderNamer() {
    }

    /**
     * Folder name for a job found by a crawler.
     *
     * <p>What identifies the job, in order of preference: its job ID, its URL,
     * and as a last resort the job description text (crawlers such as Reed
     * currently fill in neither ID nor URL).
     *
     * @param job the job; may be null or have null fields
     * @param jobContent the job description text; may be null
     * @return a non-empty folder name
     */
    public static String folderName(JobInfo job, String jobContent) {
        String title = job == null ? null : job.getTitle();
        String company = job == null ? null : job.getCompany();
        return folderName(title, company, uniqueKey(job, jobContent));
    }

    /**
     * Folder name from the parts.
     *
     * @param title the job title; may be null or blank
     * @param company the company name; may be null or blank
     * @param uniqueKey anything that tells this job from others with the same
     *        title and company (job ID, URL, description); when null or blank
     *        the name has no hash suffix
     * @return a non-empty folder name
     */
    public static String folderName(String title, String company, String uniqueKey) {
        String titlePart = slug(title, MAX_TITLE_WORDS);
        if (titlePart.isEmpty()) {
            titlePart = UNTITLED;
        }

        StringBuilder name = new StringBuilder(PREFIX).append(titlePart);

        String companyPart = slug(company, MAX_COMPANY_WORDS);
        if (!companyPart.isEmpty()) {
            name.append('_').append(companyPart);
        }

        if (!isBlank(uniqueKey)) {
            name.append('_').append(shortHash(uniqueKey.trim()));
        }

        return name.toString();
    }

    private static String uniqueKey(JobInfo job, String jobContent) {
        if (job != null && !isBlank(job.getJobId())) {
            return "id:" + job.getJobId().trim();
        }
        if (job != null && !isBlank(job.getUrl())) {
            return "url:" + job.getUrl().trim();
        }
        if (!isBlank(jobContent)) {
            // Whitespace differences between two fetches of the same page do not make a new job.
            return "content:" + jobContent.trim().replaceAll("\\s+", " ");
        }
        return null;
    }

    /**
     * Reduce free text to at most {@code maxWords} words joined by underscores,
     * using only {@code A-Z a-z 0-9 _ -}, at most {@link #MAX_PART_LENGTH} long.
     */
    static String slug(String text, int maxWords) {
        if (text == null) {
            return "";
        }

        // "Développeur" -> "Developpeur" rather than "Dveloppeur"
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

        // Search results number their cards: "3. Java Developer" -> "Java Developer"
        ascii = ascii.replaceFirst("^\\s*\\d+[.)]\\s+", "");

        // Everything else becomes a word break, so "C#/.NET" gives "C", "NET"
        String[] words = ascii.replaceAll("[^A-Za-z0-9_-]+", " ").trim().split("\\s+");

        List<String> kept = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty() && kept.size() < maxWords) {
                kept.add(word);
            }
        }

        String slug = String.join("_", kept);
        if (slug.length() > MAX_PART_LENGTH) {
            slug = slug.substring(0, MAX_PART_LENGTH);
        }
        // No dangling separators after cutting
        return slug.replaceAll("^[_-]+|[_-]+$", "");
    }

    /**
     * First {@link #HASH_LENGTH} hex digits of the SHA-256 of {@code key}.
     */
    static String shortHash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; hex.length() < HASH_LENGTH; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on every Java platform
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
