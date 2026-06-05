package com.homestay3.homestaybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.*;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.OrderPriceSnapshot;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderPriceSnapshotRepository;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.exception.PriceChangedException;
import com.homestay3.homestaybackend.service.PricingEngineService;
import com.homestay3.homestaybackend.service.PricingService;
import com.homestay3.homestaybackend.service.PromotionMatchService;
import com.homestay3.homestaybackend.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingServiceImpl implements PricingService {

    private final HomestayRepository homestayRepository;
    private final OrderPriceSnapshotRepository snapshotRepository;
    private final PromotionMatchService promotionMatchService;
    private final CouponService couponService;
    private final ObjectProvider<SystemConfigService> systemConfigServiceProvider;
    private final ObjectMapper objectMapper;
    private final PricingEngineService pricingEngineService;
    private final org.redisson.api.RedissonClient redissonClient;

    @Override
    @Transactional(readOnly = true)
    public PricingQuoteResponse quote(PricingQuoteRequest request, Long userId) {
        PricingResult result = calculate(
                request.getHomestayId(), request.getCheckInDate(), request.getCheckOutDate(),
                request.getGuestCount(), request.getCouponIds(), userId
        );

        String quoteToken = generateQuoteToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        // 缓存报价到 Redis（10 分钟过期）
        try {
            String couponIdsJson = objectMapper.writeValueAsString(
                    request.getCouponIds() != null ? request.getCouponIds() : java.util.Collections.emptyList());
            String requestHash = buildRequestHash(request);

            PricingQuoteCache cache = PricingQuoteCache.builder()
                    .quoteToken(quoteToken)
                    .requestHash(requestHash)
                    .homestayId(request.getHomestayId())
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .guestCount(request.getGuestCount())
                    .couponIdsJson(couponIdsJson)
                    .payableAmount(result.getPayableAmount())
                    .expiresAt(expiresAt)
                    .build();

            org.redisson.api.RBucket<PricingQuoteCache> bucket =
                    redissonClient.getBucket("pricing:quote:" + quoteToken);
            bucket.set(cache, 10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("报价缓存写入 Redis 失败: {}", e.getMessage());
        }

        // 查询用户可用优惠券（用于前端展示切换），按当前房源和金额过滤
        List<AvailableCouponDTO> availableCoupons;
        if (userId != null) {
            availableCoupons = couponService.getAvailableCouponDTOs(userId).stream()
                    .filter(dto -> {
                        // 门槛金额过滤
                        if (dto.getThresholdAmount() != null
                                && result.getRoomOriginalAmount().compareTo(dto.getThresholdAmount()) < 0) {
                            return false;
                        }
                        // 房源范围过滤：调用计算接口，若优惠为 0 则不适配
                        try {
                            CouponDiscountResult dr = couponService.calculateCouponDiscount(
                                    dto.getId(), request.getHomestayId(), result.getRoomOriginalAmount());
                            return dr.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            availableCoupons = Collections.emptyList();
        }

        return PricingQuoteResponse.builder()
                .quoteToken(quoteToken)
                .expiresAt(expiresAt)
                .homestayId(result.getHomestayId())
                .nights(result.getNights())
                .roomOriginalAmount(result.getRoomOriginalAmount())
                .activityDiscountAmount(result.getActivityDiscountAmount())
                .fullReductionAmount(result.getFullReductionAmount())
                .couponDiscountAmount(result.getCouponDiscountAmount())
                .couponPlatformDiscountAmount(result.getCouponPlatformDiscountAmount())
                .couponHostDiscountAmount(result.getCouponHostDiscountAmount())
                .platformDiscountAmount(result.getPlatformDiscountAmount())
                .hostDiscountAmount(result.getHostDiscountAmount())
                .cleaningFee(result.getCleaningFee())
                .serviceFee(result.getServiceFee())
                .payableAmount(result.getPayableAmount())
                .hostReceivableAmount(result.getHostReceivableAmount())
                .dailyPrices(result.getDailyPrices())
                .appliedPromotions(result.getAppliedPromotions())
                .availableCoupons(availableCoupons)
                .effectiveCouponIds(result.getEffectiveCouponIds())
                .priceDetails(result.getPriceDetails())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingResult calculate(Long homestayId, LocalDate checkInDate, LocalDate checkOutDate,
                                    Integer guestCount, List<Long> couponIds, Long userId) {
        // 1. 参数校验
        if (homestayId == null) {
            throw new IllegalArgumentException("房源ID不能为空");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw new IllegalArgumentException("入住日期和退房日期不能为空");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("退房日期必须晚于入住日期");
        }

        // 2. 获取房源
        Homestay homestay = homestayRepository.findById(homestayId)
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在"));

        if (guestCount != null && guestCount > homestay.getMaxGuests()) {
            throw new IllegalArgumentException("入住人数不能超过房源最大容纳人数：" + homestay.getMaxGuests());
        }

        int nights = (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        if (nights < homestay.getMinNights()) {
            throw new IllegalArgumentException("住宿天数不能少于" + homestay.getMinNights() + "晚");
        }

        // 3. 获取定价配置
        BigDecimal cleaningFeeAmount = getPricingConfig("pricing.cleaning_fee_amount", "50");
        BigDecimal serviceFeeRate = getPricingConfig("pricing.service_fee", "0.15");
        BigDecimal weekendRate = getPricingConfig("pricing.weekend_rate", "1.2");
        BigDecimal holidayRate = getPricingConfig("pricing.holiday_rate", "1.5");

        // 4. 使用定价引擎计算每日价格（步骤 1~2：日期级调价）
        List<PriceCalculationResponse.DailyPrice> dailyPrices =
                pricingEngineService.calculateDailyPrices(homestay, checkInDate, checkOutDate);

        // 计算日期级调价后的房费总和
        BigDecimal totalBasePrice = dailyPrices.stream()
                .map(PriceCalculationResponse.DailyPrice::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. 入住级折扣（步骤 3：早鸟、连住）
        int advanceDays = (int) ChronoUnit.DAYS.between(LocalDate.now(), checkInDate);
        PricingEngineService.StayDiscountResult stayDiscount =
                pricingEngineService.calculateStayDiscounts(homestay, checkInDate, checkOutDate, nights, advanceDays);

        BigDecimal stayDiscountMultiplier = stayDiscount.multiplier();
        BigDecimal afterStayDiscount = totalBasePrice.multiply(stayDiscountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // 6. 房源折扣（系统配置级别的折扣）
        BigDecimal homestayDiscountRate = getHomestayDiscount(homestayId);
        BigDecimal homestayDiscountAmount = BigDecimal.ZERO;
        if (homestayDiscountRate != null && homestayDiscountRate.compareTo(BigDecimal.ONE) < 0) {
            homestayDiscountAmount = afterStayDiscount.multiply(BigDecimal.ONE.subtract(homestayDiscountRate));
        }

        BigDecimal roomOriginalAmount = totalBasePrice;
        BigDecimal afterHomestayDiscount = afterStayDiscount.subtract(homestayDiscountAmount);

        // 7. 匹配活动优惠
        PricingResult.PricingResultBuilder resultBuilder = PricingResult.builder()
                .homestayId(homestayId)
                .nights(nights)
                .dailyPrices(dailyPrices)
                .roomOriginalAmount(roomOriginalAmount)
                .hostDiscountAmount(homestayDiscountAmount);

        List<PromotionMatchService.PromotionMatch> promotionMatches =
                promotionMatchService.matchPromotions(homestay, checkInDate, checkOutDate, userId, roomOriginalAmount);
        promotionMatchService.applyPromotions(resultBuilder, promotionMatches);

        PricingResult tempResult = resultBuilder.build();
        BigDecimal afterActivityDiscount = afterHomestayDiscount
                .subtract(tempResult.getActivityDiscountAmount() != null ? tempResult.getActivityDiscountAmount() : BigDecimal.ZERO)
                .subtract(tempResult.getFullReductionAmount() != null ? tempResult.getFullReductionAmount() : BigDecimal.ZERO);

        // 8. 优惠券优惠（按承担方拆分）
        CouponDiscountResult couponResult = CouponDiscountResult.builder()
                .discountAmount(BigDecimal.ZERO)
                .subsidyBearer("PLATFORM")
                .build();
        if (couponIds != null && !couponIds.isEmpty()) {
            couponResult = couponService.calculateCouponDiscount(couponIds, homestayId, roomOriginalAmount, userId);
        }
        BigDecimal couponDiscount = defaultMoney(couponResult.getDiscountAmount());
        BigDecimal couponPlatformDiscount = defaultMoney(couponResult.getPlatformDiscountAmount());
        BigDecimal couponHostDiscount = defaultMoney(couponResult.getHostDiscountAmount());

        BigDecimal maxCouponDiscount = afterActivityDiscount.max(BigDecimal.ZERO);
        if (couponDiscount.compareTo(maxCouponDiscount) > 0) {
            BigDecimal ratio = maxCouponDiscount.divide(couponDiscount, 8, RoundingMode.HALF_UP);
            couponPlatformDiscount = couponPlatformDiscount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            couponHostDiscount = couponHostDiscount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            couponDiscount = maxCouponDiscount.setScale(2, RoundingMode.HALF_UP);
            BigDecimal splitDiff = couponDiscount.subtract(couponPlatformDiscount.add(couponHostDiscount));
            if (couponHostDiscount.compareTo(BigDecimal.ZERO) > 0) {
                couponHostDiscount = couponHostDiscount.add(splitDiff);
            } else {
                couponPlatformDiscount = couponPlatformDiscount.add(splitDiff);
            }
        }

        // 9. 计算清洁费和服务费（清洁费为固定金额，与入住晚数无关）
        BigDecimal cleaningFee = cleaningFeeAmount;
        BigDecimal discountedRoomAmount = afterActivityDiscount.subtract(couponDiscount).max(BigDecimal.ZERO);
        BigDecimal serviceFee = discountedRoomAmount.multiply(serviceFeeRate).setScale(2, RoundingMode.HALF_UP);

        // 10. 计算最终金额
        BigDecimal payableAmount = discountedRoomAmount.add(cleaningFee).add(serviceFee);

        // 房东应收 = 原始房费 - 房源折扣 - 活动房东承担 - 优惠券房东承担
        BigDecimal totalHostDiscount = homestayDiscountAmount;
        totalHostDiscount = totalHostDiscount.add(tempResult.getHostDiscountAmount() != null ? tempResult.getHostDiscountAmount() : BigDecimal.ZERO);
        totalHostDiscount = totalHostDiscount.add(couponHostDiscount);
        BigDecimal hostReceivable = roomOriginalAmount.subtract(totalHostDiscount).max(BigDecimal.ZERO);

        // 平台补贴 = 活动优惠中平台承担的部分 + 优惠券平台承担部分
        BigDecimal platformSubsidy = tempResult.getPlatformDiscountAmount() != null ? tempResult.getPlatformDiscountAmount() : BigDecimal.ZERO;
        platformSubsidy = platformSubsidy.add(couponPlatformDiscount);

        return resultBuilder
                .couponDiscountAmount(couponDiscount)
                .couponPlatformDiscountAmount(couponPlatformDiscount)
                .couponHostDiscountAmount(couponHostDiscount)
                .platformDiscountAmount(platformSubsidy)
                .hostDiscountAmount(totalHostDiscount)
                .discountedRoomAmount(discountedRoomAmount)
                .cleaningFee(cleaningFee)
                .serviceFee(serviceFee)
                .payableAmount(payableAmount)
                .hostReceivableAmount(hostReceivable)
                .effectiveCouponIds(couponResult.getEffectiveCouponIds())
                .appliedPricingRules(stayDiscount.appliedRules())
                .priceDetails(PriceCalculationResponse.PriceDetails.builder()
                        .cleaningFeeAmount(cleaningFeeAmount)
                        .serviceFeeRate(serviceFeeRate)
                        .weekendRate(weekendRate)
                        .holidayRate(holidayRate)
                        .discountRate(homestayDiscountRate)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public void savePriceSnapshot(Long orderId, PricingResult result, String quoteToken) {
        try {
            String dailyPriceJson = objectMapper.writeValueAsString(result.getDailyPrices());
            String pricingDetailJson = objectMapper.writeValueAsString(result);

            OrderPriceSnapshot snapshot = OrderPriceSnapshot.builder()
                    .orderId(orderId)
                    .quoteToken(quoteToken)
                    .roomOriginalAmount(result.getRoomOriginalAmount())
                    .dailyPriceJson(dailyPriceJson)
                    .activityDiscountAmount(result.getActivityDiscountAmount())
                    .fullReductionAmount(result.getFullReductionAmount())
                    .couponDiscountAmount(result.getCouponDiscountAmount())
                    .platformDiscountAmount(result.getPlatformDiscountAmount())
                    .hostDiscountAmount(result.getHostDiscountAmount())
                    .cleaningFee(result.getCleaningFee())
                    .serviceFee(result.getServiceFee())
                    .payableAmount(result.getPayableAmount())
                    .hostReceivableAmount(result.getHostReceivableAmount())
                    .pricingDetailJson(pricingDetailJson)
                    .build();

            snapshotRepository.save(snapshot);
        } catch (JsonProcessingException e) {
            log.error("保存价格快照序列化失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PricingResult getPriceSnapshot(Long orderId) {
        OrderPriceSnapshot snapshot = snapshotRepository.findByOrderId(orderId).orElse(null);
        if (snapshot == null) {
            return null;
        }
        try {
            List<PriceCalculationResponse.DailyPrice> dailyPrices =
                    objectMapper.readValue(snapshot.getDailyPriceJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, PriceCalculationResponse.DailyPrice.class));

            return PricingResult.builder()
                    .roomOriginalAmount(snapshot.getRoomOriginalAmount())
                    .dailyPrices(dailyPrices)
                    .activityDiscountAmount(snapshot.getActivityDiscountAmount())
                    .fullReductionAmount(snapshot.getFullReductionAmount())
                    .couponDiscountAmount(snapshot.getCouponDiscountAmount())
                    .platformDiscountAmount(snapshot.getPlatformDiscountAmount())
                    .hostDiscountAmount(snapshot.getHostDiscountAmount())
                    .cleaningFee(snapshot.getCleaningFee())
                    .serviceFee(snapshot.getServiceFee())
                    .payableAmount(snapshot.getPayableAmount())
                    .hostReceivableAmount(snapshot.getHostReceivableAmount())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("读取价格快照反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String generateQuoteToken() {
        return "QUOTE_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional(readOnly = true)
    public boolean validateQuoteToken(String quoteToken, com.homestay3.homestaybackend.dto.OrderDTO orderDTO, Long userId) {
        if (quoteToken == null || quoteToken.isBlank()) {
            return false;
        }

        org.redisson.api.RBucket<PricingQuoteCache> bucket =
                redissonClient.getBucket("pricing:quote:" + quoteToken);
        PricingQuoteCache cache = bucket.get();

        if (cache == null) {
            log.info("QuoteToken 不存在或已过期: {}", quoteToken);
            return false;
        }

        // 参数一致性校验
        if (!Objects.equals(cache.getHomestayId(), orderDTO.getHomestayId())
                || !Objects.equals(cache.getCheckInDate(), orderDTO.getCheckInDate())
                || !Objects.equals(cache.getCheckOutDate(), orderDTO.getCheckOutDate())
                || !Objects.equals(cache.getGuestCount(), orderDTO.getGuestCount())) {
            log.info("QuoteToken 参数不一致: token={}", quoteToken);
            return false;
        }

        // 重算价格，比对金额
        PricingResult latestResult = calculate(
                orderDTO.getHomestayId(),
                orderDTO.getCheckInDate(),
                orderDTO.getCheckOutDate(),
                orderDTO.getGuestCount(),
                orderDTO.getCouponIds(),
                userId
        );

        if (latestResult.getPayableAmount().compareTo(cache.getPayableAmount()) != 0) {
            log.info("价格发生变化: token={}, cached={}, latest={}",
                    quoteToken, cache.getPayableAmount(), latestResult.getPayableAmount());
            // 生成最新报价用于返回给前端
            PricingQuoteResponse latestQuote = quote(
                    PricingQuoteRequest.builder()
                            .homestayId(orderDTO.getHomestayId())
                            .checkInDate(orderDTO.getCheckInDate())
                            .checkOutDate(orderDTO.getCheckOutDate())
                            .guestCount(orderDTO.getGuestCount())
                            .couponIds(orderDTO.getCouponIds())
                            .build(),
                    userId
            );
            throw new PriceChangedException("价格已发生变化，请确认最新报价", latestQuote);
        }

        return true;
    }

    private String buildRequestHash(PricingQuoteRequest request) {
        // 简单哈希：拼接关键参数
        String raw = request.getHomestayId() + "|"
                + request.getCheckInDate() + "|"
                + request.getCheckOutDate() + "|"
                + request.getGuestCount() + "|"
                + (request.getCouponIds() != null ? request.getCouponIds().toString() : "");
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes());
    }



    private BigDecimal getPricingConfig(String key, String defaultValue) {
        String value = systemConfigServiceProvider.getObject().getConfigValue(key, defaultValue);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal(defaultValue);
        }
    }

    private BigDecimal getHomestayDiscount(Long homestayId) {
        String discountKey = "homestay.discount." + homestayId;
        String discountValue = systemConfigServiceProvider.getObject().getConfigValue(discountKey);
        if (discountValue != null) {
            try {
                return new BigDecimal(discountValue);
            } catch (NumberFormatException e) {
                log.warn("房源折扣配置 {} 格式错误", discountKey);
            }
        }
        return null;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
