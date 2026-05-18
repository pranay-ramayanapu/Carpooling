package com.example.carpooling.services;

import com.example.carpooling.entities.Ride;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RedisService {
    private static final Logger log = LoggerFactory.getLogger(RedisService.class);
    private static final String popularityKey = "popularity";

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> entityClass) {
        try {
            Object o = redisTemplate.opsForValue().get(key);
            return objectMapper.readValue(o.toString(), entityClass);
        } catch (Exception e) {
            log.error(e.getMessage());
//            e.printStackTrace();
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        try {
            Object json = redisTemplate.opsForValue().get(key);
            if (json==null) return null;

            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(json.toString(), type);
        } catch (Exception e) {
            log.error(e.getMessage());
//            e.printStackTrace();
            return null;
        }
    }


    public void set(String key, Object o, Long ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(o);
            redisTemplate.opsForValue().set(key, jsonValue, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(e.getMessage());
//            e.printStackTrace();
        }
    }

    public Long incrCount(String key){
        return redisTemplate.opsForValue().increment(key);
    }

    public void setExpiry(String key,Long ttl){
        redisTemplate.opsForValue().getAndExpire(key,Duration.ofMinutes(ttl));
    }

    public void incrementCityCount(String city) {
        redisTemplate.opsForZSet().incrementScore(popularityKey, city, 1.0);
    }

    public Set<String> getTop5Cities() {
//        delete(popularityKey);
        Set<ZSetOperations.TypedTuple<Object>> topCitiesWithScores =
                redisTemplate.opsForZSet().reverseRangeWithScores(popularityKey, 0, 4);

        if (topCitiesWithScores == null || topCitiesWithScores.isEmpty()) {
            return Collections.emptySet();
        }

        return topCitiesWithScores.stream()
                .map(tuple -> tuple.getValue() != null ? tuple.getValue().toString() : null)
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.toSet());
    }


    public List<String> getToRemoveCity() {
        Set<ZSetOperations.TypedTuple<Object>> topCitiesWithScores =
                redisTemplate.opsForZSet().reverseRangeWithScores(popularityKey, 5, 5);

        if (topCitiesWithScores == null) {
            return Collections.emptyList();
        }

        return topCitiesWithScores.stream()
                .map(tuple -> tuple.getValue() != null ? tuple.getValue().toString() : null)
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    public Long getLength(){
        return redisTemplate.opsForZSet().zCard(popularityKey);
    }


    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error(e.getMessage());
//            e.printStackTrace();
        }
    }

}
