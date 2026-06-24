package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.PriceCompetitivenessDTO;
import com.homestay3.homestaybackend.dto.SuggestedFeatureDTO;
import com.homestay3.homestaybackend.entity.Amenity;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.service.impl.HomestayFeatureAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomestayFeatureAnalysisServiceImpl 单元测试")
class HomestayFeatureAnalysisServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private HomestayRepository homestayRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private PriceAnalysisService priceAnalysisService;

    @InjectMocks
    private HomestayFeatureAnalysisServiceImpl featureAnalysisService;

    private Homestay baseHomestay;

    @BeforeEach
    void setUp() {
        baseHomestay = Homestay.builder()
                .id(1L)
                .title("测试房源")
                .type("APARTMENT")
                .price(new BigDecimal("300"))
                .maxGuests(4)
                .cityCode("SH")
                .cityText("上海")
                .districtCode("PD")
                .districtText("浦东新区")
                .addressDetail("测试地址")
                .provinceCode("SH")
                .provinceText("上海")
                .minNights(1)
                .status(com.homestay3.homestaybackend.model.HomestayStatus.ACTIVE)
                .build();

        // 默认 mock：无订单、无评价、价格分析返回空
        lenient().when(orderRepository.countByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                .thenReturn(0L);
        lenient().when(orderRepository.countByHomestayId(anyLong())).thenReturn(0L);
        lenient().when(orderRepository.findByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(reviewRepository.findLatestReviewsByHomestayId(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        lenient().when(priceAnalysisService.analyzePriceCompetitiveness(any(Homestay.class))).thenReturn(null);
    }

    private Amenity amenity(String value, String label, String icon) {
        Amenity amenity = new Amenity();
        amenity.setValue(value);
        amenity.setLabel(label);
        amenity.setIcon(icon);
        return amenity;
    }

    private Order order(Long id, String status, LocalDate checkInDate, LocalDateTime createdAt) {
        return Order.builder()
                .id(id)
                .status(status)
                .checkInDate(checkInDate)
                .checkOutDate(checkInDate.plusDays(1))
                .createdAt(createdAt)
                .build();
    }

    private Review review(int rating, String content) {
        return Review.builder()
                .rating(rating)
                .content(content)
                .isPublic(true)
                .deleted(false)
                .build();
    }

    private List<String> featureIds(List<SuggestedFeatureDTO> features) {
        return features.stream().map(SuggestedFeatureDTO::getFeatureId).collect(Collectors.toList());
    }

    @Nested
    @DisplayName("各维度分析单独触发")
    class DimensionTests {

        @Test
        @DisplayName("房源类型维度")
        void propertyTypeDimension() {
            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("PROPERTY_TYPE_APARTMENT");
            assertThat(features).anyMatch(f -> "家庭式公寓".equals(f.getTitle()));
        }

        @Test
        @DisplayName("价格竞争力维度")
        void priceDimension() {
            PriceCompetitivenessDTO priceAnalysis = PriceCompetitivenessDTO.builder()
                    .competitivenessLevel(PriceCompetitivenessDTO.PriceCompetitivenessLevel.HIGHLY_COMPETITIVE)
                    .priceDifferenceFromAverage(-20.0)
                    .comparisonScope("同城市同类型")
                    .build();
            when(priceAnalysisService.analyzePriceCompetitiveness(baseHomestay)).thenReturn(priceAnalysis);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("PRICE_HIGHLY_COMPETITIVE");
            assertThat(features).anyMatch(f -> "高性价比".equals(f.getTitle()));
        }

        @Test
        @DisplayName("设施组合维度")
        void amenityCombinationDimension() {
            baseHomestay.setAmenities(Set.of(
                    amenity("HIGH_SPEED_WIFI", "高速WiFi", "Connection"),
                    amenity("DESK", "办公桌", "Monitor")
            ));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("BUSINESS_WORKSPACE");
            assertThat(features).anyMatch(f -> "商务办公".equals(f.getTitle()));
        }

        @Test
        @DisplayName("单一设施维度")
        void singleAmenityDimension() {
            baseHomestay.setAmenities(Set.of(amenity("PARKING", "停车位", "Van")));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("AMENITY_PARKING");
        }

        @Test
        @DisplayName("位置优势维度")
        void locationDimension() {
            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("LOCATION_SH");
        }

        @Test
        @DisplayName("预订活跃度维度")
        void bookingActivityDimension() {
            when(orderRepository.countByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                    .thenReturn(5L);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("POPULAR_BOOKING");
            assertThat(features).anyMatch(f -> f.getDescription().contains("120天"));
        }

        @Test
        @DisplayName("周末流行度维度")
        void weekendPopularityDimension() {
            LocalDateTime now = LocalDateTime.now();
            List<Order> orders = Arrays.asList(
                    order(1L, OrderStatus.COMPLETED.name(), LocalDate.now().with(java.time.DayOfWeek.FRIDAY), now.minusDays(5)),
                    order(2L, OrderStatus.COMPLETED.name(), LocalDate.now().with(java.time.DayOfWeek.FRIDAY), now.minusDays(12)),
                    order(3L, OrderStatus.COMPLETED.name(), LocalDate.now().with(java.time.DayOfWeek.MONDAY), now.minusDays(20))
            );
            when(orderRepository.findByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                    .thenReturn(orders);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("WEEKEND_POPULAR");
            verify(orderRepository).findByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any());
        }

        @Test
        @DisplayName("用户评价维度 - 高评分")
        void reviewDimensionHighRating() {
            List<Review> reviews = Arrays.asList(
                    review(5, "非常干净，位置好"),
                    review(5, "干净舒适"),
                    review(4, "不错")
            );
            when(reviewRepository.findLatestReviewsByHomestayId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(reviews));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("HIGH_RATING");
        }

        @Test
        @DisplayName("用户评价维度 - 关键词")
        void reviewDimensionKeyword() {
            List<Review> reviews = Arrays.asList(
                    review(4, "非常干净"),
                    review(4, "干净卫生"),
                    review(4, "位置好")
            );
            when(reviewRepository.findLatestReviewsByHomestayId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(reviews));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).anyMatch(id -> id.startsWith("REVIEW_KEYWORD_"));
        }

        @Test
        @DisplayName("入住便利性维度 - 自助入住")
        void checkInConvenienceDimension() {
            baseHomestay.setAmenities(Set.of(amenity("SELF_CHECKIN", "自助入住", "Key")));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("CONVENIENT_CHECKIN");
        }
    }

    @Nested
    @DisplayName("多维度平衡与排序")
    class BalanceTests {

        @Test
        @DisplayName("每个维度最多输出 1 个特色")
        void oneFeaturePerDimension() {
            baseHomestay.setAmenities(Set.of(
                    amenity("HIGH_SPEED_WIFI", "高速WiFi", "Connection"),
                    amenity("DESK", "办公桌", "Monitor"),
                    amenity("PARKING", "停车位", "Van")
            ));
            when(orderRepository.countByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                    .thenReturn(5L);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            // 设施维度应只有商务办公（组合）或单一设施，不会同时出现
            long amenityFeatureCount = features.stream()
                    .filter(f -> f.getFeatureId().startsWith("AMENITY_") || f.getFeatureId().startsWith("BUSINESS_WORKSPACE"))
                    .count();
            assertThat(amenityFeatureCount).isEqualTo(1);
        }

        @Test
        @DisplayName("按优先级降序排序")
        void sortedByPriorityDescending() {
            when(orderRepository.countByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any()))
                    .thenReturn(5L);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(features).isSortedAccordingTo((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        }
    }

    @Nested
    @DisplayName("搜索条件提权")
    class SearchCriteriaBoostTests {

        @Test
        @DisplayName("命中 featureId 时优先级 +50（权重系统在最后再次加权）")
        void boostMatchingFeature() {
            // 公寓 PROPERTY_TYPE 基础优先级 11，先 +50 提权，再经权重系统 1.2 倍加权 -> 73
            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(
                    baseHomestay, Collections.singletonList("PROPERTY_TYPE_APARTMENT"));

            SuggestedFeatureDTO propertyFeature = features.stream()
                    .filter(f -> "PROPERTY_TYPE_APARTMENT".equals(f.getFeatureId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(propertyFeature.getPriority()).isEqualTo(73);
        }

        @Test
        @DisplayName("未命中时不提升")
        void noBoostForUnmatchedFeature() {
            // 公寓 PROPERTY_TYPE 基础优先级 11，经权重系统加权后变为 13
            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(
                    baseHomestay, Collections.singletonList("PROPERTY_TYPE_VILLA"));

            SuggestedFeatureDTO propertyFeature = features.stream()
                    .filter(f -> "PROPERTY_TYPE_APARTMENT".equals(f.getFeatureId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(propertyFeature.getPriority()).isEqualTo(13);
        }
    }

    @Nested
    @DisplayName("权重系统")
    class WeightSystemTests {

        @Test
        @DisplayName("别墅类型提升房源类型特色优先级")
        void villaBoostsPropertyType() {
            Homestay villa = Homestay.builder()
                    .id(2L)
                    .title("别墅")
                    .type("VILLA")
                    .price(new BigDecimal("800"))
                    .maxGuests(8)
                    .cityCode("SH")
                    .cityText("上海")
                    .districtCode("PD")
                    .districtText("浦东新区")
                    .addressDetail("测试地址")
                    .provinceCode("SH")
                    .provinceText("上海")
                    .minNights(1)
                    .status(com.homestay3.homestaybackend.model.HomestayStatus.ACTIVE)
                    .build();

            List<SuggestedFeatureDTO> villaFeatures = featureAnalysisService.analyzeFeatures(villa, null);
            SuggestedFeatureDTO villaPropertyFeature = villaFeatures.stream()
                    .filter(f -> f.getFeatureId().startsWith("PROPERTY_TYPE_"))
                    .findFirst()
                    .orElseThrow();

            List<SuggestedFeatureDTO> apartmentFeatures = featureAnalysisService.analyzeFeatures(baseHomestay, null);
            SuggestedFeatureDTO apartmentPropertyFeature = apartmentFeatures.stream()
                    .filter(f -> f.getFeatureId().startsWith("PROPERTY_TYPE_"))
                    .findFirst()
                    .orElseThrow();

            assertThat(villaPropertyFeature.getPriority()).isGreaterThan(apartmentPropertyFeature.getPriority());
        }

        @Test
        @DisplayName("低价房源提升价格特色优先级")
        void lowPriceBoostsPriceFeature() {
            PriceCompetitivenessDTO priceAnalysis = PriceCompetitivenessDTO.builder()
                    .competitivenessLevel(PriceCompetitivenessDTO.PriceCompetitivenessLevel.COMPETITIVE)
                    .priceDifferenceFromAverage(-10.0)
                    .comparisonScope("同城市同类型")
                    .build();
            when(priceAnalysisService.analyzePriceCompetitiveness(any(Homestay.class))).thenReturn(priceAnalysis);

            baseHomestay.setPrice(new BigDecimal("150"));
            List<SuggestedFeatureDTO> lowPriceFeatures = featureAnalysisService.analyzeFeatures(baseHomestay, null);
            SuggestedFeatureDTO lowPriceFeature = lowPriceFeatures.stream()
                    .filter(f -> f.getFeatureId().startsWith("PRICE_"))
                    .findFirst()
                    .orElseThrow();

            baseHomestay.setPrice(new BigDecimal("500"));
            List<SuggestedFeatureDTO> highPriceFeatures = featureAnalysisService.analyzeFeatures(baseHomestay, null);
            SuggestedFeatureDTO highPriceFeature = highPriceFeatures.stream()
                    .filter(f -> f.getFeatureId().startsWith("PRICE_"))
                    .findFirst()
                    .orElseThrow();

            assertThat(lowPriceFeature.getPriority()).isGreaterThan(highPriceFeature.getPriority());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("null Homestay 返回空列表")
        void nullHomestayReturnsEmpty() {
            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(null, null);

            assertThat(features).isEmpty();
        }

        @Test
        @DisplayName("空设施不生成设施特色")
        void emptyAmenitiesNoAmenityFeature() {
            baseHomestay.setAmenities(Collections.emptySet());

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).noneMatch(id -> id.startsWith("AMENITY_") || id.startsWith("BUSINESS_WORKSPACE"));
        }

        @Test
        @DisplayName("设施为 null 不抛出异常")
        void nullAmenitiesNoException() {
            baseHomestay.setAmenities(null);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(features).isNotEmpty();
            assertThat(featureIds(features)).noneMatch(id -> id.startsWith("AMENITY_") || id.startsWith("BUSINESS_WORKSPACE"));
        }

        @Test
        @DisplayName("空评价不生成评价特色")
        void emptyReviewsNoReviewFeature() {
            when(reviewRepository.findLatestReviewsByHomestayId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).noneMatch(id -> id.startsWith("HIGH_RATING") || id.startsWith("POSITIVE_RATING") || id.startsWith("REVIEW_KEYWORD_"));
        }

        @Test
        @DisplayName("评价数量不足时不生成评价特色")
        void insufficientReviewsNoReviewFeature() {
            List<Review> reviews = Arrays.asList(review(5, "很好"));
            when(reviewRepository.findLatestReviewsByHomestayId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(reviews));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).noneMatch(id -> id.startsWith("HIGH_RATING") || id.startsWith("POSITIVE_RATING") || id.startsWith("REVIEW_KEYWORD_"));
        }

        @Test
        @DisplayName("房源 ID 为 null 时不调用订单和评价仓库")
        void nullIdSkipsRepositoryCalls() {
            baseHomestay.setId(null);

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(features).isNotEmpty();
            verify(orderRepository, never()).countByHomestayIdAndStatusInAndCreatedAtBetween(anyLong(), anyList(), any(), any());
            verify(reviewRepository, never()).findLatestReviewsByHomestayId(anyLong(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("组合规则匹配")
    class CombinationRuleTests {

        @Test
        @DisplayName("单个必需设施即可命中组合规则（阈值 max(1, size/2)）")
        void singleRequiredAmenityMatchesCombinationRule() {
            // 厨房烹饪组合需要 厨房 + 冰箱 + 微波炉，阈值为 1
            baseHomestay.setAmenities(Set.of(amenity("KITCHEN", "厨房", "KnifeFork")));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).contains("COOKING_FACILITIES");
        }

        @Test
        @DisplayName("无匹配设施时不命中组合规则")
        void noMatchWhenAmenitiesMissing() {
            baseHomestay.setAmenities(Set.of(amenity("PARKING", "停车位", "Van")));

            List<SuggestedFeatureDTO> features = featureAnalysisService.analyzeFeatures(baseHomestay, null);

            assertThat(featureIds(features)).noneMatch(id -> id.startsWith("BUSINESS_WORKSPACE") || id.startsWith("COOKING_FACILITIES"));
        }

        @Test
        @DisplayName("组合规则中同一设施只计 1 次（反射验证）")
        void noDuplicateCountingInCombinationRule() throws Exception {
            // 构造一个 4 项组合规则，阈值为 2；若旧逻辑重复计数，1 个设施会被计为 2 次而误判命中
            Class<?> ruleClass = Class.forName(
                    "com.homestay3.homestaybackend.service.impl.HomestayFeatureAnalysisServiceImpl$CombinationRule");
            Constructor<?> constructor = ruleClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object rule = constructor.newInstance(
                    Arrays.asList("A", "B", "C", "D"),
                    Arrays.asList("A", "B", "C", "D"),
                    "TEST_COMBO",
                    "Star",
                    "测试组合",
                    "描述",
                    10
            );

            java.lang.reflect.Method checkMethod = ruleClass.getDeclaredMethod("checkAmenities", Map.class, Set.class);
            checkMethod.setAccessible(true);

            Map<String, String> amenityMap = new java.util.HashMap<>();
            amenityMap.put("a", "A");
            Set<Amenity> amenities = Set.of(amenity("A", "A", "Star"));

            boolean matched = (boolean) checkMethod.invoke(rule, amenityMap, amenities);

            // 只有 1 个真实设施，不应满足阈值 2
            assertThat(matched).isFalse();
        }
    }

    @Nested
    @DisplayName("DTO 重载移除")
    class DtoOverloadTests {

        @Test
        @DisplayName("接口不再声明 analyzeFeatures(HomestayDTO, List)")
        void dtoOverloadRemovedFromInterface() {
            boolean hasDtoOverload = Arrays.stream(HomestayFeatureAnalysisService.class.getMethods())
                    .anyMatch(m -> m.getName().equals("analyzeFeatures") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].getSimpleName().equals("HomestayDTO"));

            assertThat(hasDtoOverload).isFalse();
        }
    }
}
