package com.apps.ecommerce.service;

/**
 * How a message leaves the application. Two implementations exist and exactly one
 * is active, chosen by app.mail.provider:
 *
 *   smtp   - SmtpMailService, the default. Talks to Mailpit locally.
 *   resend - ResendMailService, an HTTPS call. Required on hosts that block
 *            outbound SMTP ports, which Render does.
 *
 * Callers do not care which; UserRegisteredListener just asks for a send.
 */
public interface MailService {

    void send(String to, String subject, String text);
}
