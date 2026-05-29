package com.example.carpooling.services;

import com.example.carpooling.dto.EmailDto;
import com.example.carpooling.queues.producers.EmailProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class VerificationService {
    private static final long OTP_TTL_SECONDS = 600L;

    @Autowired
    private RedisService redisService;

    @Autowired
    private EmailProducer emailProducer;

    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public void sendOtp(String email) {
        String otp = generateOtp();
        String body = "Your otp for verfication of email in CarpoolingConnect : " + otp;
        EmailDto emailDto = new EmailDto(email, "Otp for email verification", body);
        redisService.set(email, otp, OTP_TTL_SECONDS);

        emailProducer.sendEmail(emailDto);
    }

    public boolean validateOtp(String email, String otp) {
        String dbotp = redisService.get(email, String.class);
        if (dbotp == null || !dbotp.equals(otp))
            return false;
        return true;
    }
}
