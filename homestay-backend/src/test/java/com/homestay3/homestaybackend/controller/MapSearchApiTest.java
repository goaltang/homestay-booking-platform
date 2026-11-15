package com.homestay3.homestaybackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.PoiSuggestionDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.PoiSearchService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 地图找房相关接口：匿名访问边界、分页正确性、视野结果上限与聚合计数。
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:map_search_api;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class MapSearchApiTest {

    /** 远离其他测试数据的一块视野，避免互相干扰 */
    private static final String BOUNDS =
            "\"northEastLat\":11,\"northEastLng\":171,\"southWestLat\":10,\"southWestLng\":170";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired HomestayRepository homestays;
    @Autowired EntityManager entityManager;
    @MockBean PoiSearchService poiSearchService;

    private String districtCode;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        districtCode = "T" + suffix;
        User owner = users.save(User.builder().username("map_owner_" + suffix)
                .email("map_owner_" + suffix + "@test.invalid").password("x").role("ROLE_HOST").enabled(true).build());

        for (int i = 1; i <= 5; i++) {
            homestays.save(listing(owner, "房源" + i, 100 * i, 10.1 + i * 0.1, 170.1 + i * 0.1, HomestayStatus.ACTIVE));
        }
        // 下架房源与无坐标房源不应出现在地图结果和聚合里
        homestays.save(listing(owner, "下架", 50, 10.5, 170.5, HomestayStatus.INACTIVE));
        Homestay noCoordinates = listing(owner, "无坐标", 60, 0, 0, HomestayStatus.ACTIVE);
        noCoordinates.setLatitude(null);
        noCoordinates.setLongitude(null);
        homestays.save(noCoordinates);

        // 让后续查询真正走数据库，而不是复用持久化上下文里的实体
        entityManager.flush();
        entityManager.clear();
    }

    private Homestay listing(User owner, String title, int price, double lat, double lng, HomestayStatus status) {
        Homestay homestay = new Homestay();
        homestay.setTitle(title);
        homestay.setType("公寓");
        homestay.setPrice(BigDecimal.valueOf(price));
        homestay.setMaxGuests(2);
        homestay.setMinNights(1);
        homestay.setStatus(status);
        homestay.setOwner(owner);
        homestay.setAddressDetail("测试地址");
        homestay.setProvinceCode("110000");
        homestay.setCityCode("110100");
        homestay.setDistrictCode(districtCode);
        homestay.setLatitude(BigDecimal.valueOf(lat));
        homestay.setLongitude(BigDecimal.valueOf(lng));
        homestay.setImages(new ArrayList<>(List.of("/a/" + title + "-1.jpg", "/a/" + title + "-2.jpg")));
        return homestay;
    }

    private JsonNode postJson(String path, String body) throws Exception {
        String response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    @Test
    void poiSuggestionsArePublicButGeocodeStillRequiresLogin() throws Exception {
        PoiSuggestionDTO suggestion = new PoiSuggestionDTO();
        suggestion.setName("故宫");
        when(poiSearchService.searchPoiSuggestions(eq("故宫"), any(), any())).thenReturn(List.of(suggestion));

        mvc.perform(get("/api/map/poi-suggestions").param("keyword", "故宫"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("故宫"));
        mvc.perform(get("/api/map/geocode").param("address", "故宫"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pagedSearchReturnsTheRequestedPageWithImagesAndTotal() throws Exception {
        JsonNode page = postJson("/api/homestays/search", "{\"districtCode\":\"" + districtCode
                + "\",\"page\":1,\"size\":2,\"sortBy\":\"price\",\"sortDirection\":\"asc\"}");

        assertThat(page.get("totalElements").asInt()).isEqualTo(6);
        JsonNode content = page.get("content");
        assertThat(content).hasSize(2);
        // 第 2 页（每页 2 条，按价格升序）：60 元无坐标房源排第 1 页，第 2 页是 200 和 300
        assertThat(new BigDecimal(content.get(0).get("price").asText())).isEqualByComparingTo("200");
        assertThat(new BigDecimal(content.get(1).get("price").asText())).isEqualByComparingTo("300");
        assertThat(content.get(0).get("images")).hasSize(2);
    }

    @Test
    void mapSearchRespectsRequestedSize() throws Exception {
        JsonNode all = postJson("/api/homestays/map-search", "{" + BOUNDS + ",\"size\":100}");
        assertThat(all).hasSize(5);

        JsonNode capped = postJson("/api/homestays/map-search", "{" + BOUNDS + ",\"size\":3}");
        assertThat(capped).hasSize(3);
    }

    @Test
    void clustersCountOnlyActiveListingsWithCoordinates() throws Exception {
        JsonNode clusters = postJson("/api/homestays/map-clusters", "{" + BOUNDS + ",\"zoom\":3}");

        int total = 0;
        for (JsonNode cluster : clusters) {
            total += cluster.get("count").asInt();
        }
        assertThat(total).isEqualTo(5);
    }
}
