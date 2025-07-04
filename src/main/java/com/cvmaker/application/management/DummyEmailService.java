package com.cvmaker.application.management;

import java.util.ArrayList;
import java.util.List;

public class DummyEmailService {
    
    public static class DummyEmail {
        private final String id;
        private final String subject;
        private final String from;
        private final String date;
        private final String body;

        public DummyEmail(String id, String subject, String from, String date, String body) {
            this.id = id;
            this.subject = subject;
            this.from = from;
            this.date = date;
            this.body = body;
        }

        public String getId() { return id; }
        public String getSubject() { return subject; }
        public String getFrom() { return from; }
        public String getDate() { return date; }
        public String getBody() { return body; }
    }

    public List<DummyEmail> generateDummyEmails() {
        List<DummyEmail> dummyEmails = new ArrayList<>();

        // Job-related emails
        dummyEmails.add(new DummyEmail("dummy001", "Application Received - Software Engineer Position",
                "noreply@techcorp.com", "2024-06-15",
                "Thank you for applying to the Software Engineer position at TechCorp. We have received your application and will review it within 5-7 business days."));

        dummyEmails.add(new DummyEmail("dummy002", "Your application for Data Scientist role",
                "careers@datacompany.com", "2024-06-18",
                "Dear Candidate, we have received your application for the Data Scientist position. Our team will review your qualifications and contact you if there's a match."));

        // Non-job-related emails
        dummyEmails.add(new DummyEmail("dummy003", "Your Amazon order has shipped",
                "no-reply@amazon.com", "2024-06-16",
                "Good news! Your order #123-456789 has shipped and will arrive by June 20th. Track your package using the link below."));

        dummyEmails.add(new DummyEmail("dummy004", "Weekly Newsletter - Tech News",
                "newsletter@technews.com", "2024-06-17",
                "This week in tech: New AI developments, startup funding rounds, and the latest gadget reviews. Don't miss our exclusive interview with a Silicon Valley CEO."));

        // More job-related emails
        dummyEmails.add(new DummyEmail("dummy005", "Interview Invitation - Frontend Developer",
                "hr@webstartup.com", "2024-06-20",
                "We would like to invite you for an interview for the Frontend Developer position. Please reply with your availability for next week. The interview will be conducted via Zoom."));

        dummyEmails.add(new DummyEmail("dummy006", "Phone screening for Backend Engineer role",
                "recruiting@bigtech.com", "2024-06-22",
                "Hi! We're impressed with your profile and would like to schedule a 30-minute phone screening for the Backend Engineer position. Are you available this Thursday at 2 PM EST?"));

        // Continue with remaining dummy emails...
        dummyEmails.add(new DummyEmail("dummy007", "Your bank statement is ready",
                "statements@mybank.com", "2024-06-19",
                "Your monthly statement for Account ending in 1234 is now available. Log in to your online banking to view and download your statement."));

        dummyEmails.add(new DummyEmail("dummy008", "Job Offer - Senior Java Developer",
                "offers@javacompany.com", "2024-07-01",
                "Congratulations! We are pleased to extend an offer for the Senior Java Developer position. Starting salary: $95,000. Please review the attached offer letter and respond by July 5th."));

        dummyEmails.add(new DummyEmail("dummy009", "Interview Confirmation - UX Designer",
                "design@uxstudio.com", "2024-07-02",
                "This confirms your interview for the UX Designer position scheduled for July 8th at 10:00 AM. Location: 123 Design Street, Suite 400. Please bring your portfolio."));

        dummyEmails.add(new DummyEmail("dummy010", "Coding Assessment - Full Stack Developer",
                "tech@codingcompany.com", "2024-07-03",
                "As the next step in our hiring process, please complete the attached coding assessment. You have 48 hours to submit your solution. The assessment focuses on React and Node.js."));

        return dummyEmails;
    }
}