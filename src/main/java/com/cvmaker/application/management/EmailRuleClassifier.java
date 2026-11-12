package com.cvmaker.application.management;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keyword rules that classify an email without an LLM. Used when the AI answer is
 * missing or unusable.
 *
 * <p>Every keyword is matched as a whole word or phrase, never as a substring, so
 * "all" does not match "call" and "test" does not match "latest". A keyword that ends
 * in {@code *} matches any word starting with it ("reject*" matches "rejected").
 * The class is pure: no I/O and no state.
 */
public final class EmailRuleClassifier {

    /** Mass mailings. Any of these means the email is not about the user's own application. */
    private static final Pattern BULK_MAIL = words(
            "newsletter*", "job alert*", "jobs alert*", "career tips", "career advice",
            "webinar*", "digest", "recommended jobs", "jobs you may", "jobs for you");

    /** Phrases that refer to an application the user made. */
    private static final Pattern OWN_APPLICATION = words(
            "your application", "you applied", "you have applied", "you've applied",
            "your candidacy", "your resume", "your interview", "we received your",
            "we have received your", "we've received your", "thank you for applying",
            "thanks for applying", "your interest in", "moving forward with your",
            "your submission", "sent your application");

    private static final Pattern INTERVIEW = words("interview*");
    private static final Pattern SCHEDULE_OR_CONFIRM = words("schedul*", "reschedul*", "confirm*");
    private static final Pattern INVITE_OR_SCHEDULE = words("invit*", "schedul*", "reschedul*", "availability");
    private static final Pattern OFFER = words("offer*");
    private static final Pattern POSITION = words("position", "role");
    private static final Pattern JOB_OR_POSITION = words("job", "position", "role");
    private static final Pattern APPLICATION = words("application");
    private static final Pattern REJECT = words("reject*");
    private static final Pattern NOT_MOVING_FORWARD = words(
            "not moving forward", "not be moving forward");

    private static final Pattern REJECTION = words(
            "reject*", "unfortunately", "not moving forward", "not be moving forward",
            "regret to inform", "unsuccessful", "not been successful", "not be progressing",
            "not progressing", "decided not to proceed", "pursue other candidates",
            "proceed with other candidates", "progress with other candidates",
            "unable to offer", "not able to offer", "cannot offer", "can't offer",
            "not be offering", "won't be offering");
    /** Wording that only a real offer uses. "We offer flexible hours" must not match. */
    private static final Pattern CLEAR_OFFER = words(
            "job offer", "offer of employment", "offer letter", "formal offer",
            "pleased to offer", "delighted to offer", "happy to offer", "excited to offer",
            "like to offer you", "offer you the position", "offer you the role",
            "offer you the job", "offer you a position", "offer you a role",
            "extend an offer", "make you an offer");

    private static final Pattern CONFIRMATION = words(
            "application received", "thank you for applying", "thanks for applying",
            "received your application");
    /** A bare "test" is left out on purpose: it is part of job titles such as "Test Analyst". */
    private static final Pattern ASSESSMENT = words(
            "assessment*", "coding challenge", "coding test", "online test", "technical test",
            "aptitude test", "psychometric test", "take-home", "take home task");
    private static final Pattern SCREENING = words("phone screen*", "screening call", "telephone screen*");
    private static final Pattern PHONE = words("phone", "telephone");
    private static final Pattern SCREEN = words("screen*");

    private static final Pattern NO_REPLY = words(
            "noreply", "no-reply", "no_reply", "donotreply", "do-not-reply");

    /** Provider name to the pattern that finds it in the sender, in a fixed order. */
    private static final Map<String, Pattern> PROVIDER_IN_SENDER = new LinkedHashMap<>();
    /** Provider name to the pattern that finds it in the body ("applied via LinkedIn"). */
    private static final Map<String, Pattern> PROVIDER_IN_BODY = new LinkedHashMap<>();

    static {
        String[] providers = {"LinkedIn", "Indeed", "Glassdoor", "AngelList", "ZipRecruiter", "Monster", "Dice"};
        for (String provider : providers) {
            PROVIDER_IN_SENDER.put(provider, words(provider));
            PROVIDER_IN_BODY.put(provider, words("via " + provider, "through " + provider));
        }
    }

    private EmailRuleClassifier() {
    }

