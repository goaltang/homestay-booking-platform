package com.homestay3.homestaybackend.service.search.impl;

import com.homestay3.homestaybackend.dto.HomestaySearchRequest;
import com.homestay3.homestaybackend.dto.HomestaySearchResultDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.HomestaySearchService;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES 搜索路径集成测试：验证 Elasticsearch 可用时的搜索行为。
 * 使用 Testcontainers 启动真实 ES 实例。
 * 若当前环境无 Docker，则自动跳过全部测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class HomestaySearchServiceEsIntegrationTest {

    static ElasticsearchContainer elasticsearch;

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (elasticsearch != null && elasticsearch.isRunning()) {
            registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        }
    }

    @Autowired
    private HomestaySearchService homestaySearchService;

    @Autowired
    private HomestayIndexingService homestayIndexingService;

    @Autowired
    private HomestayRepository homestayRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeAll
    static void beforeAll() {
        Assumptions.assumeTrue(isDockerAvailable(), "跳过 ES 集成测试：当前环境未安装或启动 Docker");
        elasticsearch = new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.5.0"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node");
        elasticsearch.start();
    }

    @AfterAll
    static void afterAll() {
        if (elasticsearch != null && elasticsearch.isRunning()) {
            elasticsearch.stop();
        }
    }

    @BeforeEach
    void setUp() {
        homestayRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void whenElasticsearchAvailable_shouldIndexAndSearchByKeyword() {
        // 1. 创建测试用户
        User owner = User.builder()
                .username("testowner")
                .email("owner@test.com")
                .password("password")
                .build();
        owner = userRepository.save(owner);

        // 2. 创建测试房源
        Homestay homestay = Homestay.builder()
                .title("海边度假别墅")
                .type("VILLA")
                .price(BigDecimal.valueOf(800))
                .status(HomestayStatus.ACTIVE)
                .maxGuests(6)
                .minNights(1)
                .addressDetail("海滨大道1号")
                .provinceCode("440000")
                .cityCode("440300")
                .provinceText("广东省")
                .cityText("深圳市")
                .owner(owner)
                .build();
        homestay = homestayRepository.save(homestay);

        // 3. 全量重建索引
        int indexedCount = homestayIndexingService.rebuildIndex();
        assertEquals(1, indexedCount, "应索引 1 条房源");

        // 4. 通过 ES 搜索关键词
        HomestaySearchRequest request = HomestaySearchRequest.builder()
                .keyword("海边")
                .page(0)
                .size(12)
                .build();

        Page<HomestaySearchResultDTO> resultPage = homestaySearchService.searchHomestayPage(request);

        // 5. 断言
        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements(), "应返回 1 条结果");
        assertEquals("海边度假别墅", resultPage.getContent().get(0).getTitle());
    }

    @Test
    void whenElasticsearchAvailable_shouldRespectPriceFilter() {
        // 1. 创建测试用户
        User owner = User.builder()
                .username("testowner2")
                .email("owner2@test.com")
                .password("password")
                .build();
        owner = userRepository.save(owner);

        // 2. 创建两个不同价格的房源
        Homestay cheap = Homestay.builder()
                .title("经济型民宿")
                .type("APARTMENT")
                .price(BigDecimal.valueOf(200))
                .status(HomestayStatus.ACTIVE)
                .maxGuests(2)
                .minNights(1)
                .addressDetail("市中心")
                .provinceCode("440000")
                .cityCode("440300")
                .provinceText("广东省")
                .cityText("深圳市")
                .owner(owner)
                .build();

        Homestay expensive = Homestay.builder()
                .title("豪华别墅")
                .type("VILLA")
                .price(BigDecimal.valueOf(2000))
                .status(HomestayStatus.ACTIVE)
                .maxGuests(10)
                .minNights(1)
                .addressDetail("山顶")
                .provinceCode("440000")
                .cityCode("440300")
                .provinceText("广东省")
                .cityText("深圳市")
                .owner(owner)
                .build();

        homestayRepository.saveAll(List.of(cheap, expensive));

        // 3. 重建索引
        int indexedCount = homestayIndexingService.rebuildIndex();
        assertEquals(2, indexedCount);

        // 4. 带价格过滤搜索
        HomestaySearchRequest request = HomestaySearchRequest.builder()
                .keyword("民宿")
                .minPrice(BigDecimal.valueOf(0))
                .maxPrice(BigDecimal.valueOf(500))
                .page(0)
                .size(12)
                .build();

        Page<HomestaySearchResultDTO> resultPage = homestaySearchService.searchHomestayPage(request);

        // 5. 断言：只返回便宜的房源
        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements(), "价格过滤后应只返回 1 条结果");
        assertEquals("经济型民宿", resultPage.getContent().get(0).getTitle());
    }
}
