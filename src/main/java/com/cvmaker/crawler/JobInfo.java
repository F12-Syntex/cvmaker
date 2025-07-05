package com.cvmaker.crawler;

/**
 * Model class for job information.
 */
public class JobInfo {

    private String title = "";
    private String company = "";
    private String location = "";
    private String jobId = "";
    private String url = "";

    public JobInfo() {
    }

    public JobInfo(String title, String company) {
        this.title = title;
        this.company = company;
    }

    public JobInfo(String title, String company, String location) {
        this.title = title;
        this.company = company;
        this.location = location;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "JobInfo{"
                + "title='" + title + '\''
                + ", company='" + company + '\''
                + ", location='" + location + '\''
                + '}';
    }
}
