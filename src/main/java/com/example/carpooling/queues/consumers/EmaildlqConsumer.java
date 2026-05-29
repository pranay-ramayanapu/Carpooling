package com.example.carpooling.queues.consumers;

import com.example.carpooling.dto.EmailDto;
import com.example.carpooling.services.EmailService;
import com.example.carpooling.services.FailedEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmaildlqConsumer {

    private static final long DLQ_RETRY_DELAY_MS = 30_000L;

    private static final Logger log = LoggerFactory.getLogger(EmaildlqConsumer.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private FailedEmailService failedEmailService;

    @RabbitListener(queues = "email-dlq")
    public void listen(EmailDto emailDto) {
        try {
            Thread.sleep(DLQ_RETRY_DELAY_MS);
            emailService.sendEmergencyEmail(emailDto.getEmail(), emailDto.getSubject(), emailDto.getBody());
            log.info("Email retry success (DLQ): {}", emailDto.getEmail());
        } catch (Exception e) {
            log.error("Final email retry failed (DLQ): {}", emailDto.getEmail(), e);
            failedEmailService.saveFailedEmail(emailDto, e.getMessage());
        }
    }
}
