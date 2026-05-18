package com.example.carpooling.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.net.URI;

@Configuration
public class RedisConfig {

    @Value("${REDIS_HOST}")
    private String redisHost;

    @Value("${REDIS_PASSWORD}")
    private String redisPassword;

    @Value("${REDIS_PORT}")
    private int redisPort;

    @Value("${spring.redis.ssl:false}")
    private boolean redisSsl;

    @Value("${REDIS_URI:}")
    private String redisUri;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        JedisClientConfiguration jedisClientConfiguration = redisSsl
                ? JedisClientConfiguration.builder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .useSsl()
                        .build()
                : JedisClientConfiguration.builder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .build();

        RedisStandaloneConfiguration redisStandaloneConfiguration = createRedisConfiguration();
        return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration);
    }

    private RedisStandaloneConfiguration createRedisConfiguration() {
        if (redisUri != null && !redisUri.isBlank()) {
            URI uri = URI.create(redisUri);
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
            configuration.setHostName(uri.getHost());
            configuration.setPort(uri.getPort() > 0 ? uri.getPort() : redisPort);

            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                configuration.setPassword(org.springframework.data.redis.connection.RedisPassword
                        .of(userInfo.substring(userInfo.indexOf(':') + 1)));
            } else if (redisPassword != null && !redisPassword.isBlank()) {
                configuration.setPassword(org.springframework.data.redis.connection.RedisPassword.of(redisPassword));
            }

            return configuration;
        }

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isBlank()) {
            configuration.setPassword(org.springframework.data.redis.connection.RedisPassword.of(redisPassword));
        }
        return configuration;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);

        return redisTemplate;

    }
}