    /**
     * Decides whether an email is about a job application the user made.
     * Conservative: mass mailings are refused first, then the email must either name the
     * user's application or describe a clear application stage.
     *
     * @param subject email subject; may be null
     * @param from    sender header; may be null
     * @param body    plain-text body; may be null
     */
    public static boolean isJobRelated(String subject, String from, String body) {
        String text = join(subject, from, body);

        if (found(BULK_MAIL, text)) {
            return false;
        }
        if (found(OWN_APPLICATION, text)) {
            return true;
        }
        return (found(INTERVIEW, text) && found(SCHEDULE_OR_CONFIRM, text))
                || (found(OFFER, text) && found(POSITION, text))
                || (found(REJECT, text) && found(APPLICATION, text))
                || (found(NOT_MOVING_FORWARD, text) && found(APPLICATION, text));
    }

    /**
     * Picks a category for an email already known to be job related. Never returns null:
     * with no clear signal the answer is {@link EmailCategory#APPLICATION_CONFIRMATION}.
     *
     * @param subject email subject; may be null
     * @param body    plain-text body; may be null
     */
    public static EmailCategory categorize(String subject, String body) {
        String text = join(subject, body);

        // Order matters. A rejection often mentions the interview or the offer it refuses,
        // so it is checked first; a clear offer outranks the interview it follows.
        if (found(REJECTION, text)) {
            return EmailCategory.REJECTION;
        }
        if (found(CLEAR_OFFER, text)) {
            return EmailCategory.OFFER;
        }
        if (found(INTERVIEW, text) && found(INVITE_OR_SCHEDULE, text)) {
            return EmailCategory.INTERVIEW_INVITATION;
        }
        if (found(CONFIRMATION, text)) {
            return EmailCategory.APPLICATION_CONFIRMATION;
        }
        if (found(ASSESSMENT, text)) {
            return EmailCategory.ASSESSMENT_REQUEST;
        }
        if (found(SCREENING, text) || (found(PHONE, text) && found(SCREEN, text))) {
            return EmailCategory.SCREENING_CALL;
        }
        if (found(OFFER, text) && found(JOB_OR_POSITION, text)) {
            return EmailCategory.OFFER;
        }
        return EmailCategory.APPLICATION_CONFIRMATION;
    }

    /**
     * Guesses where the application was made. The sender decides first. The body only
     * counts when it says "via X" or "through X", because a plain mention ("Follow us on
     * LinkedIn", "we are indeed pleased") says nothing about the source.
     *
     * @param from sender header; may be null
     * @param body plain-text body; may be null
     * @return a job board name, "Company Website" for a no-reply sender, else "Direct Email"
     */
    public static String provider(String from, String body) {
        String sender = join(from);
        for (Map.Entry<String, Pattern> entry : PROVIDER_IN_SENDER.entrySet()) {
            if (found(entry.getValue(), sender)) {
                return entry.getKey();
            }
        }
        String text = join(body);
        for (Map.Entry<String, Pattern> entry : PROVIDER_IN_BODY.entrySet()) {
            if (found(entry.getValue(), text)) {
                return entry.getKey();
            }
        }
        return found(NO_REPLY, sender) ? "Company Website" : "Direct Email";
    }

    private static boolean found(Pattern pattern, String text) {
        return pattern.matcher(text).find();
    }

    /** Joins the non-null parts with spaces and straightens typographic apostrophes. */
    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                sb.append(part).append(' ');
            }
        }
        return sb.toString().replace('’', '\'');
    }

    /**
     * Builds one case-insensitive pattern that matches any of the keywords as whole words.
     * A space in a keyword matches any run of white space; a trailing {@code *} matches
     * the rest of the word.
     */
    static Pattern words(String... keywords) {
        StringBuilder alternatives = new StringBuilder();
        for (String keyword : keywords) {
            boolean prefix = keyword.endsWith("*");
            String base = prefix ? keyword.substring(0, keyword.length() - 1) : keyword;
            StringBuilder one = new StringBuilder();
            for (String word : base.trim().split("\\s+")) {
                if (one.length() > 0) {
                    one.append("\\s+");
                }
                one.append(Pattern.quote(word));
            }
            if (prefix) {
                one.append("[\\p{L}\\p{N}]*");
            }
            if (alternatives.length() > 0) {
                alternatives.append('|');
            }
            alternatives.append(one);
        }
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
