package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.HomestaySearchRequest;
import com.homestay3.homestaybackend.dto.HomestaySearchResultDTO;
import com.homestay3.homestaybackend.dto.MapClusterDTO;
import com.homestay3.homestaybackend.entity.Amenity;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayType;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.search.HomestayDocument;
import com.homestay3.homestaybackend.repository.AmenityRepository;
import com.homestay3.homestaybackend.repository.HomestayDocumentRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.HomestayTypeRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.service.HomestaySearchService;
import com.homestay3.homestaybackend.service.search.HomestayIndexingService;
import com.homestay3.homestaybackend.service.search.SearchPersonalizationService;
import com.homestay3.homestaybackend.service.search.UserBehaviorTrackingService;
import com.homestay3.homestaybackend.util.SortUtils;
import com.homestay3.homestaybackend.util.UserUtil;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.jpa.domain.Specification;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.GeoHashPrecision;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.GeoHashGridAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.GeoHashGridBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomestaySearchServiceImpl implements HomestaySearchService {

    private final HomestayRepository homestayRepository;
    private final HomestayTypeRepository homestayTypeRepository;
    private final AmenityRepository amenityRepository;
    private final HomestayDtoAssembler homestayDtoAssembler;
    private final HomestaySpecificationSupport homestaySpecificationSupport;
    private final ObjectProvider<HomestayIndexingService> homestayIndexingServiceProvider;
    private final ObjectProvider<HomestayDocumentRepository> homestayDocumentRepositoryProvider;
    private final ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider;
    private final UserBehaviorTrackingService userBehaviorTrackingService;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final SearchPersonalizationService searchPersonalizationService;

    @Override
    @Transactional(readOnly = true)
    public List<HomestayDTO> searchHomestays(HomestaySearchRequest request) {
        boolean hasKeyword = StringUtils.hasText(request.getKeyword());
        boolean hasViewport = Stream.of(request.getNorthEastLat(), request.getNorthEastLng(),
                request.getSouthWestLat(), request.getSouthWestLng()).allMatch(Objects::nonNull);

        // 尝试 ES 路径（有 keyword 或 有视口边界时）
        if (hasKeyword || hasViewport) {
            try {
                HomestayIndexingService indexingService = homestayIndexingServiceProvider.getIfAvailable();
                if (indexingService != null && indexingService.isElasticsearchAvailable()) {
                    List<HomestayDTO> esResults = hasKeyword
                            ? searchByElasticsearch(request)
                            : searchViewportByElasticsearch(request);
                    if (esResults != null && !esResults.isEmpty()) {
                        if (hasKeyword) {
                            try {
                                userBehaviorTrackingService.trackSearch(
                                        null, null, request.getKeyword(),
                                        request.getCityCode(), request.getPropertyType());
                            } catch (Exception ignored) {}
                        }
                        return applyPersonalization(applySearchSorting(esResults, request), request);
                    }
                }
            } catch (Exception e) {
                log.warn("ES search failed, falling back to JPA: {}", e.getMessage());
            }
        }

        final String typeCodeToSearch = resolveTypeCode(request.getPropertyType());

        Specification<Homestay> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), HomestayStatus.ACTIVE));

            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = "%" + request.getKeyword() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("title"), keyword),
                        criteriaBuilder.like(root.get("description"), keyword),
                        criteriaBuilder.like(root.get("addressDetail"), keyword)));
            }

            if (StringUtils.hasText(request.getLocation())) {
                String locationLike = "%" + request.getLocation() + "%";
                String locationExact = request.getLocation();
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("provinceText"), locationLike),
                        criteriaBuilder.like(root.get("cityText"), locationLike),
                        criteriaBuilder.like(root.get("districtText"), locationLike),
                        criteriaBuilder.like(root.get("addressDetail"), locationLike),
                        criteriaBuilder.equal(root.get("provinceCode"), locationExact),
                        criteriaBuilder.equal(root.get("cityCode"), locationExact),
                        criteriaBuilder.equal(root.get("districtCode"), locationExact)));
            }

            if (StringUtils.hasText(request.getProvinceCode())) {
                predicates.add(root.get("provinceCode")
                        .in(homestaySpecificationSupport.getProvinceCodeCandidates(request.getProvinceCode())));
            }
            if (StringUtils.hasText(request.getCityCode())) {
                predicates.add(root.get("cityCode")
                        .in(homestaySpecificationSupport.getCityCodeCandidates(request.getCityCode())));
            }
            if (StringUtils.hasText(request.getDistrictCode())) {
                predicates.add(criteriaBuilder.equal(root.get("districtCode"), request.getDistrictCode().trim()));
            }

            if (request.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }

            if (Stream.of(
                    request.getNorthEastLat(),
                    request.getNorthEastLng(),
                    request.getSouthWestLat(),
                    request.getSouthWestLng()).allMatch(Objects::nonNull)) {
                BigDecimal minLatitude = request.getNorthEastLat().min(request.getSouthWestLat());
                BigDecimal maxLatitude = request.getNorthEastLat().max(request.getSouthWestLat());
                BigDecimal minLongitude = request.getNorthEastLng().min(request.getSouthWestLng());
                BigDecimal maxLongitude = request.getNorthEastLng().max(request.getSouthWestLng());

                predicates.add(criteriaBuilder.isNotNull(root.get("latitude")));
                predicates.add(criteriaBuilder.isNotNull(root.get("longitude")));
                predicates.add(criteriaBuilder.between(root.get("latitude"), minLatitude, maxLatitude));
                predicates.add(criteriaBuilder.between(root.get("longitude"), minLongitude, maxLongitude));
            }

            if (request.getMinGuests() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("maxGuests"), request.getMinGuests()));
            }

            if (typeCodeToSearch != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), typeCodeToSearch));
            }

            List<String> requiredAmenityValues = request.getRequiredAmenities();
            if (requiredAmenityValues != null && !requiredAmenityValues.isEmpty()) {
                List<Amenity> requiredAmenities = amenityRepository.findByValueInIgnoreCase(requiredAmenityValues);
                if (!requiredAmenities.isEmpty()) {
                    for (Amenity amenity : requiredAmenities) {
                        predicates.add(criteriaBuilder.isMember(amenity, root.get("amenities")));
                    }
                } else {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }

            LocalDate checkInDate = request.getCheckInDate();
            LocalDate checkOutDate = request.getCheckOutDate();
            if (checkInDate != null && checkOutDate != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Order> orderRoot = subquery.from(Order.class);
                subquery.select(orderRoot.get("id"));

                Predicate homestayMatch = criteriaBuilder.equal(orderRoot.get("homestay"), root);
                Predicate statusMatch = orderRoot.get("status").in(
                        OrderStatus.CONFIRMED.name(),
                        OrderStatus.PAID.name());
                Predicate dateOverlap = criteriaBuilder.and(
                        criteriaBuilder.greaterThan(orderRoot.get("checkOutDate"), checkInDate),
                        criteriaBuilder.lessThan(orderRoot.get("checkInDate"), checkOutDate));

                subquery.where(criteriaBuilder.and(homestayMatch, statusMatch, dateOverlap));
                predicates.add(criteriaBuilder.not(criteriaBuilder.exists(subquery)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        List<Homestay> homestays = homestayRepository.findAll(
                homestaySpecificationSupport.withDetailFetch(specification));

        List<String> criteriaFromSearch = new ArrayList<>();
        if (request.getRequiredAmenities() != null && !request.getRequiredAmenities().isEmpty()) {
            request.getRequiredAmenities().forEach(amenityCode ->
                    criteriaFromSearch.add("AMENITY_" + amenityCode.toUpperCase().replace(" ", "_")));
        }
        if (typeCodeToSearch != null) {
            criteriaFromSearch.add("PROPERTY_TYPE_" + typeCodeToSearch.toUpperCase());
        }

        List<String> finalCriteria = criteriaFromSearch.isEmpty()
                ? null
                : Collections.unmodifiableList(criteriaFromSearch);

        List<HomestayDTO> results = homestayDtoAssembler.toDTOs(homestays, finalCriteria);
        return applyPersonalization(applySearchSorting(results, request), request);
    }

    @Override
    public List<HomestaySearchResultDTO> searchHomestayResults(HomestaySearchRequest request) {
        return searchHomestays(request).stream()
                .map(homestayDtoAssembler::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HomestaySearchResultDTO> searchHomestayPage(HomestaySearchRequest request) {
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 12;

        // ES 路径：有 keyword 或 有视口边界时
        boolean hasKeyword = StringUtils.hasText(request.getKeyword());
        boolean hasViewport = Stream.of(request.getNorthEastLat(), request.getNorthEastLng(),
                request.getSouthWestLat(), request.getSouthWestLng()).allMatch(Objects::nonNull);

        if (hasKeyword || hasViewport) {
            try {
                HomestayIndexingService indexingService = homestayIndexingServiceProvider.getIfAvailable();
                if (indexingService != null && indexingService.isElasticsearchAvailable()) {
                    List<HomestayDTO> esResults = hasKeyword
                            ? searchByElasticsearch(request)
                            : searchViewportByElasticsearch(request);
                    esResults = applyPersonalization(applySearchSorting(esResults, request), request);
                    return toSearchResultPage(esResults, page, size);
                }
            } catch (Exception e) {
                log.warn("ES search failed, falling back to JPA: {}", e.getMessage());
            }
        }

        final String typeCodeToSearch = resolveTypeCode(request.getPropertyType());

        // Distance / Rating 排序需要全量查询后内存计算
        if ((isDistanceSort(request.getSortBy()) && hasDistanceSortOrigin(request)) || isRatingSort(request.getSortBy())) {
            Specification<Homestay> specification = buildSearchSpecification(request, typeCodeToSearch);
            List<Homestay> homestays = homestayRepository.findAll(
                    homestaySpecificationSupport.withDetailFetch(specification));
            List<String> finalCriteria = buildFinalCriteria(request, typeCodeToSearch);
            List<HomestayDTO> dtos = homestayDtoAssembler.toDTOs(homestays, finalCriteria);
            dtos = applyPersonalization(applySearchSorting(dtos, request), request);
            return toSearchResultPage(dtos, page, size);
        }

        // JPA 路径：数据库层排序 + 分页
        Specification<Homestay> specification = buildSearchSpecification(request, typeCodeToSearch);
        Sort sort = buildJpaSort(request);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Homestay> homestayPage = homestayRepository.findAll(
                homestaySpecificationSupport.withDetailFetch(specification), pageable);

        List<String> finalCriteria = buildFinalCriteria(request, typeCodeToSearch);
        List<HomestayDTO> dtos = homestayDtoAssembler.toDTOs(homestayPage.getContent(), finalCriteria);
        dtos = applyPersonalization(dtos, request);
        List<HomestaySearchResultDTO> content = dtos.stream()
                .map(homestayDtoAssembler::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, homestayPage.getTotalElements());
    }

    private String resolveTypeCode(String propertyType) {
        if (!StringUtils.hasText(propertyType)) {
            return null;
        }
        String normalized = propertyType.trim();
        // 数据库 homestays.type 存的是中文名称，所以返回 name 用于匹配
        Optional<HomestayType> typeOpt = homestayTypeRepository.findByNameIgnoreCase(normalized);
        if (typeOpt.isPresent()) {
            return typeOpt.get().getName();
        }
        typeOpt = homestayTypeRepository.findByCode(normalized.toUpperCase());
        return typeOpt.map(HomestayType::getName).orElse(null);
    }

    private Specification<Homestay> buildSearchSpecification(HomestaySearchRequest request, String typeCodeToSearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), HomestayStatus.ACTIVE));

            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = "%" + request.getKeyword() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("title"), keyword),
                        criteriaBuilder.like(root.get("description"), keyword),
                        criteriaBuilder.like(root.get("addressDetail"), keyword)));
            }

            if (StringUtils.hasText(request.getLocation())) {
                String locationLike = "%" + request.getLocation() + "%";
                String locationExact = request.getLocation();
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("provinceText"), locationLike),
                        criteriaBuilder.like(root.get("cityText"), locationLike),
                        criteriaBuilder.like(root.get("districtText"), locationLike),
                        criteriaBuilder.like(root.get("addressDetail"), locationLike),
                        criteriaBuilder.equal(root.get("provinceCode"), locationExact),
                        criteriaBuilder.equal(root.get("cityCode"), locationExact),
                        criteriaBuilder.equal(root.get("districtCode"), locationExact)));
            }

            if (StringUtils.hasText(request.getProvinceCode())) {
                predicates.add(root.get("provinceCode")
                        .in(homestaySpecificationSupport.getProvinceCodeCandidates(request.getProvinceCode())));
            }
            if (StringUtils.hasText(request.getCityCode())) {
                predicates.add(root.get("cityCode")
                        .in(homestaySpecificationSupport.getCityCodeCandidates(request.getCityCode())));
            }
            if (StringUtils.hasText(request.getDistrictCode())) {
                predicates.add(criteriaBuilder.equal(root.get("districtCode"), request.getDistrictCode().trim()));
            }

            if (request.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }

            if (Stream.of(
                    request.getNorthEastLat(),
                    request.getNorthEastLng(),
                    request.getSouthWestLat(),
                    request.getSouthWestLng()).allMatch(Objects::nonNull)) {
                BigDecimal minLatitude = request.getNorthEastLat().min(request.getSouthWestLat());
                BigDecimal maxLatitude = request.getNorthEastLat().max(request.getSouthWestLat());
                BigDecimal minLongitude = request.getNorthEastLng().min(request.getSouthWestLng());
                BigDecimal maxLongitude = request.getNorthEastLng().max(request.getSouthWestLng());

                predicates.add(criteriaBuilder.isNotNull(root.get("latitude")));
                predicates.add(criteriaBuilder.isNotNull(root.get("longitude")));
                predicates.add(criteriaBuilder.between(root.get("latitude"), minLatitude, maxLatitude));
                predicates.add(criteriaBuilder.between(root.get("longitude"), minLongitude, maxLongitude));
            }

            if (request.getMinGuests() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("maxGuests"), request.getMinGuests()));
            }

            if (typeCodeToSearch != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), typeCodeToSearch));
            }

            List<String> requiredAmenityValues = request.getRequiredAmenities();
            if (requiredAmenityValues != null && !requiredAmenityValues.isEmpty()) {
                List<Amenity> requiredAmenities = amenityRepository.findByValueInIgnoreCase(requiredAmenityValues);
                if (!requiredAmenities.isEmpty()) {
                    for (Amenity amenity : requiredAmenities) {
                        predicates.add(criteriaBuilder.isMember(amenity, root.get("amenities")));
                    }
                } else {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }

            LocalDate checkInDate = request.getCheckInDate();
            LocalDate checkOutDate = request.getCheckOutDate();
            if (checkInDate != null && checkOutDate != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Order> orderRoot = subquery.from(Order.class);
                subquery.select(orderRoot.get("id"));

                Predicate homestayMatch = criteriaBuilder.equal(orderRoot.get("homestay"), root);
                Predicate statusMatch = orderRoot.get("status").in(
                        OrderStatus.CONFIRMED.name(),
                        OrderStatus.PAID.name());
                Predicate dateOverlap = criteriaBuilder.and(
                        criteriaBuilder.greaterThan(orderRoot.get("checkOutDate"), checkInDate),
                        criteriaBuilder.lessThan(orderRoot.get("checkInDate"), checkOutDate));

                subquery.where(criteriaBuilder.and(homestayMatch, statusMatch, dateOverlap));
                predicates.add(criteriaBuilder.not(criteriaBuilder.exists(subquery)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<String> buildFinalCriteria(HomestaySearchRequest request, String typeCodeToSearch) {
        List<String> criteriaFromSearch = new ArrayList<>();
        if (request.getRequiredAmenities() != null && !request.getRequiredAmenities().isEmpty()) {
            request.getRequiredAmenities().forEach(amenityCode ->
                    criteriaFromSearch.add("AMENITY_" + amenityCode.toUpperCase().replace(" ", "_")));
        }
        if (typeCodeToSearch != null) {
            criteriaFromSearch.add("PROPERTY_TYPE_" + typeCodeToSearch.toUpperCase());
        }
        return criteriaFromSearch.isEmpty() ? null : Collections.unmodifiableList(criteriaFromSearch);
    }

    private Sort buildJpaSort(HomestaySearchRequest request) {
        String sortParam = StringUtils.hasText(request.getSort()) ? request.getSort()
                : (StringUtils.hasText(request.getSortBy()) ? request.getSortBy() + "," + request.getSortDirection() : "");
        Map<String, String> fieldMapping = Map.of(
                "price", "price",
                "pricepernight", "price",
                "createdat", "createdAt",
                "created_at", "createdAt",
                "default", "id",
                "rating", "id"
        );
        Set<String> allowedFields = Set.of("id", "price", "createdAt", "updatedAt", "title");
        return SortUtils.parseSort(sortParam, fieldMapping, allowedFields);
    }

    private Page<HomestaySearchResultDTO> toSearchResultPage(List<HomestayDTO> dtos, int page, int size) {
        int total = dtos.size();
        int start = page * size;
        if (start >= total) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), total);
        }
        int end = Math.min(start + size, total);
        List<HomestaySearchResultDTO> content = dtos.subList(start, end).stream()
                .map(homestayDtoAssembler::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    @Override
    public List<MapClusterDTO> getMapClusters(HomestaySearchRequest request) {
        // 优先尝试 ES geohash_grid 聚合路径
        try {
            HomestayIndexingService indexingService = homestayIndexingServiceProvider.getIfAvailable();
            if (indexingService != null && indexingService.isElasticsearchAvailable()) {
                List<MapClusterDTO> esResults = getMapClustersByElasticsearch(request);
                if (esResults != null && !esResults.isEmpty()) {
                    return esResults;
                }
            }
        } catch (Exception e) {
            log.warn("ES map clusters failed, falling back to JPA: {}", e.getMessage());
        }

        // JPA 降级路径：内存网格聚合
        int zoom = request.getZoom() != null ? request.getZoom() : 12;
        double gridSize = resolveClusterGridSize(zoom);

        Map<String, MapClusterAccumulator> clusters = new LinkedHashMap<>();
        searchHomestays(request).stream()
                .filter(Objects::nonNull)
                .filter(homestay -> homestay.getLatitude() != null && homestay.getLongitude() != null)
                .forEach(homestay -> {
                    double latitude = homestay.getLatitude();
                    double longitude = homestay.getLongitude();
                    long latitudeBucket = (long) Math.floor(latitude / gridSize);
                    long longitudeBucket = (long) Math.floor(longitude / gridSize);
                    String key = latitudeBucket + ":" + longitudeBucket;

                    clusters.computeIfAbsent(key, ignored -> new MapClusterAccumulator())
                            .add(latitude, longitude);
                });

        return clusters.values().stream()
                .map(MapClusterAccumulator::toDTO)
                .sorted(Comparator.comparing(MapClusterDTO::getCount).reversed())
                .collect(Collectors.toList());
    }

    private List<MapClusterDTO> getMapClustersByElasticsearch(HomestaySearchRequest request) {
        ElasticsearchOperations elasticsearchOperations = elasticsearchOperationsProvider.getIfAvailable();
        if (elasticsearchOperations == null) {
            return Collections.emptyList();
        }

        try {
            String queryJson = buildViewportElasticsearchQueryJson(request);
            int geohashPrecision = resolveGeohashPrecision(request.getZoom());

            NativeQueryBuilder queryBuilder = new NativeQueryBuilder();
            queryBuilder.withQuery(Query.of(q -> q.wrapper(w -> w.query(queryJson))));
            queryBuilder.withMaxResults(0);
            queryBuilder.withAggregation("clusters", Aggregation.of(a -> a
                    .geohashGrid(ghg -> ghg
                            .field("location")
                            .precision(GeoHashPrecision.of(ghp -> ghp.geohashLength(geohashPrecision)))
                    )
                    .aggregations("avg_lat", Aggregation.of(sub -> sub.avg(avg -> avg.field("location.lat"))))
                    .aggregations("avg_lon", Aggregation.of(sub -> sub.avg(avg -> avg.field("location.lon"))))
            ));

            NativeQuery nativeQuery = queryBuilder.build();
            SearchHits<HomestayDocument> searchHits = elasticsearchOperations.search(nativeQuery, HomestayDocument.class);

            if (!searchHits.hasAggregations()) {
                return Collections.emptyList();
            }

            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation clusterAgg = aggregations.get("clusters");
            if (clusterAgg == null) {
                return Collections.emptyList();
            }

            Aggregate aggregate = clusterAgg.aggregation().getAggregate();
            if (!aggregate.isGeohashGrid()) {
                return Collections.emptyList();
            }

            GeoHashGridAggregate geoHashGrid = aggregate.geohashGrid();
            if (geoHashGrid.buckets() == null || !geoHashGrid.buckets().isArray()) {
                return Collections.emptyList();
            }

            List<MapClusterDTO> clusters = new ArrayList<>();
            for (GeoHashGridBucket bucket : geoHashGrid.buckets().array()) {
                double avgLat = 0.0;
                double avgLon = 0.0;

                Map<String, Aggregate> subAggs = bucket.aggregations();
                if (subAggs != null) {
                    if (subAggs.containsKey("avg_lat") && subAggs.get("avg_lat").isAvg()) {
                        avgLat = subAggs.get("avg_lat").avg().value();
                    }
                    if (subAggs.containsKey("avg_lon") && subAggs.get("avg_lon").isAvg()) {
                        avgLon = subAggs.get("avg_lon").avg().value();
                    }
                }

                clusters.add(MapClusterDTO.builder()
                        .latitude(Math.round(avgLat * 1_000_000d) / 1_000_000d)
                        .longitude(Math.round(avgLon * 1_000_000d) / 1_000_000d)
                        .count((int) bucket.docCount())
                        .build());
            }

            return clusters.stream()
                    .sorted(Comparator.comparing(MapClusterDTO::getCount).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("ES map clusters query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private int resolveGeohashPrecision(Integer zoom) {
        if (zoom == null) return 5;
        if (zoom <= 5) return 2;
        if (zoom <= 8) return 3;
        if (zoom <= 11) return 4;
        if (zoom <= 14) return 5;
        return 6;
    }

    @Override
    public List<HomestayDTO> getNearbyHomestays(HomestaySearchRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : 20;

        // 优先尝试 ES geo_distance 路径
        try {
            HomestayIndexingService indexingService = homestayIndexingServiceProvider.getIfAvailable();
            if (indexingService != null && indexingService.isElasticsearchAvailable()) {
                List<HomestayDTO> esResults = searchNearbyByElasticsearch(request);
                if (esResults != null && !esResults.isEmpty()) {
                    return esResults;
                }
            }
        } catch (Exception e) {
            log.warn("ES nearby search failed, falling back to JPA: {}", e.getMessage());
        }

        return searchHomestaysWithinRadius(request, limit);
    }

    @Override
    public List<HomestaySearchResultDTO> getNearbyHomestayResults(HomestaySearchRequest request) {
        return getNearbyHomestays(request).stream()
                .map(homestayDtoAssembler::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<HomestayDTO> searchHomestaysNearLandmark(HomestaySearchRequest request) {
        // 优先尝试 ES geo_distance 路径
        try {
            HomestayIndexingService indexingService = homestayIndexingServiceProvider.getIfAvailable();
            if (indexingService != null && indexingService.isElasticsearchAvailable()) {
                List<HomestayDTO> esResults = searchNearbyByElasticsearch(request);
                if (esResults != null && !esResults.isEmpty()) {
                    return esResults;
                }
            }
        } catch (Exception e) {
            log.warn("ES landmark search failed, falling back to JPA: {}", e.getMessage());
        }

        return searchHomestaysWithinRadius(request, request.getLimit());
    }

    @Override
    public List<HomestaySearchResultDTO> searchHomestayResultsNearLandmark(HomestaySearchRequest request) {
        return searchHomestaysNearLandmark(request).stream()
                .map(homestayDtoAssembler::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<HomestayDTO> searchHomestays(
            String keyword,
            String provinceCode,
            String cityCode,
            Integer minPrice,
            Integer maxPrice,
            Integer guests,
            String type) {
        BigDecimal minPriceDecimal = minPrice != null ? BigDecimal.valueOf(minPrice) : null;
        BigDecimal maxPriceDecimal = maxPrice != null ? BigDecimal.valueOf(maxPrice) : null;

        List<Homestay> homestays = homestayRepository.searchHomestays(
                keyword,
                minPriceDecimal,
                maxPriceDecimal,
                guests,
                type);
        return homestayDtoAssembler.toDTOs(homestays, null);
    }

    private double resolveClusterGridSize(int zoom) {
        int normalizedZoom = Math.max(1, Math.min(20, zoom));
        return Math.max(0.0005d, 180.0d / Math.pow(2.0d, normalizedZoom));
    }

    private List<HomestayDTO> applyPersonalization(List<HomestayDTO> results, HomestaySearchRequest request) {
        if (results == null || results.isEmpty()) {
            return results;
        }
        // 仅在未指定显式排序时应用个性化
        if (StringUtils.hasText(request.getSortBy()) || StringUtils.hasText(request.getSort())) {
            return results;
        }
        Long userId = request.getUserId();
        if (userId == null) {
            try {
                userId = UserUtil.getCurrentUserId();
            } catch (IllegalStateException e) {
                // 未登录用户，跳过个性化
                return results;
            }
        }
        return searchPersonalizationService.boost(results, userId);
    }

    private List<HomestayDTO> applySearchSorting(List<HomestayDTO> results, HomestaySearchRequest request) {
        if (results == null || results.isEmpty() || request == null || !StringUtils.hasText(request.getSortBy())) {
            return results;
        }

        if (isDistanceSort(request.getSortBy())) {
            if (!hasDistanceSortOrigin(request)) {
                log.warn("Distance sort requested without origin coordinates");
                return results;
            }
            double latitude = request.getLatitude().doubleValue();
            double longitude = request.getLongitude().doubleValue();
            results.forEach(homestay -> attachDistanceKm(homestay, latitude, longitude));
            boolean descending = isDescending(request.getSortDirection());
            Comparator<HomestayDTO> comparator = (left, right) -> compareDistanceKm(left, right, descending);
            return results.stream()
                    .sorted(comparator.thenComparing(HomestayDTO::getId, Comparator.nullsLast(Long::compareTo)))
                    .collect(Collectors.toList());
        }

        if (isRatingSort(request.getSortBy())) {
            boolean descending = isDescending(request.getSortDirection());
            // 批量预加载评分，避免 N+1
            List<Long> ids = results.stream().map(HomestayDTO::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                Map<Long, Double> ratingMap = toMapDouble(reviewRepository.getAverageRatingByHomestayIds(ids));
                results.forEach(dto -> dto.setRating(ratingMap.getOrDefault(dto.getId(), 0.0)));
            }
            Comparator<HomestayDTO> comparator = (left, right) -> {
                Double leftRating = left.getRating();
                Double rightRating = right.getRating();
                if (leftRating == null && rightRating == null) return 0;
                if (leftRating == null) return 1;
                if (rightRating == null) return -1;
                int result = Double.compare(leftRating, rightRating);
                return descending ? -result : result;
            };
            return results.stream()
                    .sorted(comparator.thenComparing(HomestayDTO::getId, Comparator.nullsLast(Long::compareTo)))
                    .collect(Collectors.toList());
        }

        return results;
    }

    private boolean isDistanceSort(String sortBy) {
        return "DISTANCE".equalsIgnoreCase(sortBy)
                || "distance".equalsIgnoreCase(sortBy)
                || "distanceKm".equalsIgnoreCase(sortBy);
    }

    private boolean isRatingSort(String sortBy) {
        return "RATING".equalsIgnoreCase(sortBy)
                || "rating".equalsIgnoreCase(sortBy);
    }

    private boolean hasDistanceSortOrigin(HomestaySearchRequest request) {
        return request.getLatitude() != null && request.getLongitude() != null;
    }

    private boolean isDescending(String sortDirection) {
        return "desc".equalsIgnoreCase(sortDirection)
                || "descending".equalsIgnoreCase(sortDirection);
    }

    private int compareDistanceKm(HomestayDTO left, HomestayDTO right, boolean descending) {
        Double leftDistance = left.getDistanceKm();
        Double rightDistance = right.getDistanceKm();

        if (leftDistance == null && rightDistance == null) {
            return 0;
        }
        if (leftDistance == null) {
            return 1;
        }
        if (rightDistance == null) {
            return -1;
        }

        int result = Double.compare(leftDistance, rightDistance);
        return descending ? -result : result;
    }

    private List<HomestayDTO> searchHomestaysWithinRadius(HomestaySearchRequest request, Integer limit) {
        double latitude = request.getLatitude().doubleValue();
        double longitude = request.getLongitude().doubleValue();
        double radiusKm = request.getRadiusKm().doubleValue();

        applyNearbyBoundingBox(request, latitude, longitude, radiusKm);

        Stream<HomestayDTO> results = searchHomestays(request).stream()
                .filter(Objects::nonNull)
                .filter(homestay -> homestay.getLatitude() != null && homestay.getLongitude() != null)
                .filter(homestay -> calculateDistanceKm(
                        latitude,
                        longitude,
                        homestay.getLatitude(),
                        homestay.getLongitude()) <= radiusKm)
                .map(homestay -> {
                    double distanceKm = calculateDistanceKm(
                            latitude,
                            longitude,
                            homestay.getLatitude(),
                            homestay.getLongitude());
                    homestay.setDistanceKm(roundDistance(distanceKm));
                    return homestay;
                })
                .sorted(Comparator.comparing(HomestayDTO::getDistanceKm));

        if (limit != null) {
            results = results.limit(limit);
        }

        return results.collect(Collectors.toList());
    }

    private void applyNearbyBoundingBox(
            HomestaySearchRequest request,
            double latitude,
            double longitude,
            double radiusKm) {
        if (Stream.of(
                request.getNorthEastLat(),
                request.getNorthEastLng(),
                request.getSouthWestLat(),
                request.getSouthWestLng()).allMatch(Objects::nonNull)) {
            return;
        }

        double latitudeDelta = radiusKm / 111.32d;
        double longitudeScale = Math.max(Math.cos(Math.toRadians(latitude)), 0.000001d);
        double longitudeDelta = radiusKm / (111.32d * longitudeScale);
        double minLongitude = longitude - longitudeDelta;
        double maxLongitude = longitude + longitudeDelta;

        if (minLongitude < -180d || maxLongitude > 180d) {
            return;
        }

        request.setNorthEastLat(BigDecimal.valueOf(Math.min(90d, latitude + latitudeDelta)));
        request.setSouthWestLat(BigDecimal.valueOf(Math.max(-90d, latitude - latitudeDelta)));
        request.setNorthEastLng(BigDecimal.valueOf(maxLongitude));
        request.setSouthWestLng(BigDecimal.valueOf(minLongitude));
    }

    private double calculateDistanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double earthRadiusKm = 6371.0088d;
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);

        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private void attachDistanceKm(HomestayDTO homestay, double latitude, double longitude) {
        if (homestay == null || homestay.getLatitude() == null || homestay.getLongitude() == null) {
            return;
        }

        double distanceKm = calculateDistanceKm(
                latitude,
                longitude,
                homestay.getLatitude(),
                homestay.getLongitude());
        homestay.setDistanceKm(roundDistance(distanceKm));
    }

    private double roundDistance(double value) {
        return Math.round(value * 100d) / 100d;
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Double> toMapDouble(List<Object[]> rows) {
        if (rows == null) return Map.of();
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(),
                r -> ((Number) r[1]).doubleValue(),
                (a, b) -> a));
    }

    private List<HomestayDTO> searchNearbyByElasticsearch(HomestaySearchRequest request) {
        ElasticsearchOperations elasticsearchOperations = elasticsearchOperationsProvider.getIfAvailable();
        if (elasticsearchOperations == null) {
            return Collections.emptyList();
        }

        try {
            String queryJson = buildNearbyElasticsearchQueryJson(request);
            int limit = request.getLimit() != null ? request.getLimit() : 20;
            // 多取一些以应对后续日期冲突过滤
            int fetchSize = Math.max(limit * 3, 100);
            double centerLat = request.getLatitude().doubleValue();
            double centerLon = request.getLongitude().doubleValue();

            NativeQueryBuilder queryBuilder = new NativeQueryBuilder();
            queryBuilder.withQuery(Query.of(q -> q.wrapper(w -> w.query(queryJson))));
            queryBuilder.withMaxResults(fetchSize);
            queryBuilder.withSort(SortOptions.of(s -> s
                    .geoDistance(gd -> gd
                            .field("location")
                            .location(l -> l.latlon(ll -> ll.lat(centerLat).lon(centerLon)))
                            .order(SortOrder.Asc)
                            .unit(DistanceUnit.Kilometers)
                    )
            ));

            NativeQuery nativeQuery = queryBuilder.build();
            SearchHits<HomestayDocument> searchHits = elasticsearchOperations.search(
                    nativeQuery, HomestayDocument.class);

            if (searchHits == null || searchHits.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> homestayIds = new ArrayList<>();
            Map<Long, Double> distanceMap = new HashMap<>();
            for (org.springframework.data.elasticsearch.core.SearchHit<HomestayDocument> hit : searchHits.getSearchHits()) {
                Long id = hit.getContent().getId();
                homestayIds.add(id);
                List<Object> sortValues = hit.getSortValues();
                if (sortValues != null && !sortValues.isEmpty() && sortValues.get(0) instanceof Number) {
                    distanceMap.put(id, ((Number) sortValues.get(0)).doubleValue());
                }
            }

            if (homestayIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Homestay> homestays = homestayRepository.findAllById(homestayIds);
            Map<Long, Homestay> homestayMap = homestays.stream()
                    .collect(Collectors.toMap(Homestay::getId, h -> h, (a, b) -> a));
            List<Homestay> orderedHomestays = homestayIds.stream()
                    .map(homestayMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 日期可用性冲突过滤（ES 路径暂无法前置）
            if (request.getCheckInDate() != null && request.getCheckOutDate() != null) {
                List<Long> conflictIds = orderRepository.findConflictingHomestayIds(
                        homestayIds, request.getCheckInDate(), request.getCheckOutDate());
                if (conflictIds != null && !conflictIds.isEmpty()) {
                    orderedHomestays = orderedHomestays.stream()
                            .filter(h -> !conflictIds.contains(h.getId()))
                            .collect(Collectors.toList());
                }
            }

            List<String> finalCriteria = buildFinalCriteria(request, resolveTypeCode(request.getPropertyType()));
            List<HomestayDTO> dtos = homestayDtoAssembler.toDTOs(orderedHomestays, finalCriteria);

            // 从 ES _geo_distance sort 获取精确距离
            for (HomestayDTO dto : dtos) {
                Double distance = distanceMap.get(dto.getId());
                if (distance != null) {
                    dto.setDistanceKm(roundDistance(distance));
                }
            }

            // 日期冲突过滤可能破坏 ES 排序，重新按距离排序（null-safe，无距离排最后）
            dtos.sort(Comparator.comparing(HomestayDTO::getDistanceKm,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            if (dtos.size() > limit) {
                return dtos.subList(0, limit);
            }
            return dtos;
        } catch (Exception e) {
            log.warn("ES nearby search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<HomestayDTO> searchViewportByElasticsearch(HomestaySearchRequest request) {
        ElasticsearchOperations elasticsearchOperations = elasticsearchOperationsProvider.getIfAvailable();
        if (elasticsearchOperations == null) {
            return Collections.emptyList();
        }

        try {
            String queryJson = buildViewportElasticsearchQueryJson(request);
            StringQuery stringQuery = new StringQuery(queryJson, PageRequest.of(0, 10000));
            SearchHits<HomestayDocument> searchHits = elasticsearchOperations.search(
                    stringQuery, HomestayDocument.class);

            if (searchHits == null || searchHits.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> homestayIds = searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (homestayIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Homestay> homestays = homestayRepository.findAllById(homestayIds);
            Map<Long, Homestay> homestayMap = homestays.stream()
                    .collect(Collectors.toMap(Homestay::getId, h -> h, (a, b) -> a));
            List<Homestay> orderedHomestays = homestayIds.stream()
                    .map(homestayMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 日期可用性冲突过滤（ES 路径暂无法前置）
            if (request.getCheckInDate() != null && request.getCheckOutDate() != null) {
                List<Long> conflictIds = orderRepository.findConflictingHomestayIds(
                        homestayIds, request.getCheckInDate(), request.getCheckOutDate());
                if (conflictIds != null && !conflictIds.isEmpty()) {
                    orderedHomestays = orderedHomestays.stream()
                            .filter(h -> !conflictIds.contains(h.getId()))
                            .collect(Collectors.toList());
                }
            }

            List<String> finalCriteria = buildFinalCriteria(request, resolveTypeCode(request.getPropertyType()));
            return homestayDtoAssembler.toDTOs(orderedHomestays, finalCriteria);
        } catch (Exception e) {
            log.warn("ES viewport search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<HomestayDTO> searchByElasticsearch(HomestaySearchRequest request) {
        String keyword = request.getKeyword();
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        ElasticsearchOperations elasticsearchOperations = elasticsearchOperationsProvider.getIfAvailable();
        if (elasticsearchOperations == null) {
            return Collections.emptyList();
        }

        String queryJson = null;
        try {
            queryJson = buildElasticsearchQueryJson(request);
            StringQuery stringQuery = new StringQuery(queryJson, PageRequest.of(0, 10000));
            SearchHits<HomestayDocument> searchHits = elasticsearchOperations.search(
                    stringQuery, HomestayDocument.class);
            if (searchHits == null || searchHits.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> homestayIds = searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (homestayIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 用 ID 列表去 JPA 查询完整实体，保留原有 DTO 组装逻辑
            List<Homestay> homestays = homestayRepository.findAllById(homestayIds);

            // 保持 ES 返回的顺序
            Map<Long, Homestay> homestayMap = homestays.stream()
                    .collect(Collectors.toMap(Homestay::getId, h -> h, (a, b) -> a));
            List<Homestay> orderedHomestays = homestayIds.stream()
                    .map(homestayMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 补充：可订日期检查（ES 搜索绕过了 JPA 的日期冲突子查询）
            if (request.getCheckInDate() != null && request.getCheckOutDate() != null) {
                List<Long> conflictIds = orderRepository.findConflictingHomestayIds(
                        homestayIds, request.getCheckInDate(), request.getCheckOutDate());
                if (conflictIds != null && !conflictIds.isEmpty()) {
                    orderedHomestays = orderedHomestays.stream()
                            .filter(h -> !conflictIds.contains(h.getId()))
                            .collect(Collectors.toList());
                }
            }

            List<String> finalCriteria = buildFinalCriteria(request, resolveTypeCode(request.getPropertyType()));
            return homestayDtoAssembler.toDTOs(orderedHomestays, finalCriteria);
        } catch (Exception e) {
            log.warn("ES advanced search failed: {} | queryJson={}", e.getMessage(), queryJson, e);
            return Collections.emptyList();
        }
    }

    private StringBuilder buildCommonElasticsearchFilters(HomestaySearchRequest request) {
        double minPrice = request.getMinPrice() != null ? request.getMinPrice().doubleValue() : 0.0;
        int minGuests = request.getMinGuests() != null ? request.getMinGuests() : 1;

        StringBuilder filters = new StringBuilder();
        // 无上限时省略 lte：Double.MAX_VALUE 经 %.2f 序列化成 312 位数字，ES JSON 解析失败（all shards failed）
        if (request.getMaxPrice() != null) {
            filters.append(String.format("{\"range\":{\"price\":{\"gte\":%.2f,\"lte\":%.2f}}},", minPrice, request.getMaxPrice().doubleValue()));
        } else {
            filters.append(String.format("{\"range\":{\"price\":{\"gte\":%.2f}}},", minPrice));
        }
        filters.append(String.format("{\"range\":{\"maxGuests\":{\"gte\":%d}}},", minGuests));

        if (StringUtils.hasText(request.getProvinceCode())) {
            Set<String> candidates = homestaySpecificationSupport.getProvinceCodeCandidates(request.getProvinceCode());
            filters.append("{\"terms\":{\"provinceCode\":[");
            boolean first = true;
            for (String c : candidates) {
                if (!first) filters.append(",");
                filters.append("\"").append(jsonEscape(c)).append("\"");
                first = false;
            }
            filters.append("]}},");
        }
        if (StringUtils.hasText(request.getCityCode())) {
            Set<String> candidates = homestaySpecificationSupport.getCityCodeCandidates(request.getCityCode());
            filters.append("{\"terms\":{\"cityCode\":[");
            boolean first = true;
            for (String c : candidates) {
                if (!first) filters.append(",");
                filters.append("\"").append(jsonEscape(c)).append("\"");
                first = false;
            }
            filters.append("]}},");
        }
        if (StringUtils.hasText(request.getDistrictCode())) {
            filters.append(String.format("{\"term\":{\"districtCode\":\"%s\"}},",
                    jsonEscape(request.getDistrictCode().trim())));
        }
        String typeCode = resolveTypeCode(request.getPropertyType());
        if (typeCode != null) {
            filters.append(String.format("{\"term\":{\"type\":\"%s\"}},", jsonEscape(typeCode)));
        }
        if (request.getRequiredAmenities() != null && !request.getRequiredAmenities().isEmpty()) {
            for (String a : request.getRequiredAmenities()) {
                filters.append(String.format("{\"term\":{\"amenities\":\"%s\"}},", jsonEscape(a)));
            }
        }
        return filters;
    }

    private String buildElasticsearchQueryJson(HomestaySearchRequest request) {
        String keyword = jsonEscape(request.getKeyword());
        StringBuilder filters = buildCommonElasticsearchFilters(request);

        // location 文本筛选：匹配 addressDetail 或地区编码
        if (StringUtils.hasText(request.getLocation())) {
            String locationExact = jsonEscape(request.getLocation().trim());
            String locationFuzzy = jsonEscape(request.getLocation().trim());
            filters.append(String.format(
                    "{\"bool\":{\"should\":[" +
                    "{\"match\":{\"addressDetail\":\"%s\"}}," +
                    "{\"term\":{\"provinceCode\":\"%s\"}}," +
                    "{\"term\":{\"cityCode\":\"%s\"}}," +
                    "{\"term\":{\"districtCode\":\"%s\"}}" +
                    "],\"minimum_should_match\":1}},",
                    locationFuzzy, locationExact, locationExact, locationExact));
        }

        if (Stream.of(request.getNorthEastLat(), request.getNorthEastLng(),
                request.getSouthWestLat(), request.getSouthWestLng()).allMatch(Objects::nonNull)) {
            double topLat = request.getNorthEastLat().max(request.getSouthWestLat()).doubleValue();
            double bottomLat = request.getNorthEastLat().min(request.getSouthWestLat()).doubleValue();
            double leftLng = request.getNorthEastLng().min(request.getSouthWestLng()).doubleValue();
            double rightLng = request.getNorthEastLng().max(request.getSouthWestLng()).doubleValue();
            filters.append(String.format(
                    "{\"geo_bounding_box\":{\"location\":{\"top_left\":{\"lat\":%.6f,\"lon\":%.6f},\"bottom_right\":{\"lat\":%.6f,\"lon\":%.6f}}}},",
                    topLat, leftLng, bottomLat, rightLng));
        }

        String filterStr = filters.toString();
        if (filterStr.endsWith(",")) {
            filterStr = filterStr.substring(0, filterStr.length() - 1);
        }

        return String.format(
                "{\"function_score\":{\"query\":{\"bool\":{\"must\":[{\"match\":{\"status\":\"ACTIVE\"}},{\"multi_match\":{\"query\":\"%s\",\"fields\":[\"title^3\",\"description^2\",\"addressDetail^2\",\"amenities\"],\"type\":\"best_fields\",\"fuzziness\":\"AUTO\"}}],\"filter\":[%s]}},\"functions\":[{\"field_value_factor\":{\"field\":\"rating\",\"factor\":0.20,\"modifier\":\"log1p\",\"missing\":0}},{\"field_value_factor\":{\"field\":\"bookingCount\",\"factor\":0.075,\"modifier\":\"log1p\",\"missing\":0}},{\"field_value_factor\":{\"field\":\"favoriteCount\",\"factor\":0.075,\"modifier\":\"log1p\",\"missing\":0}},{\"gauss\":{\"createdAt\":{\"origin\":\"now\",\"scale\":\"30d\",\"decay\":0.5}}}],\"score_mode\":\"sum\",\"boost_mode\":\"multiply\"}}",
                keyword, filterStr);
    }

    private String buildNearbyElasticsearchQueryJson(HomestaySearchRequest request) {
        StringBuilder filters = buildCommonElasticsearchFilters(request);

        // geo_distance: 圆形范围过滤
        double lat = request.getLatitude().doubleValue();
        double lon = request.getLongitude().doubleValue();
        double radiusKm = request.getRadiusKm() != null ? request.getRadiusKm().doubleValue() : 20.0;
        filters.append(String.format(
                "{\"geo_distance\":{\"distance\":\"%.2fkm\",\"location\":{\"lat\":%.6f,\"lon\":%.6f}}},",
                radiusKm, lat, lon));

        String filterStr = filters.toString();
        if (filterStr.endsWith(",")) {
            filterStr = filterStr.substring(0, filterStr.length() - 1);
        }

        return String.format(
                "{\"bool\":{\"must\":[{\"match\":{\"status\":\"ACTIVE\"}}],\"filter\":[%s]}}",
                filterStr);
    }

    private String buildViewportElasticsearchQueryJson(HomestaySearchRequest request) {
        StringBuilder filters = buildCommonElasticsearchFilters(request);

        // location 文本筛选
        if (StringUtils.hasText(request.getLocation())) {
            String locationExact = jsonEscape(request.getLocation().trim());
            String locationFuzzy = jsonEscape(request.getLocation().trim());
            filters.append(String.format(
                    "{\"bool\":{\"should\":[" +
                    "{\"match\":{\"addressDetail\":\"%s\"}}," +
                    "{\"term\":{\"provinceCode\":\"%s\"}}," +
                    "{\"term\":{\"cityCode\":\"%s\"}}," +
                    "{\"term\":{\"districtCode\":\"%s\"}}" +
                    "],\"minimum_should_match\":1}},",
                    locationFuzzy, locationExact, locationExact, locationExact));
        }

        // geo_bounding_box: 视口范围过滤
        if (Stream.of(request.getNorthEastLat(), request.getNorthEastLng(),
                request.getSouthWestLat(), request.getSouthWestLng()).allMatch(Objects::nonNull)) {
            double topLat = request.getNorthEastLat().max(request.getSouthWestLat()).doubleValue();
            double bottomLat = request.getNorthEastLat().min(request.getSouthWestLat()).doubleValue();
            double leftLng = request.getNorthEastLng().min(request.getSouthWestLng()).doubleValue();
            double rightLng = request.getNorthEastLng().max(request.getSouthWestLng()).doubleValue();
            filters.append(String.format(
                    "{\"geo_bounding_box\":{\"location\":{\"top_left\":{\"lat\":%.6f,\"lon\":%.6f},\"bottom_right\":{\"lat\":%.6f,\"lon\":%.6f}}}},",
                    topLat, leftLng, bottomLat, rightLng));
        }

        String filterStr = filters.toString();
        if (filterStr.endsWith(",")) {
            filterStr = filterStr.substring(0, filterStr.length() - 1);
        }

        return String.format(
                "{\"bool\":{\"must\":[{\"match\":{\"status\":\"ACTIVE\"}}],\"filter\":[%s]}}",
                filterStr);
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static class MapClusterAccumulator {
        private double latitudeSum;
        private double longitudeSum;
        private int count;

        void add(double latitude, double longitude) {
            latitudeSum += latitude;
            longitudeSum += longitude;
            count++;
        }

        MapClusterDTO toDTO() {
            return MapClusterDTO.builder()
                    .latitude(roundCoordinate(latitudeSum / count))
                    .longitude(roundCoordinate(longitudeSum / count))
                    .count(count)
                    .build();
        }

        private static double roundCoordinate(double value) {
            return Math.round(value * 1_000_000d) / 1_000_000d;
        }
    }
}
