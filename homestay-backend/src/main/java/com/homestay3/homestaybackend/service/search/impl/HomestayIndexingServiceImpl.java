package com.homestay3.homestaybackend.service.search.impl;

import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.model.search.HomestayDocument;
import com.homestay3.homestaybackend.repository.*;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class HomestayIndexingServiceImpl implements HomestayIndexingService {

    private final HomestayRepository homestayRepository;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final HomestayDocumentRepository homestayDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // 缓存 ES 可用性状态，避免每次搜索都 ping ES
    private volatile Boolean esAvailableCache;
    private volatile long lastAvailabilityCheck = 0;
    private static final long AVAILABILITY_CACHE_MS = 30_000;

    @Override
    @Transactional(readOnly = true)
    public int rebuildIndex() {
        log.info("Starting full index rebuild for homestays...");

        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of("homestay_index"));
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("Deleted existing homestay_index");
        }
        indexOps.create();
        log.info("Created homestay_index");

        List<Homestay> allHomestays = homestayRepository.findAll();

        // 批量预加载统计数据，避免 N+1
        List<Long> allIds = allHomestays.stream().map(Homestay::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Double> ratingMap = toMapDouble(reviewRepository.getAverageRatingByHomestayIds(allIds));
        Map<Long, Long> reviewCountMap = toMapLong(reviewRepository.getReviewCountByHomestayIds(allIds));
        Map<Long, Long> bookingCountMap = toMapLong(orderRepository.countByHomestayIds(allIds));
        Map<Long, Long> favoriteCountMap = toMapLong(userFavoriteRepository.countByHomestayIds(allIds));

        List<HomestayDocument> documents = allHomestays.stream()
                .map(h -> convertToDocument(h, ratingMap, reviewCountMap, bookingCountMap, favoriteCountMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!documents.isEmpty()) {
            homestayDocumentRepository.saveAll(documents);
            log.info("Indexed {} homestays into ES", documents.size());
        }
        return documents.size();
    }

    @Override
    @Async("taskExecutor")
    @Transactional(readOnly = true)
    public void syncHomestay(Long homestayId) {
        try {
            Homestay homestay = homestayRepository.findById(homestayId).orElse(null);
            if (homestay == null) {
                deleteHomestay(homestayId);
                return;
            }
            HomestayDocument document = convertToDocument(homestay);
            if (document != null) {
                homestayDocumentRepository.save(document);
                log.debug("Synced homestay {} to ES", homestayId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync homestay {} to ES: {}", homestayId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void syncHomestays(List<Long> homestayIds) {
        if (homestayIds == null || homestayIds.isEmpty()) {
            return;
        }
        List<Homestay> homestays = homestayRepository.findAllById(homestayIds);
        List<HomestayDocument> documents = homestays.stream()
                .map(this::convertToDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!documents.isEmpty()) {
            homestayDocumentRepository.saveAll(documents);
            log.info("Batch synced {} homestays to ES", documents.size());
        }
    }

    @Override
    public void deleteHomestay(Long homestayId) {
        try {
            homestayDocumentRepository.deleteById(homestayId);
            log.debug("Deleted homestay {} from ES", homestayId);
        } catch (Exception e) {
            log.warn("Failed to delete homestay {} from ES: {}", homestayId, e.getMessage());
        }
    }

    @Override
    public boolean isElasticsearchAvailable() {
        long now = System.currentTimeMillis();
        if (esAvailableCache != null && (now - lastAvailabilityCheck) < AVAILABILITY_CACHE_MS) {
            return esAvailableCache;
        }
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of("homestay_index"));
            boolean available = indexOps.exists() || indexOps.create();
            esAvailableCache = available;
            lastAvailabilityCheck = now;
            return available;
        } catch (Exception e) {
            log.debug("Elasticsearch not available: {}", e.getMessage());
            esAvailableCache = false;
            lastAvailabilityCheck = now;
            return false;
        }
    }

    @Override
    public HomestayDocument findById(Long homestayId) {
        Optional<HomestayDocument> doc = homestayDocumentRepository.findById(homestayId);
        return doc.orElse(null);
    }

    // 增量同步用：实时查询统计
    private HomestayDocument convertToDocument(Homestay homestay) {
        if (homestay == null) {
            return null;
        }
        Long homestayId = homestay.getId();
        Double rating = reviewRepository.getAverageRatingByHomestayId(homestayId);
        Long reviewCount = reviewRepository.getReviewCountByHomestayId(homestayId);
        Long bookingCount = orderRepository.countByHomestayId(homestayId);
        long favoriteCount = userFavoriteRepository.countByHomestayId(homestayId);
        return buildDocument(homestay, rating, reviewCount, bookingCount, (int) favoriteCount);
    }

    // 全量重建用：批量统计
    private HomestayDocument convertToDocument(
            Homestay homestay,
            Map<Long, Double> ratingMap,
            Map<Long, Long> reviewCountMap,
            Map<Long, Long> bookingCountMap,
            Map<Long, Long> favoriteCountMap) {
        if (homestay == null) {
            return null;
        }
        Long id = homestay.getId();
        Double rating = ratingMap != null ? ratingMap.get(id) : null;
        Long reviewCount = reviewCountMap != null ? reviewCountMap.get(id) : null;
        Long bookingCount = bookingCountMap != null ? bookingCountMap.get(id) : null;
        Long favoriteCount = favoriteCountMap != null ? favoriteCountMap.get(id) : null;
        return buildDocument(homestay, rating, reviewCount, bookingCount,
                favoriteCount != null ? favoriteCount.intValue() : 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Double> toMapDouble(List<Object[]> rows) {
        if (rows == null) return Map.of();
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(),
                r -> ((Number) r[1]).doubleValue(),
                (a, b) -> a));
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Long> toMapLong(List<Object[]> rows) {
        if (rows == null) return Map.of();
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(),
                r -> ((Number) r[1]).longValue(),
                (a, b) -> a));
    }

    private HomestayDocument buildDocument(
            Homestay homestay, Double rating, Long reviewCount, Long bookingCount, Integer favoriteCount) {
        List<String> amenityValues = homestay.getAmenities() != null
                ? homestay.getAmenities().stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getValue())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                : List.of();

        GeoPoint location = null;
        if (homestay.getLatitude() != null && homestay.getLongitude() != null) {
            location = new GeoPoint(homestay.getLatitude().doubleValue(), homestay.getLongitude().doubleValue());
        }

        return HomestayDocument.builder()
                .id(homestay.getId())
                .title(homestay.getTitle())
                .description(homestay.getDescription())
                .addressDetail(homestay.getAddressDetail())
                .provinceCode(homestay.getProvinceCode())
                .cityCode(homestay.getCityCode())
                .districtCode(homestay.getDistrictCode())
                .type(homestay.getType())
                .amenities(amenityValues)
                .price(homestay.getPrice())
                .maxGuests(homestay.getMaxGuests())
                .rating(rating != null ? rating : 0.0)
                .reviewCount(reviewCount != null ? reviewCount.intValue() : 0)
                .bookingCount(bookingCount != null ? bookingCount.intValue() : 0)
                .favoriteCount(favoriteCount != null ? favoriteCount : 0)
                .location(location)
                .status(homestay.getStatus() != null ? homestay.getStatus().name() : null)
                .createdAt(homestay.getCreatedAt())
                .updatedAt(homestay.getUpdatedAt())
                .build();
    }
}
