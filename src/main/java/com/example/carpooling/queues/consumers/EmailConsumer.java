package com.example.carpooling.queues.consumers;

import com.example.carpooling.dto.EmailDto;
import com.example.carpooling.queues.producers.EmaildlqProducer;
import com.example.carpooling.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {
    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);
    @Autowired
    private EmailService emailService;

    @Autowired
    private EmaildlqProducer emaildlqProducer;

    @RabbitListener(queues = "email-queue")
    public void listen(EmailDto emailDto) {
        try {
            emailService.sendEmergencyEmail(emailDto.getEmail(), emailDto.getSubject(), emailDto.getBody());
            log.info("Email sent successfully to: {}", emailDto.getEmail());
        } catch (Exception e) {
            log.error("Failed to send email to {}. Moving to DLQ.", emailDto.getEmail(), e);
            emaildlqProducer.sendDlq(emailDto);
            log.info("email added to dlq");
        }
    }
}
