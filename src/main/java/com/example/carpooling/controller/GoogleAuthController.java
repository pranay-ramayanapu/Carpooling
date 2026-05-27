package com.example.carpooling.controller;

import com.example.carpooling.entities.User;
import com.example.carpooling.enums.Role;
import com.example.carpooling.repositories.UserRepository;
import com.example.carpooling.services.AnalyticsService;
import com.example.carpooling.services.UserDetailsServiceImpl;
import com.example.carpooling.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {

  private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);
  @Value("${GOOGLE_CLIENT_ID}")
  private String clientId;

  @Value("${GOOGLE_CLIENT_SECRET}")
  private String clientSecret;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  UserDetailsServiceImpl userDetailsService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private AnalyticsService analyticsService;

  @GetMapping("/callback")
  public ResponseEntity<?> handleGoogleCallback(@RequestParam String code) {
    try {
      String tokenEndpoint = "https://oauth2.googleapis.com/token";
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("code", code);
      params.add("client_id", clientId);
      params.add("client_secret", clientSecret);
      params.add("redirect_uri", "https://carpooling-5as1.onrender.com/auth/google/callback");
      params.add("grant_type", "authorization_code");

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
      ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, request, Map.class);

      String idToken = (String) tokenResponse.getBody().get("id_token");
      String userInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
      ResponseEntity<Map> userInfoResponse = restTemplate.getForEntity(userInfoUrl, Map.class);

      if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
        Map<String, Object> userInfo = userInfoResponse.getBody();
        String email = (String) userInfo.get("email");

        User user = userRepository.findByEmail(email);
        if (user == null) {
          user = new User();
          user.setEmail(email);
          user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
          user.setRole(Role.RIDER);
          user.setRating(0);
          user.setRating_count(0);
          analyticsService.incUsers();
          userRepository.save(user);
        }

        // try {
        // userDetailsService.loadUserByUsername(email);
        // } catch (Exception e) {
        // User user = new User();
        // user.setEmail(email);
        // user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        // user.setRole(Role.RIDER);
        // user.setRating(0);
        // user.setRating_count(0);
        // analyticsService.incUsers();
        // userRepository.save(user);
        // }

        String jwtToken = jwtUtil.generateToken(user.getId().toHexString(), Role.RIDER.name());

        return ResponseEntity.status(302)
            .header("Location", "https://carpooling-frontend-iota.vercel.app/oauth-success?token=" + jwtToken)
            .build();
      }

      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User info fetch failed.");

    } catch (Exception e) {
      log.error("Exception occurred while handleGoogleCallback " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

}
