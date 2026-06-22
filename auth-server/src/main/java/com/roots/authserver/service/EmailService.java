package com.roots.authserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    // Auto-configured by Spring Boot in every profile from spring.mail.* (the concrete
    // bean is a JavaMailSenderImpl), so it is always present. Whether mail is actually
    // sent is gated separately by emailSender.enabled (off in dev/test, on in qa/prod).
    private final MailSender mailSender;

    // Field injection (not a constructor arg) so @RequiredArgsConstructor keeps wiring
    // only mailSender; a final field here would land in the Lombok constructor without
    // its @Value annotation and fail to bind. Defaults to false so a missing/misconfigured
    // profile fails closed (no accidental sends). Disabled under the test profile.
    @Value("${emailSender.enabled:false}")
    private boolean emailSenderEnabled;

    // Dev-only debugging aid: when email is disabled, log the token value at INFO so it can
    // be copied from the console (dev/test tokens are in-memory/short-lived). Enabled only in
    // the dev profile; defaults to false so other profiles never leak token values to logs.
    @Value("${emailSender.logToken:false}")
    private boolean logToken;

    @Async
    public void sendOTTEmail(String to, String otp) {
        if (!emailSenderEnabled) {
            if (logToken) {
                log.info("Email sending disabled; OTT for {} is {}", to, otp);
            } else {
                log.warn("Email sending disabled (emailSender.enabled=false); skipping OTT email to {}", to);
            }
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your One-Time Password for Roots");
        message.setText("Your One-Time Password is: " + otp);
        mailSender.send(message);
    }

    @Async
    public void sendMagicLinkEmail(String to, String magicLink) {
        if (!emailSenderEnabled) {
            if (logToken) {
                log.info("Email sending disabled; magic link for {} is {}", to, magicLink);
            } else {
                log.warn("Email sending disabled (emailSender.enabled=false); skipping magic-link email to {}", to);
            }
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify Email For Created Account @ Roots");
        message.setText("Proceed to login with " + magicLink);
        mailSender.send(message);
    }
}
