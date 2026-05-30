package com.example.carpooling.controller;

import com.example.carpooling.entities.User;
import com.example.carpooling.enums.Role;
import com.example.carpooling.repositories.UserRepository;
import com.example.carpooling.services.AnalyticsService;
import com.example.carpooling.services.UserDetailsServiceImpl;
import com.example.carpooling.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {

  private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);
  @Value("${GOOGLE_CLIENT_ID}")
  private String clientId;

  @Value("${GOOGLE_CLIENT_SECRET}")
  private String clientSecret;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${app.google-redirect-uri}")
  private String googleRedirectUri;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

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

  @GetMapping("/start")
  public ResponseEntity<Void> startGoogleLogin() {
    String authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth"
        + "?client_id=" + clientId
        + "&redirect_uri=" + googleRedirectUri
        + "&response_type=code"
        + "&scope=openid%20email%20profile"
        + "&access_type=offline"
        + "&prompt=consent";

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, authorizationUrl)
        .build();
  }

  @GetMapping("/callback")
  public ResponseEntity<?> handleGoogleCallback(@RequestParam String code) {
    try {
      String tokenEndpoint = "https://oauth2.googleapis.com/token";
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("code", code);
      params.add("client_id", clientId);
      params.add("client_secret", clientSecret);
      params.add("redirect_uri", googleRedirectUri);
      params.add("grant_type", "authorization_code");

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
      ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenEndpoint, request, String.class);

      if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
        log.error("Google token exchange failed. Status={}, Body={}", tokenResponse.getStatusCode(),
            tokenResponse.getBody());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google token exchange failed.");
      }

      JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
      String idToken = tokenJson.path("id_token").asText(null);
      if (idToken == null || idToken.isBlank()) {
        log.error("Google token exchange returned no id_token. Body={}", tokenResponse.getBody());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google login did not return an ID token.");
      }
      String userInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
      ResponseEntity<String> userInfoResponse = restTemplate.getForEntity(userInfoUrl, String.class);

      if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
        JsonNode userInfoJson = objectMapper.readTree(userInfoResponse.getBody());
        String email = userInfoJson.path("email").asText(null);
        if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google login did not return an email.");
        }

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

        String jwtToken = jwtUtil.generateToken(user.getId().toHexString(), user.getRole().name());
        String successUrl = normalizeUrl(frontendUrl) + "/oauth-success?token=" + jwtToken;

        return ResponseEntity.status(302)
            .header("Location", successUrl)
            .build();
      }

      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User info fetch failed.");

    } catch (Exception e) {
      log.error("Exception occurred while handleGoogleCallback " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private String normalizeUrl(String value) {
    if (value == null) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

}
