package com.homestay3.homestaybackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.PoiSuggestionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiSearchServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;

    PoiSearchService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new PoiSearchService(restTemplate, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "amapApiKey", "test-key");
    }

    @Test
    void emptyResultsAreNotCached() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}");

        List<PoiSuggestionDTO> results = service.searchPoiSuggestions("故宫", null, 10);

        assertThat(results).isEmpty();
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void cacheKeyIncludesLimit() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(
                "{\"status\":\"1\",\"tips\":[{\"id\":\"B1\",\"name\":\"故宫\",\"location\":\"116.397,39.918\"}]}");

        assertThat(service.searchPoiSuggestions("故宫", null, 1)).hasSize(1);

        verify(valueOperations).get("map:poi:all:1:故宫");
        verify(valueOperations).set(eq("map:poi:all:1:故宫"), anyString(), anyLong(), eq(TimeUnit.HOURS));
    }
}
