package com.roots.authserver.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendOTTEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your One-Time Password for Roots");
        message.setText("Your One-Time Password is: " + otp);
        mailSender.send(message);
    }

    @Async
    public void sendMagicLinkEmail(String to, String magicLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify Email For Created Account @ Roots");
        message.setText("Proceed to login with " + magicLink);
        mailSender.send(message);
    }
}
