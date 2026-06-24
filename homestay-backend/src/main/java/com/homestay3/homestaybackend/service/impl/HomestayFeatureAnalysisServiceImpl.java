package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.PriceCompetitivenessDTO;
import com.homestay3.homestaybackend.dto.SuggestedFeatureDTO;
import com.homestay3.homestaybackend.entity.Amenity;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.service.HomestayFeatureAnalysisService;
import com.homestay3.homestaybackend.service.PriceAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import org.springframework.data.domain.Page;
import java.util.Optional;

@Service
public class HomestayFeatureAnalysisServiceImpl implements HomestayFeatureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(HomestayFeatureAnalysisServiceImpl.class);
    private static final int MAX_SUGGESTED_FEATURES = 8; // 支持多维度平衡显示，最多8个特色
    private static final int RECENT_DAYS_FOR_BOOKING_ANALYSIS = 120; // 扩展到120天，增加触发概率
    private static final long MIN_BOOKINGS_FOR_POPULAR_TAG = 1; // 降低阈值到1，测试环境更容易触发
    private static final String COMPARABLE_HOMESTAY_STATUS = "ACTIVE"; // Status of homestays to compare against for price
    private static final double PRICE_COMPETITIVENESS_THRESHOLD = 0.15; // e.g., 15% cheaper than average

    // Constants for Review Analysis
    private static final int MIN_REVIEWS_FOR_ANALYSIS = 3;
    private static final double POSITIVE_REVIEW_RATING_THRESHOLD = 4.0; // Reviews with average rating >= 4.0 are considered positive
    private static final int MIN_KEYWORD_FREQUENCY = 2; // Keyword mentioned at least this many times
    private static final int MAX_REVIEW_KEYWORD_FEATURES = 2; // Max number of keyword-based features from reviews
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
            "干净", "舒适", "方便", "位置好", "安静", "风景", "景观",
            "热情", "周到", "服务好", "性价比高", "推荐", "满意", "温馨", "设施全"
    );

    // Constant for boosting priority based on search criteria
    private static final int PRIORITY_BOOST_FOR_SEARCH_CRITERIA = 50;
    
    // 权重系统配置 - 调整为更合理的值范围
    private static final double BASE_WEIGHT_PROPERTY_TYPE = 1.2;
    private static final double BASE_WEIGHT_PRICE = 1.1;
    private static final double BASE_WEIGHT_COMBINATION = 1.0;
    private static final double BASE_WEIGHT_AMENITY = 0.9;
    private static final double BASE_WEIGHT_LOCATION = 1.0;
    private static final double BASE_WEIGHT_BOOKING_ACTIVITY = 0.8;
    private static final double BASE_WEIGHT_REVIEWS = 1.0;

    private final OrderRepository orderRepository;
    private final HomestayRepository homestayRepository;
    private final ReviewRepository reviewRepository;
    private final PriceAnalysisService priceAnalysisService;

    @Autowired
    public HomestayFeatureAnalysisServiceImpl(OrderRepository orderRepository, HomestayRepository homestayRepository, ReviewRepository reviewRepository, PriceAnalysisService priceAnalysisService) {
        this.orderRepository = orderRepository;
        this.homestayRepository = homestayRepository;
        this.reviewRepository = reviewRepository;
        this.priceAnalysisService = priceAnalysisService;
    }

    @Override
    public List<SuggestedFeatureDTO> analyzeFeatures(Homestay homestay, List<String> referringSearchCriteria) {
        if (homestay == null) {
            return new ArrayList<>();
        }
        log.debug("开始多维度平衡特色分析，房源ID: {}, 搜索条件: {}", homestay.getId(), referringSearchCriteria);

        // 使用维度管理器确保每个维度最多一个特色
        Map<String, SuggestedFeatureDTO> dimensionFeatures = new HashMap<>();

        // 1. 房源类型与空间维度
        analyzePropertyTypeAndSpace(homestay, dimensionFeatures);

        // 2. 价格竞争力维度
        analyzeDimensionPrice(homestay, dimensionFeatures);

        // 3. 设施特色维度（组合设施优先，单一设施为备选）
        analyzeDimensionAmenities(homestay, dimensionFeatures);

        // 4. 位置优势维度
        analyzeDimensionLocation(homestay, dimensionFeatures);

        // 5. 预订活跃度维度
        analyzeDimensionBookingActivity(homestay, dimensionFeatures);

        // 6. 周末流行度维度
        analyzeDimensionWeekendPopularity(homestay, dimensionFeatures);

        // 7. 用户评价维度
        analyzeDimensionReviews(homestay, dimensionFeatures);

        // 8. 入住便利性维度
        analyzeDimensionCheckInConvenience(homestay, dimensionFeatures);

        // 收集所有维度的特色
        List<SuggestedFeatureDTO> features = new ArrayList<>(dimensionFeatures.values());
        log.info("多维度分析完成，房源ID: {}，生成特色数: {}，维度覆盖: {}", 
                homestay.getId(), features.size(), dimensionFeatures.keySet());

        // 应用搜索条件权重提升
        boostFeaturesMatchingSearchCriteria(features, referringSearchCriteria);

        // 应用权重系统重新计算优先级
        applyWeightSystem(homestay, features);

        // 按优先级排序返回（不再限制数量）
        List<SuggestedFeatureDTO> sortedFeatures = features.stream()
                       .sorted(Comparator.comparingInt(SuggestedFeatureDTO::getPriority).reversed())
                       .collect(Collectors.toList());

        log.debug("最终特色列表，房源ID: {}，特色数: {}，优先级排序: {}", 
                homestay.getId(), sortedFeatures.size(), 
                sortedFeatures.stream().map(f -> f.getTitle() + "(" + f.getPriority() + ")").collect(Collectors.toList()));

        return sortedFeatures;
    }

    private void analyzePropertyTypeAndSpace(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析房源类型维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzePropertyTypeAndSpace(homestay, tempFeatures);
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("PROPERTY_TYPE", tempFeatures.get(0));
            log.debug("房源类型维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzeDimensionPrice(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析价格竞争力维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzePriceCompetitiveness(homestay, tempFeatures);
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("PRICE", tempFeatures.get(0));
            log.debug("价格竞争力维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzeDimensionAmenities(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析设施特色维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        
        // 优先分析组合设施
        Set<String> usedAmenities = analyzeCombinationFeatures(homestay, tempFeatures);
        
        // 如果没有组合设施特色，则分析单一设施
        if (tempFeatures.isEmpty()) {
            analyzeKeyAmenities(homestay, tempFeatures, new HashSet<>());
        }
        
        // 选择优先级最高的设施特色
        if (!tempFeatures.isEmpty()) {
            SuggestedFeatureDTO bestAmenityFeature = tempFeatures.stream()
                    .max(Comparator.comparingInt(SuggestedFeatureDTO::getPriority))
                    .orElse(tempFeatures.get(0));
            dimensionFeatures.put("AMENITIES", bestAmenityFeature);
            log.debug("设施特色维度特色: {} (从{}个候选中选择)", bestAmenityFeature.getTitle(), tempFeatures.size());
        }
    }

    private void analyzeDimensionLocation(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析位置优势维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzeLocationAdvantages(homestay, tempFeatures);
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("LOCATION", tempFeatures.get(0));
            log.debug("位置优势维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzeDimensionBookingActivity(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析预订活跃度维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        
        // 优先分析近期预订活跃度
        analyzeBookingActivity(homestay, tempFeatures);
        
        // 如果没有近期活跃度，分析基础活跃度作为后备
        if (tempFeatures.isEmpty()) {
            analyzeBasicActivity(homestay, tempFeatures);
        }
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("BOOKING_ACTIVITY", tempFeatures.get(0));
            log.debug("预订活跃度维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzeDimensionWeekendPopularity(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析周末流行度维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzeWeekendPopularity(homestay, tempFeatures);
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("WEEKEND", tempFeatures.get(0));
            log.debug("周末流行度维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzeDimensionReviews(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析用户评价维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzeReviewsAndGenerateFeatures(homestay, tempFeatures);
        
        // 选择优先级最高的评价特色
        if (!tempFeatures.isEmpty()) {
            SuggestedFeatureDTO bestReviewFeature = tempFeatures.stream()
                    .max(Comparator.comparingInt(SuggestedFeatureDTO::getPriority))
                    .orElse(tempFeatures.get(0));
            dimensionFeatures.put("REVIEWS", bestReviewFeature);
            log.debug("用户评价维度特色: {} (从{}个候选中选择)", bestReviewFeature.getTitle(), tempFeatures.size());
        }
    }

    private void analyzeDimensionCheckInConvenience(Homestay homestay, Map<String, SuggestedFeatureDTO> dimensionFeatures) {
        log.debug("分析入住便利性维度，房源ID: {}", homestay.getId());
        List<SuggestedFeatureDTO> tempFeatures = new ArrayList<>();
        analyzeCheckInConvenience(homestay, tempFeatures);
        
        if (!tempFeatures.isEmpty()) {
            dimensionFeatures.put("CHECKIN", tempFeatures.get(0));
            log.debug("入住便利性维度特色: {}", tempFeatures.get(0).getTitle());
        }
    }

    private void analyzePropertyTypeAndSpace(Homestay homestay, List<SuggestedFeatureDTO> features) {
        String type = homestay.getType(); // Assuming type is a String code like "TRADITIONAL", "APARTMENT"
        Integer maxGuests = homestay.getMaxGuests();
        String title = "优质房源";
        String description = "";
        String iconName = "House"; // Default icon
        String specificTypeFeatureId = "PROPERTY_TYPE_GENERAL";

        if (type != null) {
            specificTypeFeatureId = "PROPERTY_TYPE_" + type.toUpperCase();
            switch (type.toUpperCase()) {
                case "TRADITIONAL":
                    title = "传统民居";
                    description = String.format("体验纯正的当地%s风情。", type.toLowerCase());
                    if (maxGuests != null && maxGuests >= 4) {
                        title = "宽敞传统民居";
                        description += String.format(" 空间充裕，可容纳多达%d位房客。", maxGuests);
                    }
                    break;
                case "APARTMENT":
                    title = "现代公寓";
                    iconName = "OfficeBuilding";
                    description = "设施齐全的现代公寓，出行便利。";
                    if (maxGuests != null && maxGuests >= 3) {
                        title = "家庭式公寓";
                        description = String.format("适合家庭入住，可容纳%d位房客，设施齐全。", maxGuests);
                    }
                    break;
                case "VILLA":
                    title = "豪华别墅";
                    iconName = "HomeFilled";
                    description = "尊享私人别墅的奢华与舒适。";
                    if (maxGuests != null && maxGuests >= 6) {
                        title = "派对别墅";
                        description += String.format(" 超大空间，可容纳%d人以上，适合团队活动。", maxGuests);
                    }
                    break;
                case "UNIQUE_STAY":
                    title = "特色住宿";
                    iconName = "MagicStick";
                    description = "独一无二的住宿体验，留下难忘回忆。";
                    break;
                // Add more cases as per your defined types
                default:
                    title = "舒适住宿";
                    description = String.format("一个舒适的住宿选择，可容纳%d位房客。", maxGuests != null ? maxGuests : 1);
            }
        } else if (maxGuests != null) { // Type is null but maxGuests exists
             if (maxGuests >= 5) {
                title = "宽敞空间";
                description = String.format("空间宽敞，非常适合家庭或团队出游，可容纳%d位房客。", maxGuests);
             } else if (maxGuests >= 3) {
                title = "舒适多人入住";
                description = String.format("适合多人出行，可轻松容纳%d位房客。", maxGuests);
             }
        }
        
        features.add(SuggestedFeatureDTO.builder()
                .featureId(specificTypeFeatureId)
                .iconName(iconName)
                .title(title)
                .description(description)
                                    .priority(11) // 房源类型优先级最高
                .build());
    }

    /**
     * 分析设施组合特色 - 将多个相关设施组合成更有吸引力的特色
     */
    private Set<String> analyzeCombinationFeatures(Homestay homestay, List<SuggestedFeatureDTO> features) {
        Set<String> usedAmenities = new HashSet<>();
        
        if (homestay.getAmenities() == null || homestay.getAmenities().isEmpty()) {
            log.debug("房源ID: {} 没有设施信息，跳过组合特色分析", homestay.getId());
            return usedAmenities;
        }

        // 创建设施的映射便于查找
        Map<String, String> amenityMap = homestay.getAmenities().stream()
                .collect(Collectors.toMap(
                        amenity -> amenity.getLabel() != null ? amenity.getLabel() : "",
                        amenity -> amenity.getValue() != null ? amenity.getValue() : "",
                        (existing, replacement) -> existing // 处理重复键
                ));

        // 预定义的组合规则（按优先级排序）
        List<CombinationRule> combinationRules = Arrays.asList(
                // 商务办公组合 - 优先级最高
            new CombinationRule(
                        Arrays.asList("高速WiFi", "办公桌"),
                        Arrays.asList("高速WiFi", "办公桌"),
                        "BUSINESS_WORKSPACE",
                        "Monitor",
                        "商务办公",
                        "配备高速网络和专用办公区域，远程办公的理想选择。",
                        10
                ),
                // 家庭亲子组合
            new CombinationRule(
                        Arrays.asList("婴儿床", "儿童餐椅"),
                        Arrays.asList("婴儿床", "儿童餐椅"),
                        "FAMILY_FRIENDLY",
                "Trophy",
                        "亲子友好",
                        "贴心准备婴幼儿设施，家庭出游无忧。",
                9
            ),
                // 休闲娱乐组合
            new CombinationRule(
                        Arrays.asList("游戏机", "投影仪"),
                        Arrays.asList("游戏机", "投影仪"),
                        "ENTERTAINMENT_SETUP",
                        "VideoPlay",
                        "娱乐设施",
                        "配备游戏和影音设备，享受休闲时光。",
                        8
                ),
                // 厨房烹饪组合
            new CombinationRule(
                        Arrays.asList("厨房", "冰箱", "微波炉"),
                        Arrays.asList("厨房", "冰箱", "微波炉"),
                        "COOKING_FACILITIES",
                        "KnifeFork",
                        "厨房设施",
                        "厨房设备齐全，可自制美食，享受居家体验。",
                        7
                ),
                // 舒适休息组合
            new CombinationRule(
                        Arrays.asList("空调", "暖气"),
                        Arrays.asList("空调", "暖气"),
                        "CLIMATE_COMFORT",
                        "Sunny",
                        "四季舒适",
                        "配备冷暖空调，四季居住都舒适。",
                        6
                )
        );

        // 按优先级检查组合规则
        for (CombinationRule rule : combinationRules) {
            if (rule.checkAmenities(amenityMap, homestay.getAmenities())) {
                // 记录已使用的设施
                Set<String> matchedAmenities = rule.getMatchedAmenities(amenityMap, homestay.getAmenities());
                usedAmenities.addAll(matchedAmenities);

                // 添加组合特色
                features.add(SuggestedFeatureDTO.builder()
                        .featureId(rule.featureId)
                        .iconName(rule.iconName)
                        .title(rule.title)
                        .description(rule.description)
                        .priority(rule.priority)
                        .build());
                
                log.debug("房源ID: {} 匹配组合特色: {} (使用设施: {})", 
                        homestay.getId(), rule.title, matchedAmenities);
                
                // 只选择第一个匹配的高优先级组合，避免过多重复
                break;
            }
        }

        return usedAmenities;
    }

    /**
     * 组合规则定义类
     */
    private static class CombinationRule {
        final List<String> requiredAmenityLabels;
        final List<String> requiredAmenityValues;
        final String featureId;
        final String iconName;
        final String title;
        final String description;
        final int priority;

        CombinationRule(List<String> labels, List<String> values, String featureId, 
                       String iconName, String title, String description, int priority) {
            this.requiredAmenityLabels = labels;
            this.requiredAmenityValues = values;
            this.featureId = featureId;
            this.iconName = iconName;
            this.title = title;
            this.description = description;
            this.priority = priority;
        }

        boolean checkAmenities(Map<String, String> amenityMap, Set<Amenity> amenities) {
            // 检查是否有足够的匹配设施（至少匹配一半）
            int requiredMatches = Math.max(1, requiredAmenityLabels.size() / 2);
            int actualMatches = 0;

            // 每个要求设施只计一次：优先按 value 匹配，未命中时按 label 回退
            for (int i = 0; i < requiredAmenityLabels.size(); i++) {
                String label = requiredAmenityLabels.get(i);
                String value = i < requiredAmenityValues.size() ? requiredAmenityValues.get(i) : null;
                boolean matched = false;

                if (value != null && !value.isEmpty()) {
                    for (Amenity amenity : amenities) {
                        if (value.equalsIgnoreCase(amenity.getValue()) ||
                            value.equalsIgnoreCase(amenity.getLabel())) {
                            matched = true;
                            break;
                        }
                    }
                }

                if (!matched && label != null && !label.isEmpty()) {
                    matched = amenityMap.containsKey(label.toLowerCase());
                }

                if (matched) {
                    actualMatches++;
                }
            }

            return actualMatches >= requiredMatches;
        }

        Set<String> getMatchedAmenities(Map<String, String> amenityMap, Set<Amenity> amenities) {
            Set<String> matched = new HashSet<>();

            // 每个要求设施只收集一次：优先按 value 匹配，未命中时按 label 回退
            for (int i = 0; i < requiredAmenityLabels.size(); i++) {
                String label = requiredAmenityLabels.get(i);
                String value = i < requiredAmenityValues.size() ? requiredAmenityValues.get(i) : null;
                boolean collected = false;

                if (value != null && !value.isEmpty()) {
                    for (Amenity amenity : amenities) {
                        if (value.equalsIgnoreCase(amenity.getValue()) ||
                            value.equalsIgnoreCase(amenity.getLabel())) {
                            matched.add(amenity.getValue());
                            matched.add(amenity.getLabel());
                            collected = true;
                            break;
                        }
                    }
                }

                if (!collected && label != null && !label.isEmpty() && amenityMap.containsKey(label.toLowerCase())) {
                    matched.add(label);
                    matched.add(amenityMap.get(label.toLowerCase()));
                }
            }

            return matched;
        }
    }

    private void analyzeKeyAmenities(Homestay homestay, List<SuggestedFeatureDTO> features, Set<String> usedAmenities) {
        // 移除特征数量限制，专注于分析最重要的单一设施特色
        if (homestay.getAmenities() == null || homestay.getAmenities().isEmpty()) {
            log.debug("房源ID: {} 没有设施信息，跳过单一设施分析", homestay.getId());
            return;
        }

        Set<Amenity> amenities = homestay.getAmenities();
        log.debug("开始单一设施分析，房源ID: {}, 可用设施数量: {}, 已使用设施: {}", 
                homestay.getId(), amenities.size(), usedAmenities);

        // 按优先级排序的重要设施列表
        List<String> priorityAmenities = Arrays.asList(
                "高速WiFi", "停车位", "空调", "暖气", "洗衣机", "厨房", 
                "游泳池", "健身房", "花园", "阳台", "宠物友好", "无障碍设施"
        );

        for (String priorityAmenity : priorityAmenities) {
            // 检查是否已被组合特色使用
            if (usedAmenities.contains(priorityAmenity)) {
                continue;
            }

            // 查找匹配的设施
            Optional<Amenity> matchedAmenity = amenities.stream()
                    .filter(amenity -> priorityAmenity.equals(amenity.getLabel()))
                    .findFirst();

            if (matchedAmenity.isPresent()) {
                Amenity amenity = matchedAmenity.get();

            features.add(SuggestedFeatureDTO.builder()
                    .featureId("AMENITY_" + amenity.getValue().toUpperCase().replace(" ", "_"))
                        .iconName(amenity.getIcon() != null ? amenity.getIcon() : "Star")
                    .title(amenity.getLabel())
                        .description(amenity.getDescription() != null ? amenity.getDescription() : "重要设施特色")
                        .priority(5) // 单一设施优先级较低
                    .build());

                log.debug("添加单一设施特色: {} (ID: {})", amenity.getLabel(), amenity.getValue());
                
                // 只添加一个最重要的单一设施特色
                break;
            }
        }
    }

    private void analyzeLocationAdvantages(Homestay homestay, List<SuggestedFeatureDTO> features) {
        // 移除特征数量限制，专注于位置优势分析
        if (homestay.getAddressDetail() == null && homestay.getCityCode() == null) {
            log.debug("房源ID: {} 缺少位置信息，跳过位置优势分析", homestay.getId());
            return;
        }

        log.debug("开始位置优势分析，房源ID: {}, 城市代码: {}, 地址: {}", 
                homestay.getId(), homestay.getCityCode(), homestay.getAddressDetail());

        // 基于城市代码的位置优势
        if (homestay.getCityCode() != null) {
            String locationFeature = generateLocationFeature(homestay.getCityCode());
            if (locationFeature != null) {
                features.add(SuggestedFeatureDTO.builder()
                        .featureId("LOCATION_" + homestay.getCityCode().toUpperCase())
                        .iconName("Location")
                        .title("地理位置")
                        .description(locationFeature)
                        .priority(6)
                        .build());
                
                log.debug("添加位置优势特色: {}", locationFeature);
            }
        }
    }

    private void analyzeCheckInConvenience(Homestay homestay, List<SuggestedFeatureDTO> features) {
        // 移除特征数量限制，专注于入住便利性分析
        log.debug("开始入住便利性分析，房源ID: {}", homestay.getId());

        // 这里可以根据实际的便利性因素进行分析
        // 当前简化实现，可以根据需要扩展
        if (homestay.getAmenities() != null) {
            boolean hasKeylessEntry = homestay.getAmenities().stream()
                    .anyMatch(amenity -> "自助入住".equals(amenity.getLabel()) || "密码锁".equals(amenity.getLabel()));
            
            boolean has24HourCheckin = homestay.getAmenities().stream()
                    .anyMatch(amenity -> "24小时入住".equals(amenity.getLabel()));

            if (hasKeylessEntry || has24HourCheckin) {
                features.add(SuggestedFeatureDTO.builder()
                        .featureId("CONVENIENT_CHECKIN")
                        .iconName("Key")
                        .title("便捷入住")
                        .description(hasKeylessEntry ? "支持自助入住，灵活便捷" : "24小时入住服务")
                        .priority(5)
                        .build());
                
                log.debug("添加入住便利性特色: 便捷入住");
            }
        }
    }

    private void analyzeReviewsAndGenerateFeatures(Homestay homestay, List<SuggestedFeatureDTO> features) {
        // 移除特征数量限制，专注于评价分析
        if (homestay.getId() == null) {
            log.debug("房源ID为空，跳过评价分析");
            return;
        }

        log.debug("开始评价分析，房源ID: {}", homestay.getId());

        try {
            // 获取房源的评价数据
            Page<Review> reviewPage = reviewRepository.findLatestReviewsByHomestayId(homestay.getId(), Pageable.unpaged());
            List<Review> reviews = reviewPage.getContent();
            
            if (reviews.size() < MIN_REVIEWS_FOR_ANALYSIS) {
                log.debug("房源ID: {} 评价数量不足 ({} < {})，跳过评价特色分析", 
                        homestay.getId(), reviews.size(), MIN_REVIEWS_FOR_ANALYSIS);
                return;
            }

            // 分析平均评分
            double averageRating = reviews.stream()
                    .mapToDouble(Review::getRating)
                    .average()
                    .orElse(0.0);

            log.debug("房源ID: {} 评价统计: 总数={}, 平均评分={}",
                    homestay.getId(), reviews.size(), String.format("%.1f", averageRating));

            // 高评分特色
            if (averageRating >= 4.5) {
                features.add(SuggestedFeatureDTO.builder()
                        .featureId("HIGH_RATING")
                        .iconName("Star")
                        .title("好评如潮")
                        .description(String.format("平均评分%.1f分，深受住客喜爱", averageRating))
                        .priority(8)
                        .build());
                
                log.debug("添加高评分特色: 平均评分{}分", String.format("%.1f", averageRating));
            } else if (averageRating >= POSITIVE_REVIEW_RATING_THRESHOLD) {
                features.add(SuggestedFeatureDTO.builder()
                        .featureId("POSITIVE_RATING")
                        .iconName("Like")
                        .title("评价良好")
                        .description(String.format("平均评分%.1f分，住客满意度高", averageRating))
                        .priority(6)
                        .build());
                
                log.debug("添加正面评价特色: 平均评分{}分", String.format("%.1f", averageRating));
            }

            // 分析评价关键词（只选择最重要的一个）
            analyzeReviewKeywords(reviews, features, homestay.getId());

        } catch (Exception e) {
            log.error("评价分析失败，房源ID: {}: {}", homestay.getId(), e.getMessage(), e);
        }
    }

    // ============== 缺失的分析方法 ==============

    private void analyzePriceCompetitiveness(Homestay homestay, List<SuggestedFeatureDTO> features) {
        if (homestay.getId() == null) {
            log.debug("跳过价格分析 - 房源ID为空");
            return;
        }

        log.debug("开始价格竞争力分析，房源ID: {}, 当前价格: {}, 城市代码: {}, 区域代码: {}", 
            homestay.getId(), homestay.getPrice(), homestay.getCityCode(), homestay.getDistrictCode());

        try {
            PriceCompetitivenessDTO priceAnalysis = priceAnalysisService.analyzePriceCompetitiveness(homestay);
            
            if (priceAnalysis == null) {
                log.debug("价格竞争力分析结果为空，房源ID: {}", homestay.getId());
            return;
        }

            log.info("房源ID: {}. 价格竞争力分析完成: 等级={}, 与平均价格差异={}%, 比较范围={}, 可比房源数量={}", 
                homestay.getId(),
                priceAnalysis.getCompetitivenessLevel(), 
                String.format("%.1f", priceAnalysis.getPriceDifferenceFromAverage()),
                priceAnalysis.getComparisonScope(),
                priceAnalysis.getComparableHomestaysCount());

            // 根据竞争力等级生成不同的特色标签
            SuggestedFeatureDTO priceFeature = createPriceFeatureFromAnalysis(priceAnalysis);
            if (priceFeature != null) {
                features.add(priceFeature);
                log.debug("添加价格特色: {} - {}", priceFeature.getTitle(), priceFeature.getDescription());
            }

        } catch (Exception e) {
            log.error("价格竞争力分析失败，房源ID: {}: {}", homestay.getId(), e.getMessage(), e);
        }
    }

    private SuggestedFeatureDTO createPriceFeatureFromAnalysis(PriceCompetitivenessDTO analysis) {
        PriceCompetitivenessDTO.PriceCompetitivenessLevel level = analysis.getCompetitivenessLevel();
        
        switch (level) {
            case EXTREMELY_COMPETITIVE:
                return SuggestedFeatureDTO.builder()
                        .featureId("PRICE_EXTREMELY_COMPETITIVE")
                        .iconName("PriceTag")
                        .title("超值选择")
                        .description(String.format("价格低于%s平均水平%.0f%%，性价比极高！", 
                                analysis.getComparisonScope(), 
                                Math.abs(analysis.getPriceDifferenceFromAverage())))
                        .priority(12) // 极高优先级
                        .build();
                        
            case HIGHLY_COMPETITIVE:
                return SuggestedFeatureDTO.builder()
                        .featureId("PRICE_HIGHLY_COMPETITIVE")
                        .iconName("PriceTag")
                        .title("高性价比")
                        .description(String.format("价格低于%s平均水平%.0f%%，值得推荐！", 
                                analysis.getComparisonScope(), 
                                Math.abs(analysis.getPriceDifferenceFromAverage())))
                        .priority(10) // 高优先级
                        .build();
                        
            case COMPETITIVE:
                return SuggestedFeatureDTO.builder()
                        .featureId("PRICE_COMPETITIVE")
                        .iconName("PriceTag")
                        .title("价格优势")
                        .description(String.format("价格低于%s平均水平%.0f%%，物有所值。", 
                                analysis.getComparisonScope(), 
                                Math.abs(analysis.getPriceDifferenceFromAverage())))
                        .priority(9) // 提高优先级，从8提到9
                        .build();
                        
            case MARKET_RATE:
                // 为市场价格也添加特色显示
                return SuggestedFeatureDTO.builder()
                        .featureId("PRICE_MARKET_RATE")
                        .iconName("TrendCharts")
                        .title("市场价格")
                        .description(String.format("价格符合%s市场水平，定价合理。", 
                                analysis.getComparisonScope()))
                        .priority(6) // 中等优先级
                        .build();
                        
            case PREMIUM:
                // 只有在旺季或特殊情况下才展示溢价定位
                if (analysis.isSeasonalPeak()) {
                    return SuggestedFeatureDTO.builder()
                            .featureId("PRICE_PREMIUM_SEASONAL")
                            .iconName("Star")
                            .title("旺季精选")
                            .description("旺季期间的精品房源，品质卓越。")
                            .priority(6)
                            .build();
                }
                break;
                
            case ABOVE_MARKET:
            default:
                // 高于市场价格时不生成价格相关特色
                break;
        }
        
        return null;
    }

    private void analyzeBookingActivity(Homestay homestay, List<SuggestedFeatureDTO> features) {
        if (homestay.getId() == null) {
            log.debug("跳过预订活跃度分析 - 房源ID为空");
            return;
        }

        log.debug("开始预订活跃度分析，房源ID: {}", homestay.getId());

        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(RECENT_DAYS_FOR_BOOKING_ANALYSIS);

            List<String> relevantOrderStatus = Arrays.asList(
                OrderStatus.CONFIRMED.name(),
                OrderStatus.PAID.name(),
                OrderStatus.COMPLETED.name(),
                OrderStatus.CHECKED_IN.name()
            );
            
            log.debug("Querying orders for Homestay ID: {} between {} and {} with statuses: {}", 
                homestay.getId(), startDate, endDate, relevantOrderStatus);

            long recentBookingsCount = orderRepository.countByHomestayIdAndStatusInAndCreatedAtBetween(
                homestay.getId(), relevantOrderStatus, startDate, endDate);

            log.info("Homestay ID: {} has {} recent bookings (last {} days). Min threshold: {}", 
                homestay.getId(), recentBookingsCount, RECENT_DAYS_FOR_BOOKING_ANALYSIS, MIN_BOOKINGS_FOR_POPULAR_TAG);
            
            if (recentBookingsCount >= MIN_BOOKINGS_FOR_POPULAR_TAG) {
                SuggestedFeatureDTO bookingFeature = SuggestedFeatureDTO.builder()
                        .featureId("POPULAR_BOOKING")
                        .iconName("Hot")
                        .title("近期热门")
                        .description(String.format("过去%d天内有%d笔预订，深受住客喜爱！", RECENT_DAYS_FOR_BOOKING_ANALYSIS, recentBookingsCount))
                        .priority(10)
                        .build();
                
                features.add(bookingFeature);
                log.debug("Added feature: Popular Booking for Homestay ID: {}", homestay.getId());
            } else {
                log.debug("Homestay ID: {} does not meet minimum booking threshold ({} < {})", 
                        homestay.getId(), recentBookingsCount, MIN_BOOKINGS_FOR_POPULAR_TAG);
            }
        } catch (Exception e) {
            log.error("Error analyzing booking activity for Homestay ID: {}: {}", homestay.getId(), e.getMessage(), e);
        }
    }

    private void analyzeBasicActivity(Homestay homestay, List<SuggestedFeatureDTO> features) {
        if (homestay.getId() == null) {
            log.debug("跳过基础活跃度分析 - 房源ID为空");
            return;
        }

        log.debug("开始基础活跃度分析，房源ID: {}", homestay.getId());

        try {
            // 检查总预订数
            Long totalBookings = orderRepository.countByHomestayId(homestay.getId());
            log.debug("房源ID: {} 总预订数: {}", homestay.getId(), totalBookings);

            SuggestedFeatureDTO activityFeature = null;

            if (totalBookings != null && totalBookings >= 3) { // 降低阈值从5到3
                activityFeature = SuggestedFeatureDTO.builder()
                        .featureId("ESTABLISHED_HOMESTAY")
                        .iconName("Trophy")
                        .title("经验房源")
                        .description(String.format("已累计接待%d笔订单，经验丰富", totalBookings))
                        .priority(7)
                        .build();
                log.debug("Created established homestay feature for ID: {} with priority 7", homestay.getId());
            } else if (totalBookings != null && totalBookings >= 1) {
                activityFeature = SuggestedFeatureDTO.builder()
                        .featureId("NEW_HOMESTAY")
                        .iconName("Present")
                        .title("新推房源")
                        .description("全新房源，等待您的体验")
                        .priority(6)
                        .build();
                log.debug("Created new homestay feature for ID: {} with priority 6", homestay.getId());
            }

            if (activityFeature != null) {
                features.add(activityFeature);
                log.debug("Added activity feature: {} for ID: {}", activityFeature.getTitle(), homestay.getId());
            }
        } catch (Exception e) {
            log.error("Error analyzing basic activity for Homestay ID: {}: {}", homestay.getId(), e.getMessage(), e);
        }
    }

    private void analyzeWeekendPopularity(Homestay homestay, List<SuggestedFeatureDTO> features) {
        if (homestay.getId() == null) {
            log.debug("跳过周末流行度分析 - 房源ID为空");
            return;
        }

        log.debug("开始周末流行度分析，房源ID: {}", homestay.getId());

        final double WEEKEND_BOOKING_RATIO_THRESHOLD = 0.3; // 降低到30%
        final int MIN_TOTAL_BOOKINGS_FOR_WEEKEND_TAG = 3; // 降低最小订单数
        final int MONTHS_TO_ANALYZE_FOR_WEEKEND = 12; // Analyze bookings in the last 12 months

        try {
            LocalDateTime analysisEndDate = LocalDateTime.now();
            LocalDateTime analysisStartDate = analysisEndDate.minusMonths(MONTHS_TO_ANALYZE_FOR_WEEKEND);

            List<String> relevantOrderStatus = Arrays.asList(
                OrderStatus.CONFIRMED.name(),
                OrderStatus.PAID.name(),
                OrderStatus.COMPLETED.name(),
                OrderStatus.CHECKED_IN.name()
            );

            // 优化查询：在 SQL 层按状态和时间范围过滤，避免加载全部历史订单
            List<Order> orders = orderRepository.findByHomestayIdAndStatusInAndCreatedAtBetween(
                    homestay.getId(), relevantOrderStatus, analysisStartDate, analysisEndDate).stream()
                .filter(order -> order.getCheckInDate() != null)
                .collect(Collectors.toList());

            if (orders.size() < MIN_TOTAL_BOOKINGS_FOR_WEEKEND_TAG) {
                log.debug("Homestay ID: {} has only {} relevant bookings in the last {} months. Skipping weekend popularity analysis.",
                          homestay.getId(), orders.size(), MONTHS_TO_ANALYZE_FOR_WEEKEND);
                return;
            }

            long weekendBookingCount = orders.stream()
                .filter(order -> {
                    java.time.DayOfWeek dayOfWeek = order.getCheckInDate().getDayOfWeek();
                    return dayOfWeek == java.time.DayOfWeek.FRIDAY || dayOfWeek == java.time.DayOfWeek.SATURDAY;
                })
                .count();

            double weekendBookingRatio = (double) weekendBookingCount / orders.size();

            log.info("Homestay ID: {}: Total relevant bookings: {}, Weekend bookings: {}, Ratio: {}",
                homestay.getId(), orders.size(), weekendBookingCount, String.format("%.2f", weekendBookingRatio));

            if (weekendBookingRatio >= WEEKEND_BOOKING_RATIO_THRESHOLD) {
                SuggestedFeatureDTO weekendFeature = SuggestedFeatureDTO.builder()
                        .featureId("WEEKEND_POPULAR")
                        .iconName("Umbrella")
                        .title("周末优选")
                        .description(String.format("许多订单选择在周末入住 (周五/周六入住比例达 %.0f%%)。", weekendBookingRatio * 100))
                        .priority(7)
                        .build();
                
                features.add(weekendFeature);
                log.debug("Added feature: Weekend Popular for Homestay ID: {}", homestay.getId());
            }

        } catch (Exception e) {
            log.error("Error analyzing weekend popularity for Homestay ID: {}: {}", homestay.getId(), e.getMessage(), e);
        }
    }

    private String generateLocationFeature(String cityCode) {
        // 简化的位置特色生成
        if (cityCode == null) return null;

        switch (cityCode.toUpperCase()) {
            case "BJ":
            case "BEIJING":
                return "首都核心区域，交通便利";
            case "SH":
            case "SHANGHAI":
                return "魔都繁华地段，购物便利";
            case "GZ":
            case "GUANGZHOU":
                return "羊城商业中心，美食云集";
            case "SZ":
            case "SHENZHEN":
                return "科技之都，现代便利";
            case "CD":
            case "CHENGDU":
                return "天府之国，休闲美食之都";
            case "HZ":
            case "HANGZHOU":
                return "人间天堂，西湖风光近在咫尺";
            case "XM":
            case "XIAMEN":
                return "海滨花园城市，文艺小资聚集地";
            case "LJ":
            case "LIJIANG":
                return "古城韵味，纳西风情体验地";
            case "XA":
            case "XIAN":
                return "十三朝古都，历史文化厚重";
            case "QD":
            case "QINGDAO":
                return "红瓦绿树碧海蓝天，海滨度假胜地";
            case "NJ":
            case "NANJING":
                return "六朝古都，江南文脉所在";
            case "SU":
            case "SUZHOU":
                return "园林之城，江南水乡风情";
            case "DL":
            case "DALIAN":
                return "北方浪漫海滨，避暑休闲之选";
            case "SY":
            case "SANYA":
                return "热带海滨天堂，阳光沙滩度假首选";
            default:
                return "优越地理位置，出行方便";
        }
    }

    private void analyzeReviewKeywords(List<Review> reviews, List<SuggestedFeatureDTO> features, Long homestayId) {
            Map<String, Long> keywordCounts = new HashMap<>();
            for (String keyword : POSITIVE_KEYWORDS) {
                keywordCounts.put(keyword, 0L);
            }

            for (Review review : reviews) {
                if (review.getContent() != null && !review.getContent().isEmpty()) { 
                String reviewText = review.getContent().toLowerCase();
                    for (String keyword : POSITIVE_KEYWORDS) {
                    if (reviewText.contains(keyword)) {
                             keywordCounts.put(keyword, keywordCounts.get(keyword) + 1);
                        }
                    }
                }
            }
            
        // 选择最高频的关键词
        Optional<Map.Entry<String, Long>> topKeyword = keywordCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= MIN_KEYWORD_FREQUENCY)
            .max(Map.Entry.comparingByValue());

        if (topKeyword.isPresent()) {
            String keyword = topKeyword.get().getKey();
            Long count = topKeyword.get().getValue();

                features.add(SuggestedFeatureDTO.builder()
                    .featureId("REVIEW_KEYWORD_" + keyword.toUpperCase().replace(" ", "_"))
                    .iconName("ChatDotRound")
                    .title("住客称赞" + keyword)
                    .description(String.format("%d位住客在评价中赞赏了%s", count, keyword))
                    .priority(7)
                        .build());
            
            log.debug("Added review keyword feature: {} ({} times) for Homestay ID: {}", keyword, count, homestayId);
            }
    }

    // New method for boosting features based on search criteria
    private void boostFeaturesMatchingSearchCriteria(List<SuggestedFeatureDTO> features, List<String> referringSearchCriteria) {
        if (CollectionUtils.isEmpty(referringSearchCriteria) || CollectionUtils.isEmpty(features)) {
            return;
        }

        log.debug("Attempting to boost features based on referring search criteria: {}", referringSearchCriteria);

        for (SuggestedFeatureDTO feature : features) {
            for (String criterion : referringSearchCriteria) {
                // Attempt to match criterion with featureId.
                // Criteria could be like "AMENITY_POOL", "TYPE_VILLA", "KEYWORD_CLEAN", "SELF_CHECK_IN" etc.
                // We assume criteria are already formatted to potentially match feature IDs or parts of them.
                if (feature.getFeatureId() != null && criterion != null && 
                    feature.getFeatureId().equalsIgnoreCase(criterion)) {
                    
                    feature.setPriority(feature.getPriority() + PRIORITY_BOOST_FOR_SEARCH_CRITERIA);
                    log.info("Boosted feature \"{}\" (ID: {}) by {} due to matching search criterion \"{}\"",
                             feature.getTitle(), feature.getFeatureId(), PRIORITY_BOOST_FOR_SEARCH_CRITERIA, criterion);
                    // Optional: If a feature is boosted, we might not need to check other criteria for it.
                    // break; 
                }
                // Example of a more flexible match: if criterion is just "POOL" and featureId is "AMENITY_POOL"
                // else if (feature.getFeatureId() != null && criterion != null && 
                //          feature.getFeatureId().toUpperCase().contains(criterion.toUpperCase())) {
                //     // Add more specific checks to avoid accidental matches if using 'contains'
                //     if (feature.getFeatureId().startsWith("AMENITY_") && criterion.length() > 2) { // Basic guard
                //        feature.setPriority(feature.getPriority() + PRIORITY_BOOST_FOR_SEARCH_CRITERIA);
                //        log.info("Boosted feature (contains match) \"{}\" (ID: {}) by {} due to criterion \"{}\"",
                //                  feature.getTitle(), feature.getFeatureId(), PRIORITY_BOOST_FOR_SEARCH_CRITERIA, criterion);
                //     }
                // }
            }
        }
    }

    /**
     * 权重系统 - 根据房源特性动态调整特征权重
     */
    private void applyWeightSystem(Homestay homestay, List<SuggestedFeatureDTO> features) {
        log.debug("开始应用权重系统，房源ID: {}, 特征数量: {}", homestay.getId(), features.size());
        
        for (SuggestedFeatureDTO feature : features) {
            int originalPriority = feature.getPriority(); // 保存原始优先级
            double finalWeight = calculateFeatureWeight(homestay, feature);
            
            // 修正权重计算：确保权重至少为1.0，避免优先级过度降低
            if (finalWeight < 1.0) {
                finalWeight = 1.0 + (finalWeight - 1.0) * 0.3; // 缩小负向调整幅度
            }
            
            // 将权重转换为新的优先级（权重越高，优先级越高）
            int newPriority = (int) Math.round(originalPriority * finalWeight);
            feature.setPriority(newPriority);
            
            log.debug("特征: {}, 原优先级: {}, 权重: {}, 新优先级: {}",
                feature.getTitle(), originalPriority, String.format("%.2f", finalWeight), newPriority);
        }
    }

    /**
     * 计算单个特征的权重
     */
    private double calculateFeatureWeight(Homestay homestay, SuggestedFeatureDTO feature) {
        double baseWeight = getBaseWeight(feature);
        double contextualBonus = getContextualBonus(homestay, feature);
        double propertyBonus = getPropertyTypeBonus(homestay, feature);
        double seasonalBonus = getSeasonalBonus(homestay, feature);
        
        return baseWeight + contextualBonus + propertyBonus + seasonalBonus;
    }

    /**
     * 获取特征类型的基础权重
     */
    private double getBaseWeight(SuggestedFeatureDTO feature) {
        String featureId = feature.getFeatureId();
        
        if (featureId.startsWith("PROPERTY_TYPE_")) {
            return BASE_WEIGHT_PROPERTY_TYPE;
        } else if (featureId.startsWith("PRICE_")) {
            return BASE_WEIGHT_PRICE;
        } else if (featureId.startsWith("FAMILY_FRIENDLY_") || featureId.startsWith("CONVENIENT_") || 
                   featureId.startsWith("PET_PARADISE") || featureId.startsWith("REMOTE_WORK_")) {
            return BASE_WEIGHT_COMBINATION;
        } else if (featureId.startsWith("AMENITY_")) {
            return BASE_WEIGHT_AMENITY;
        } else if (featureId.startsWith("LOCATION_")) {
            return BASE_WEIGHT_LOCATION;
        } else if (featureId.startsWith("POPULAR_") || featureId.startsWith("WEEKEND_") || 
                   featureId.startsWith("ESTABLISHED_") || featureId.startsWith("NEW_")) {
            return BASE_WEIGHT_BOOKING_ACTIVITY;
        } else if (featureId.startsWith("HIGHLY_RATED") || featureId.startsWith("REVIEW_")) {
            return BASE_WEIGHT_REVIEWS;
        }
        
        return 0.5; // 默认权重
    }

    /**
     * 获取上下文相关的权重加成
     */
    private double getContextualBonus(Homestay homestay, SuggestedFeatureDTO feature) {
        double bonus = 0.0;
        String featureId = feature.getFeatureId();
        
        // 价格竞争力在经济型房源中权重更高
        if (featureId.startsWith("PRICE_") && homestay.getPrice() != null) {
            if (homestay.getPrice().compareTo(new BigDecimal("200")) <= 0) {
                bonus += 0.3; // 低价房源中价格优势更重要
            }
        }
        
        // 组合特色在高端房源中权重更高
        if (featureId.startsWith("FAMILY_FRIENDLY_") || featureId.startsWith("REMOTE_WORK_")) {
            if (homestay.getPrice() != null && homestay.getPrice().compareTo(new BigDecimal("300")) > 0) {
                bonus += 0.2; // 高端房源中功能组合更重要
            }
        }
        
        // 位置特色在城市中心权重更高
        if (featureId.startsWith("LOCATION_") && homestay.getDistrictText() != null) {
            String district = homestay.getDistrictText().toLowerCase();
            if (district.contains("市中心") || district.contains("中心区") || district.contains("市区")) {
                bonus += 0.3;
            }
        }
        
        // 停车相关在郊区权重更高
        if (featureId.contains("PARKING") || featureId.contains("TRANSPORT")) {
            if (homestay.getDistrictText() != null && 
                (homestay.getDistrictText().contains("郊区") || homestay.getDistrictText().contains("新区"))) {
                bonus += 0.2;
            }
        }
        
        return bonus;
    }

    /**
     * 获取房源类型相关的权重加成
     */
    private double getPropertyTypeBonus(Homestay homestay, SuggestedFeatureDTO feature) {
        double bonus = 0.0;
        String type = homestay.getType() != null ? homestay.getType().toUpperCase() : "";
        String featureId = feature.getFeatureId();
        
        // 别墅类型权重加成
        if ("VILLA".equals(type)) {
            if (featureId.startsWith("PROPERTY_TYPE_")) {
                bonus += 0.4; // 别墅类型特征权重高
            }
            if (featureId.startsWith("FAMILY_FRIENDLY_") || featureId.contains("PET_")) {
                bonus += 0.3; // 别墅适合家庭和宠物
            }
        }
        
        // 公寓类型权重加成
        if ("APARTMENT".equals(type)) {
            if (featureId.startsWith("LOCATION_") || featureId.startsWith("CONVENIENT_")) {
                bonus += 0.3; // 公寓位置和便利性重要
            }
            if (featureId.startsWith("REMOTE_WORK_")) {
                bonus += 0.2; // 公寓适合远程办公
            }
        }
        
        // 传统民居权重加成
        if ("TRADITIONAL".equals(type)) {
            if (featureId.startsWith("PROPERTY_TYPE_")) {
                bonus += 0.3; // 传统特色重要
            }
            if (featureId.startsWith("REVIEW_") && feature.getTitle().contains("传统")) {
                bonus += 0.4; // 传统相关评价权重高
            }
        }
        
        // 特色住宿权重加成
        if ("UNIQUE_STAY".equals(type)) {
            if (featureId.startsWith("PROPERTY_TYPE_")) {
                bonus += 0.5; // 特色住宿类型特征权重最高
            }
            if (featureId.startsWith("REVIEW_")) {
                bonus += 0.2; // 特色住宿的评价很重要
            }
        }
        
        return bonus;
    }

    /**
     * 获取季节性权重加成
     */
    private double getSeasonalBonus(Homestay homestay, SuggestedFeatureDTO feature) {
        double bonus = 0.0;
        String featureId = feature.getFeatureId();
        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        
        // 夏季(6-8月)权重加成
        if (month >= 6 && month <= 8) {
            if (featureId.contains("POOL") || featureId.contains("GARDEN") || featureId.contains("BALCONY")) {
                bonus += 0.2; // 夏季户外设施权重高
            }
            if (featureId.startsWith("WEEKEND_")) {
                bonus += 0.1; // 夏季周末特色权重高
            }
        }
        
        // 冬季(12-2月)权重加成
        if (month == 12 || month <= 2) {
            if (featureId.contains("HEATING") || featureId.contains("WARM")) {
                bonus += 0.3; // 冬季取暖设施权重高
            }
            if (featureId.startsWith("FAMILY_FRIENDLY_")) {
                bonus += 0.1; // 冬季家庭聚会需求高
            }
        }
        
        // 假期季节(1-2月, 7-8月, 10月)权重加成
        if (month <= 2 || (month >= 7 && month <= 8) || month == 10) {
            if (featureId.startsWith("POPULAR_") || featureId.startsWith("WEEKEND_")) {
                bonus += 0.2; // 假期季节活跃度特征权重高
            }
            if (featureId.startsWith("PRICE_")) {
                bonus += 0.1; // 假期季节价格优势更重要
            }
        }
        
        return bonus;
    }

} 