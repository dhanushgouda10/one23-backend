package com.one23.one23.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

// Sends the "Welcome to One23" email after a user signs up.
// SMTP settings come from spring.mail.* in application.properties.
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    // "From" address (for Gmail SMTP, same as the SMTP username)
    @Value("${app.mail.from}")
    private String fromAddress;

    // Link used by the "Login Now" button
    @Value("${app.frontend.login-url}")
    private String loginUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Sends the welcome email in the background (@Async) so signup is not slowed down.
    // It never throws: if sending fails we only log a warning, and signup still succeeds.
    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to One23 🚗");
            helper.setText(buildWelcomeEmailHtml(fullName), true); // true = body is HTML

            mailSender.send(mimeMessage);
            logger.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            // Usually: SMTP username/password not set in .env, or a normal Gmail
            // password used instead of an App Password. Signup must still succeed.
            logger.warn("Could not send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    // Simple HTML email body. Inline styles because most email clients ignore <style> tags.
    private String buildWelcomeEmailHtml(String fullName) {
        // htmlEscape so a name like "<b>Bob</b>" is shown as text, not rendered as HTML
        String safeName = (fullName == null || fullName.isBlank()) ? "there" : HtmlUtils.htmlEscape(fullName);

        return """
                <div style="font-family: Arial, sans-serif; background-color: #f6f7f8; padding: 32px;">
                  <div style="max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; text-align: center;">
                    <h1 style="color: #10c95a; margin-bottom: 4px;">Welcome to One23 🎉</h1>
                    <p style="font-size: 16px; color: #1a1a2e; margin-top: 0;">Hi %s,</p>
                    <p style="font-size: 15px; color: #4b5563; line-height: 1.6;">
                      Thank you for signing up! We're excited to have you on board.
                      With One23 you can quickly find and join rides with people
                      heading the same way as you.
                    </p>
                    <a href="%s"
                       style="display: inline-block; margin-top: 20px; padding: 12px 28px;
                              background-color: #10c95a; color: #ffffff; text-decoration: none;
                              border-radius: 8px; font-weight: bold;">
                      Login Now
                    </a>
                    <p style="font-size: 12px; color: #9ca3af; margin-top: 28px;">
                      If you did not create this account, you can safely ignore this email.
                    </p>
                  </div>
                </div>
                """.formatted(safeName, loginUrl);
    }
}
