package com.example.carpooling.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${EMAIL}")
    private String email;

    @Value("${app.mail.provider:sendgrid}")
    private String mailProvider;

    @Value("${app.mail.from:${EMAIL}}")
    private String fromEmail;

    @Value("${app.sendgrid.api-key:}")
    private String sendgridApiKey;


    public void sendEmergencyEmail(String to, String subject, String message) {
        if ("sendgrid".equalsIgnoreCase(mailProvider)) {
            sendWithSendGrid(to, subject, message);
            return;
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(email);
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(message);

        mailSender.send(mail);
    }

    private void sendWithSendGrid(String to, String subject, String message) {
        if (sendgridApiKey == null || sendgridApiKey.isBlank()) {
            throw new IllegalStateException("SENDGRID_API_KEY is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(sendgridApiKey);

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", fromEmail),
                "subject", subject,
                "content", List.of(Map.of("type", "text/plain", "value", message))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.sendgrid.com/v3/mail/send",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("SendGrid returned non-success status: " + response.getStatusCode());
        }
    }
}
