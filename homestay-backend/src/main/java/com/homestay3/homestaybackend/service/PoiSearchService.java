package com.homestay3.homestaybackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.PoiSuggestionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiSearchService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.map.amap.api-key:}")
    private String amapApiKey;

    private static final String POI_CACHE_PREFIX = "map:poi:";
    private static final long POI_CACHE_TTL_HOURS = 24 * 7; // 7天

    public List<PoiSuggestionDTO> searchPoiSuggestions(String keyword, String city, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        // 条数也要进缓存键：回车兜底只取 1 条，不能让它占住下拉联想的 10 条结果
        String cacheKey = POI_CACHE_PREFIX + (city != null ? city : "all") + ":" + limit + ":" + keyword;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached.toString(), new TypeReference<List<PoiSuggestionDTO>>() {});
            } catch (Exception e) {
                log.warn("POI cache deserialize failed", e);
            }
        }

        List<PoiSuggestionDTO> results = fetchFromAmap(keyword, city, limit);
        if (results.isEmpty()) {
            // 空结果可能来自 Key 未配置、网络失败或限流，不缓存，避免故障修复后仍持续 7 天返回空
            return results;
        }

        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(cacheKey, json, POI_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("POI cache write failed", e);
        }

        return results;
    }

    private List<PoiSuggestionDTO> fetchFromAmap(String keyword, String city, Integer limit) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl("https://restapi.amap.com/v3/assistant/inputtips")
                    .queryParam("key", amapApiKey)
                    .queryParam("keywords", keyword)
                    .queryParam("datatype", "poi");

            if (StringUtils.hasText(city)) {
                builder.queryParam("city", city);
            }

            URI uri = builder.build().encode().toUri();

            String response = restTemplate.getForObject(uri, String.class);
            if (!StringUtils.hasText(response)) {
                log.warn("Amap inputtips returned empty response for keyword={}", keyword);
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("Amap inputtips returned non-success for keyword={}, info={}", keyword, root.path("info").asText());
                return Collections.emptyList();
            }

            JsonNode tips = root.path("tips");
            if (!tips.isArray()) {
                return Collections.emptyList();
            }

            List<PoiSuggestionDTO> results = new ArrayList<>();
            int maxResults = limit != null && limit > 0 ? limit : 10;

            for (JsonNode tip : tips) {
                if (results.size() >= maxResults) {
                    break;
                }

                String location = tip.path("location").asText();
                if (StringUtils.hasText(location) && location.contains(",")) {
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        try {
                            double lng = Double.parseDouble(parts[0].trim());
                            double lat = Double.parseDouble(parts[1].trim());

                            PoiSuggestionDTO dto = PoiSuggestionDTO.builder()
                                    .id(tip.path("id").asText())
                                    .name(tip.path("name").asText())
                                    .address(tip.path("address").asText())
                                    .district(tip.path("district").asText())
                                    .latitude(lat)
                                    .longitude(lng)
                                    .build();

                            results.add(dto);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid location format in tip: {}", location);
                        }
                    }
                }
            }

            return results;
        } catch (Exception e) {
            log.warn("Amap inputtips failed for keyword={}: {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }
}
