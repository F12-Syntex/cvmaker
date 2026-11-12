package com.cvmaker.application.management;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The status of a job application, and the single ranking of statuses.
 *
 * <p>Status text is free text written by the AI or typed by hand ("Offer", "Offered",
 * "Interview Scheduled", "Not selected"). {@link #normalize(String)} maps it to one of these
 * constants, and {@link #rank()} orders them. Every place that has to decide which of two
 * statuses to keep must use this ranking.
 *
 * <p>The ranking answers: when two emails about the same company disagree and their dates
 * cannot decide, which status describes the application best?
 * <ul>
 * <li>The in-progress stages rank in pipeline order: applied, screening, assessment, interview,
 * final interview.</li>
 * <li>{@link #REJECTED} and {@link #WITHDRAWN} end the pipeline, so they outrank every
 * in-progress stage. An older "Interview" email must not hide a rejection.</li>
 * <li>{@link #OFFERED} and {@link #ACCEPTED} rank highest. An offer needs action from the
 * applicant and must not be hidden by a rejection for another role at the same company.</li>
 * <li>{@link #UNKNOWN} ranks lowest, so unrecognised text never replaces a known status.</li>
 * </ul>
 */
public enum ApplicationStatus {

    /** Missing or unrecognised status text. */
    UNKNOWN("Unknown", 0),
    /** The application was sent or its receipt was confirmed. */
    APPLIED("Applied", 1),
    /** The application is being reviewed, or a screening call is planned. */
    SCREENING("Screening", 2),
    /** A test, assignment or other assessment was requested. */
    ASSESSMENT("Assessment", 3),
    /** An interview was offered, scheduled or held. */
    INTERVIEW("Interview", 4),
    /** The last interview round. */
    FINAL_INTERVIEW("Final Interview", 5),
    /** The employer turned the application down. */
    REJECTED("Rejected", 6),
    /** The applicant withdrew. */
    WITHDRAWN("Withdrawn", 7),
    /** The employer made an offer. */
    OFFERED("Offered", 8),
    /** The applicant accepted an offer. */
    ACCEPTED("Accepted", 9);

    private record Rule(ApplicationStatus status, Pattern pattern) {
    }

    /**
     * Recognition rules, tried in order; the first match wins. The order matters: "not selected
     * for interview" must be read as a rejection before the interview rule sees it, and "offer
     * accepted" as accepted before the offer rule sees it.
     */
    private static final List<Rule> RULES = List.of(
            rule(WITHDRAWN, "\\bwithdr(aw|ew)"),
            rule(REJECTED, "reject|declin|unsuccessful|turned down|rescind|regret|\\bno offer\\b"
                    + "|\\bnot (been )?(selected|successful|shortlisted|chosen|progress|proceed)"
                    + "|\\bnot (be )?(moving|going) forward"
                    + "|\\bno longer\\b|position (has been |was |is )?(filled|closed)"),
            // "Accepted" alone means an accepted offer; "application accepted" does not.
            rule(ACCEPTED, "\\bhired\\b|\\bonboard|\\boffer\\b.*\\baccept|\\baccept\\w*\\b.*\\boffer"
                    + "|^(?!.*appli).*\\baccept"),
            rule(OFFERED, "\\boffer"),
            rule(FINAL_INTERVIEW, "\\bfinal\\b.*\\b(interview|round|stage)|\\b(interview|round|stage)\\b.*\\bfinal\\b"),
            rule(INTERVIEW, "interview"),
            rule(ASSESSMENT, "assess|\\btest(s|ing)?\\b|assignment|challenge|\\btask\\b|exercise|case study"
                    + "|take[- ]home"),
            rule(SCREENING, "screen|review|shortlist|consider|in progress"),
            rule(APPLIED, "appli|submit|receiv|confirm|acknowledg|\\bsent\\b|pending|waiting"));

    private final String label;
    private final int rank;

    ApplicationStatus(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    /** The text shown for this status, for example {@code Final Interview}. */
    public String label() {
        return label;
    }

    /** The rank: a higher rank wins when two statuses for the same application disagree. */
    public int rank() {
        return rank;
    }

    /** True for statuses after which nothing more is expected: rejected, withdrawn, accepted. */
    public boolean isTerminal() {
        return this == REJECTED || this == WITHDRAWN || this == ACCEPTED;
    }

    /**
     * Maps free status text to a status. Matching ignores case and surrounding text, so
     * "Offer", "Offered" and "Job offer received" are all {@link #OFFERED}.
     *
     * @param text the status text, may be null
     * @return the status, {@link #UNKNOWN} when the text is null, blank or not recognised
     */
    public static ApplicationStatus normalize(String text) {
        if (text == null) {
            return UNKNOWN;
        }
        String value = text.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", " ");
        if (value.isEmpty()) {
            return UNKNOWN;
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(value).find()) {
                return rule.status();
            }
        }
        return UNKNOWN;
    }

    /**
     * The rank of free status text.
     *
     * @param text the status text, may be null
     * @return the rank of {@code normalize(text)}
     */
    public static int rankOf(String text) {
        return normalize(text).rank();
    }

    /**
     * Tells whether a status should replace another one.
     *
     * @param candidate the status text that might replace the current one, may be null
     * @param current the status text in place, may be null
     * @return true only when the candidate ranks strictly higher; equal ranks keep the current
     */
    public static boolean isMoreAdvanced(String candidate, String current) {
        return rankOf(candidate) > rankOf(current);
    }

    /**
     * The text to show for free status text: the label of the recognised status, so that the
     * same status is always spelled the same way, or the trimmed text itself when it is not
     * recognised.
     *
     * @param text the status text, may be null
     * @return the display text, empty when the text is null or blank
     */
    public static String displayLabel(String text) {
        ApplicationStatus status = normalize(text);
        if (status != UNKNOWN) {
            return status.label();
        }
        return text == null ? "" : text.trim();
    }

    private static Rule rule(ApplicationStatus status, String regex) {
        return new Rule(status, Pattern.compile(regex));
    }
}
