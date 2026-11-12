package com.cvmaker.application.management.sheets;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.cvmaker.application.management.ApplicationStatus;
import com.cvmaker.application.management.JobApplicationData;

/**
 * Decides which of two records about the same company is the one to show. Pure: it only reads
 * the two records.
 */
public final class ApplicationRecency {

    private ApplicationRecency() {
    }

    /**
     * Tells whether a record should replace the one currently shown.
     *
     * <p>The first of these that both records have, and that differs, decides: the email
     * timestamp, the last-updated time, the processed time, the application date. When none
     * decides, the record with the higher {@link ApplicationStatus#rank() status rank} wins.
     * A tie keeps the current record.
     *
     * @param candidate the record that might replace the current one
     * @param current the record currently shown
     * @return true when the candidate should replace the current record
     */
    public static boolean isNewer(JobApplicationData candidate, JobApplicationData current) {
        int byTime = compareTimestamps(candidate, current, JobApplicationData::getEmailTimestamp);
        if (byTime == 0) {
            byTime = compareTimestamps(candidate, current, JobApplicationData::getLastUpdated);
        }
        if (byTime == 0) {
            byTime = compareTimestamps(candidate, current, JobApplicationData::getProcessedTimestamp);
        }
        if (byTime == 0) {
            Optional<LocalDate> candidateDate = DateParsing.parseDate(candidate.getDate());
            Optional<LocalDate> currentDate = DateParsing.parseDate(current.getDate());
            if (candidateDate.isPresent() && currentDate.isPresent()) {
                byTime = candidateDate.get().compareTo(currentDate.get());
            }
        }
        if (byTime != 0) {
            return byTime > 0;
        }
        return ApplicationStatus.isMoreAdvanced(candidate.getApplicationStatus(), current.getApplicationStatus());
    }

    /**
     * Keeps one record per company: the newest according to {@link #isNewer}. Company names are
     * compared ignoring case and surrounding spaces. Records that are not job related, or have
     * no company name, are left out.
     *
     * @param applications the records, in any order
     * @return the chosen records by lower-case company name, in order of first appearance
     */
    public static Map<String, JobApplicationData> latestPerCompany(Collection<JobApplicationData> applications) {
        Map<String, JobApplicationData> latest = new LinkedHashMap<>();
        for (JobApplicationData application : applications) {
            if (application == null || !application.isJobRelated()) {
                continue;
            }
            String company = application.getCompanyName();
            if (company == null || company.trim().isEmpty()) {
                continue;
            }
            String key = company.trim().toLowerCase(Locale.ROOT);
            JobApplicationData current = latest.get(key);
            if (current == null || isNewer(application, current)) {
                latest.put(key, application);
            }
        }
        return latest;
    }

    /** Compares one timestamp field of both records; 0 when either is missing or unparsable. */
    private static int compareTimestamps(JobApplicationData candidate, JobApplicationData current,
            Function<JobApplicationData, String> field) {
        Optional<Instant> candidateTime = DateParsing.parseTimestamp(field.apply(candidate));
        Optional<Instant> currentTime = DateParsing.parseTimestamp(field.apply(current));
        if (candidateTime.isEmpty() || currentTime.isEmpty()) {
            return 0;
        }
        return candidateTime.get().compareTo(currentTime.get());
    }
}
