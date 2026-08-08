package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.HomestayAdminDetailDTO;
import com.homestay3.homestaybackend.dto.HomestayAdminSummaryDTO;
import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.HomestayDetailDTO;
import com.homestay3.homestaybackend.dto.HomestaySearchResultDTO;
import com.homestay3.homestaybackend.dto.HomestaySummaryDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayType;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.mapper.HomestayMapper;
import com.homestay3.homestaybackend.repository.HomestayTypeRepository;
import com.homestay3.homestaybackend.service.AmenityService;
import com.homestay3.homestaybackend.service.HomestayFeatureAnalysisService;
import com.homestay3.homestaybackend.util.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomestayDtoAssembler {

    private final HomestayTypeRepository homestayTypeRepository;
    private final AmenityService amenityService;
    private final HomestayFeatureAnalysisService homestayFeatureAnalysisService;
    private final ImageUrlUtil imageUrlUtil;
    private final HomestayMapper homestayMapper;

    public List<HomestayDTO> toDTOs(Collection<Homestay> homestays, List<String> referringSearchCriteria) {
        return toDTOs(homestays, referringSearchCriteria, true);
    }

    public List<HomestayDTO> toDTOs(
            Collection<Homestay> homestays,
            List<String> referringSearchCriteria,
            boolean enrichFeatures) {
        if (homestays == null || homestays.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, String> propertyTypeNameMap = resolvePropertyTypeNames(homestays);
        List<HomestayDTO> result = new ArrayList<>();

        for (Homestay homestay : homestays) {
            try {
                if (homestay == null) {
                    log.warn("Skip null homestay while assembling DTO list");
                    continue;
                }

                HomestayDTO dto = toDTO(homestay, referringSearchCriteria, propertyTypeNameMap, enrichFeatures);
                if (dto != null) {
                    result.add(dto);
                }
            } catch (Exception exception) {
                log.error(
                        "Failed to assemble homestay {} to DTO: {}",
                        homestay != null ? homestay.getId() : null,
                        exception.getMessage(),
                        exception);
            }
        }

        return result;
    }

    public Page<HomestayDTO> toDTOPage(Page<Homestay> homestaysPage, List<String> referringSearchCriteria) {
        return toDTOPage(homestaysPage, referringSearchCriteria, true);
    }

    public Page<HomestayDTO> toDTOPage(
            Page<Homestay> homestaysPage,
            List<String> referringSearchCriteria,
            boolean enrichFeatures) {
        return new PageImpl<>(
                toDTOs(homestaysPage.getContent(), referringSearchCriteria, enrichFeatures),
                homestaysPage.getPageable(),
                homestaysPage.getTotalElements());
    }

    public HomestayDTO toDTO(Homestay homestay, List<String> referringSearchCriteria) {
        return toDTO(homestay, referringSearchCriteria, true);
    }

    public HomestayDTO toDTO(Homestay homestay, List<String> referringSearchCriteria, boolean enrichFeatures) {
        return toDTO(homestay, referringSearchCriteria, Collections.emptyMap(), enrichFeatures);
    }

    public List<HomestaySummaryDTO> toSummaryDTOs(
            Collection<Homestay> homestays,
            List<String> referringSearchCriteria) {
        return toDTOs(homestays, referringSearchCriteria, false).stream()
                .map(this::toSummaryDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Page<HomestaySummaryDTO> toSummaryDTOPage(
            Page<Homestay> homestaysPage,
            List<String> referringSearchCriteria) {
        return new PageImpl<>(
                toSummaryDTOs(homestaysPage.getContent(), referringSearchCriteria),
                homestaysPage.getPageable(),
                homestaysPage.getTotalElements());
    }

    public HomestaySummaryDTO toSummaryDTO(Homestay homestay, List<String> referringSearchCriteria) {
        return toSummaryDTO(toDTO(homestay, referringSearchCriteria, false));
    }

    public List<HomestaySummaryDTO> toSummaryDTOsFromDTOs(Collection<HomestayDTO> homestays) {
        if (homestays == null || homestays.isEmpty()) {
            return new ArrayList<>();
        }

        return homestays.stream()
                .map(this::toSummaryDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Page<HomestaySummaryDTO> toSummaryDTOPageFromDTOPage(Page<HomestayDTO> homestaysPage) {
        return new PageImpl<>(
                toSummaryDTOsFromDTOs(homestaysPage.getContent()),
                homestaysPage.getPageable(),
                homestaysPage.getTotalElements());
    }

    public HomestayDetailDTO toDetailDTO(Homestay homestay, List<String> referringSearchCriteria) {
        return toDetailDTO(toDTO(homestay, referringSearchCriteria));
    }

    public List<HomestayDetailDTO> toDetailDTOs(
            Collection<Homestay> homestays,
            List<String> referringSearchCriteria) {
        return toDTOs(homestays, referringSearchCriteria).stream()
                .map(this::toDetailDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<HomestaySearchResultDTO> toSearchResultDTOs(
            Collection<Homestay> homestays,
            List<String> referringSearchCriteria) {
        return toDTOs(homestays, referringSearchCriteria).stream()
                .map(this::toSearchResultDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public HomestaySearchResultDTO toSearchResultDTO(HomestayDTO dto) {
        if (dto == null) {
            return null;
        }

        return HomestaySearchResultDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .type(dto.getType())
                .propertyTypeName(dto.getPropertyTypeName())
                .price(dto.getPrice())
                .status(dto.getStatus())
                .maxGuests(dto.getMaxGuests())
                .provinceText(dto.getProvinceText())
                .cityText(dto.getCityText())
                .districtText(dto.getDistrictText())
                .addressDetail(dto.getAddressDetail())
                .coverImage(dto.getCoverImage())
                .images(copyList(dto.getImages()))
                .amenities(copyList(dto.getAmenities()))
                .suggestedFeatures(copyList(dto.getSuggestedFeatures()))
                .ownerId(dto.getOwnerId())
                .ownerUsername(dto.getOwnerUsername())
                .ownerName(dto.getOwnerName())
                .ownerAvatar(dto.getOwnerAvatar())
                .ownerRating(dto.getOwnerRating())
                .featured(dto.getFeatured())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .distanceKm(dto.getDistanceKm())
                .build();
    }

    public Page<HomestayAdminSummaryDTO> toAdminSummaryDTOPage(
            Page<Homestay> homestaysPage,
            List<String> referringSearchCriteria) {
        if (homestaysPage == null) {
            return new PageImpl<>(new ArrayList<>(), Pageable.unpaged(), 0);
        }
        List<HomestayAdminSummaryDTO> content = toAdminSummaryDTOs(homestaysPage.getContent());
        return new PageImpl<>(content, homestaysPage.getPageable(), homestaysPage.getTotalElements());
    }

    private List<HomestayAdminSummaryDTO> toAdminSummaryDTOs(Collection<Homestay> homestays) {
        if (homestays == null || homestays.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> propertyTypeNameMap = resolvePropertyTypeNames(homestays);
        List<HomestayAdminSummaryDTO> result = new ArrayList<>();
        for (Homestay homestay : homestays) {
            try {
                if (homestay == null) {
                    log.warn("Skip null homestay while assembling admin summary DTO list");
                    continue;
                }
                HomestayAdminSummaryDTO dto = buildAdminSummaryDTO(homestay, propertyTypeNameMap);
                if (dto != null) {
                    result.add(dto);
                }
            } catch (Exception exception) {
                log.error(
                        "Failed to assemble homestay {} to admin summary DTO: {}",
                        homestay != null ? homestay.getId() : null,
                        exception.getMessage(),
                        exception);
            }
        }
        return result;
    }

    private HomestayAdminSummaryDTO buildAdminSummaryDTO(Homestay homestay, Map<String, String> propertyTypeNameMap) {
        if (homestay == null) {
            return null;
        }
        try {
            String propertyTypeName = resolvePropertyTypeName(homestay.getType(), propertyTypeNameMap);
            String coverImage = homestay.getCoverImage();
            if (imageUrlUtil != null) {
                coverImage = imageUrlUtil.ensureCompleteImageUrl(coverImage);
            }

            Long ownerId = null;
            String ownerUsername = null;
            String ownerName = null;
            if (homestay.getOwner() != null) {
                User owner = homestay.getOwner();
                ownerId = owner.getId();
                ownerUsername = owner.getUsername();
                ownerName = owner.getRealName();
                if (!StringUtils.hasText(ownerName)) {
                    ownerName = owner.getNickname();
                }
                if (!StringUtils.hasText(ownerName)) {
                    ownerName = owner.getUsername();
                }
            }

            return HomestayAdminSummaryDTO.builder()
                    .id(homestay.getId())
                    .title(homestay.getTitle())
                    .type(homestay.getType())
                    .propertyTypeName(propertyTypeName)
                    .price(homestay.getPrice() != null ? homestay.getPrice().toString() : "0")
                    .status(homestay.getStatus() != null ? homestay.getStatus().name() : "UNKNOWN")
                    .maxGuests(homestay.getMaxGuests())
                    .provinceText(homestay.getProvinceText())
                    .cityText(homestay.getCityText())
                    .districtText(homestay.getDistrictText())
                    .addressDetail(homestay.getAddressDetail())
                    .coverImage(coverImage)
                    .ownerId(ownerId)
                    .ownerUsername(ownerUsername)
                    .ownerName(ownerName)
                    .featured(homestay.getFeatured() != null ? homestay.getFeatured() : false)
                    .createdAt(homestay.getCreatedAt())
                    .updatedAt(homestay.getUpdatedAt())
                    .latitude(homestay.getLatitude() != null ? homestay.getLatitude().doubleValue() : null)
                    .longitude(homestay.getLongitude() != null ? homestay.getLongitude().doubleValue() : null)
                    .build();
        } catch (Exception exception) {
            log.error("Failed to assemble homestay {} to admin summary DTO", homestay.getId(), exception);
            return null;
        }
    }

    public HomestayAdminDetailDTO toAdminDetailDTO(HomestayDTO dto) {
        if (dto == null) {
            return null;
        }

        return HomestayAdminDetailDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .type(dto.getType())
                .propertyTypeName(dto.getPropertyTypeName())
                .price(dto.getPrice())
                .status(dto.getStatus())
                .maxGuests(dto.getMaxGuests())
                .minNights(dto.getMinNights())
                .maxNights(dto.getMaxNights())
                .provinceText(dto.getProvinceText())
                .cityText(dto.getCityText())
                .districtText(dto.getDistrictText())
                .addressDetail(dto.getAddressDetail())
                .provinceCode(dto.getProvinceCode())
                .cityCode(dto.getCityCode())
                .districtCode(dto.getDistrictCode())
                .description(dto.getDescription())
                .coverImage(dto.getCoverImage())
                .images(copyList(dto.getImages()))
                .amenities(copyList(dto.getAmenities()))
                .ownerId(dto.getOwnerId())
                .ownerUsername(dto.getOwnerUsername())
                .ownerName(dto.getOwnerName())
                .ownerAvatar(dto.getOwnerAvatar())
                .ownerRating(dto.getOwnerRating())
                .ownerPhone(dto.getOwnerPhone())
                .ownerEmail(dto.getOwnerEmail())
                .ownerRealName(dto.getOwnerRealName())
                .ownerNickname(dto.getOwnerNickname())
                .ownerOccupation(dto.getOwnerOccupation())
                .ownerIntroduction(dto.getOwnerIntroduction())
                .ownerJoinDate(dto.getOwnerJoinDate())
                .ownerHostSince(dto.getOwnerHostSince())
                .ownerHomestayCount(dto.getOwnerHomestayCount())
                .ownerHostRating(dto.getOwnerHostRating())
                .featured(dto.getFeatured())
                .autoConfirm(dto.getAutoConfirm())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();
    }

    public HomestaySummaryDTO toSummaryDTO(HomestayDTO dto) {
        if (dto == null) {
            return null;
        }

        return HomestaySummaryDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .type(dto.getType())
                .propertyTypeName(dto.getPropertyTypeName())
                .price(dto.getPrice())
                .status(dto.getStatus())
                .maxGuests(dto.getMaxGuests())
                .provinceText(dto.getProvinceText())
                .cityText(dto.getCityText())
                .districtText(dto.getDistrictText())
                .addressDetail(dto.getAddressDetail())
                .coverImage(dto.getCoverImage())
                .images(copyList(dto.getImages()))
                .ownerId(dto.getOwnerId())
                .ownerUsername(dto.getOwnerUsername())
                .ownerName(dto.getOwnerName())
                .ownerAvatar(dto.getOwnerAvatar())
                .ownerRating(dto.getOwnerRating())
                .featured(dto.getFeatured())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .groupId(dto.getGroupId())
                .groupName(dto.getGroupName())
                .groupCode(dto.getGroupCode())
                .groupColor(dto.getGroupColor())
                .build();
    }

    private HomestayDetailDTO toDetailDTO(HomestayDTO dto) {
        if (dto == null) {
            return null;
        }

        return HomestayDetailDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .type(dto.getType())
                .propertyTypeName(dto.getPropertyTypeName())
                .price(dto.getPrice())
                .status(dto.getStatus())
                .maxGuests(dto.getMaxGuests())
                .minNights(dto.getMinNights())
                .maxNights(dto.getMaxNights())
                .provinceText(dto.getProvinceText())
                .cityText(dto.getCityText())
                .districtText(dto.getDistrictText())
                .addressDetail(dto.getAddressDetail())
                .provinceCode(dto.getProvinceCode())
                .cityCode(dto.getCityCode())
                .districtCode(dto.getDistrictCode())
                .description(dto.getDescription())
                .coverImage(dto.getCoverImage())
                .images(copyList(dto.getImages()))
                .amenities(copyList(dto.getAmenities()))
                .suggestedFeatures(copyList(dto.getSuggestedFeatures()))
                .ownerId(dto.getOwnerId())
                .ownerUsername(dto.getOwnerUsername())
                .ownerName(dto.getOwnerName())
                .ownerAvatar(dto.getOwnerAvatar())
                .ownerRating(dto.getOwnerRating())
                .featured(dto.getFeatured())
                .autoConfirm(dto.getAutoConfirm())
                .checkInTime(dto.getCheckInTime())
                .checkOutTime(dto.getCheckOutTime())
                .cancelPolicyType(dto.getCancelPolicyType())
                .houseRules(dto.getHouseRules())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .distanceKm(dto.getDistanceKm())
                .build();
    }

    private HomestayAdminSummaryDTO toAdminSummaryDTO(HomestayDTO dto) {
        if (dto == null) {
            return null;
        }

        return HomestayAdminSummaryDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .type(dto.getType())
                .propertyTypeName(dto.getPropertyTypeName())
                .price(dto.getPrice())
                .status(dto.getStatus())
                .maxGuests(dto.getMaxGuests())
                .provinceText(dto.getProvinceText())
                .cityText(dto.getCityText())
                .districtText(dto.getDistrictText())
                .addressDetail(dto.getAddressDetail())
                .coverImage(dto.getCoverImage())
                .ownerId(dto.getOwnerId())
                .ownerUsername(dto.getOwnerUsername())
                .ownerName(dto.getOwnerName())
                .featured(dto.getFeatured())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();
    }

    private <T> List<T> copyList(List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private Map<String, String> resolvePropertyTypeNames(Collection<Homestay> homestays) {
        Set<String> typeCodes = homestays.stream()
                .map(Homestay::getType)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (typeCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        return homestayTypeRepository.findByCodeIn(typeCodes).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        HomestayType::getCode,
                        homestayType -> StringUtils.hasText(homestayType.getName())
                                ? homestayType.getName()
                                : homestayType.getCode(),
                        (left, right) -> left,
                        HashMap::new));
    }

    private String resolvePropertyTypeName(String typeCode, Map<String, String> propertyTypeNameMap) {
        if (!StringUtils.hasText(typeCode)) {
            return null;
        }

        String propertyTypeName = propertyTypeNameMap.get(typeCode);
        if (StringUtils.hasText(propertyTypeName)) {
            return propertyTypeName;
        }

        return homestayTypeRepository.findByCode(typeCode)
                .map(HomestayType::getName)
                .filter(StringUtils::hasText)
                .orElse(typeCode);
    }

    private HomestayDTO toDTO(
            Homestay homestay,
            List<String> referringSearchCriteria,
            Map<String, String> propertyTypeNameMap,
            boolean enrichFeatures) {
        if (homestay == null) {
            log.warn("Attempted to assemble null Homestay");
            return null;
        }

        try {
            HomestayDTO dto = homestayMapper.toDTO(homestay);
            if (dto == null) {
                dto = new HomestayDTO();
            }

            applyLegacyDefaults(dto);
            enrichPropertyTypeName(dto, homestay, propertyTypeNameMap);
            normalizeImageUrls(dto, homestay);
            enrichOwnerInfo(dto, homestay);
            enrichGroupInfo(dto, homestay);
            enrichAmenities(dto, homestay);
            if (enrichFeatures) {
                enrichSuggestedFeatures(dto, homestay, referringSearchCriteria);
            }

            return dto;
        } catch (Exception exception) {
            log.error("Failed to assemble homestay {} to DTO", homestay.getId(), exception);
            return null;
        }
    }

    private void applyLegacyDefaults(HomestayDTO dto) {
        if (dto.getPrice() == null) {
            dto.setPrice("0");
        }
        if (dto.getStatus() == null) {
            dto.setStatus("UNKNOWN");
        }
        if (dto.getMaxGuests() == null) {
            dto.setMaxGuests(1);
        }
        if (dto.getMinNights() == null) {
            dto.setMinNights(1);
        }
        if (dto.getFeatured() == null) {
            dto.setFeatured(false);
        }
        if (dto.getAutoConfirm() == null) {
            dto.setAutoConfirm(false);
        }
        if (dto.getImages() == null) {
            dto.setImages(new ArrayList<>());
        }
        if (dto.getAmenities() == null) {
            dto.setAmenities(new ArrayList<>());
        }
        if (dto.getSuggestedFeatures() == null) {
            dto.setSuggestedFeatures(new ArrayList<>());
        }
    }

    private void enrichPropertyTypeName(
            HomestayDTO dto,
            Homestay homestay,
            Map<String, String> propertyTypeNameMap) {
        try {
            if (StringUtils.hasText(homestay.getType())) {
                String propertyTypeName = resolvePropertyTypeName(homestay.getType(), propertyTypeNameMap);
                dto.setPropertyTypeName(StringUtils.hasText(propertyTypeName) ? propertyTypeName : homestay.getType());
            } else {
                dto.setPropertyTypeName("未知类型");
            }
        } catch (Exception exception) {
            log.error("Failed to resolve property type name for homestay {}", homestay.getId(), exception);
            dto.setPropertyTypeName("未知类型");
        }
    }

    private void normalizeImageUrls(HomestayDTO dto, Homestay homestay) {
        try {
            if (imageUrlUtil != null) {
                dto.setCoverImage(imageUrlUtil.ensureCompleteImageUrl(homestay.getCoverImage()));
                if (homestay.getImages() != null) {
                    dto.setImages(imageUrlUtil.ensureCompleteImageUrls(homestay.getImages()));
                }
            } else {
                dto.setCoverImage(homestay.getCoverImage());
                dto.setImages(homestay.getImages());
            }
        } catch (Exception exception) {
            log.error("Failed to normalize image urls for homestay {}", homestay.getId(), exception);
            dto.setCoverImage(homestay.getCoverImage());
            dto.setImages(homestay.getImages());
        }
    }

    private void enrichOwnerInfo(HomestayDTO dto, Homestay homestay) {
        try {
            if (homestay.getOwner() == null) {
                return;
            }

            dto.setOwnerId(homestay.getOwner().getId());
            dto.setOwnerUsername(homestay.getOwner().getUsername());

            String ownerName = homestay.getOwner().getFullName();
            if (!StringUtils.hasText(ownerName)) {
                ownerName = homestay.getOwner().getNickname();
            }
            if (!StringUtils.hasText(ownerName)) {
                ownerName = homestay.getOwner().getUsername();
            }
            dto.setOwnerName(ownerName);

            if (imageUrlUtil != null) {
                dto.setOwnerAvatar(imageUrlUtil.ensureCompleteImageUrl(homestay.getOwner().getAvatar()));
            } else {
                dto.setOwnerAvatar(homestay.getOwner().getAvatar());
            }
        } catch (Exception exception) {
            log.error("Failed to map owner information for homestay {}", homestay.getId(), exception);
        }
    }

    private void enrichGroupInfo(HomestayDTO dto, Homestay homestay) {
        try {
            if (homestay.getGroup() != null) {
                dto.setGroupId(homestay.getGroup().getId());
                dto.setGroupName(homestay.getGroup().getName());
                dto.setGroupCode(homestay.getGroup().getCode());
                dto.setGroupColor(homestay.getGroup().getColor());
            }
        } catch (Exception exception) {
            log.error("Failed to map group information for homestay {}", homestay.getId(), exception);
        }
    }

    private void enrichAmenities(HomestayDTO dto, Homestay homestay) {
        try {
            if (homestay.getAmenities() != null && amenityService != null) {
                dto.setAmenities(homestay.getAmenities().stream()
                        .filter(Objects::nonNull)
                        .map(amenity -> {
                            try {
                                return amenityService.convertToDTO(amenity);
                            } catch (Exception exception) {
                                log.error("Failed to map amenity {} for homestay {}", amenity.getValue(), homestay.getId(), exception);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
            } else {
                dto.setAmenities(new ArrayList<>());
            }
        } catch (Exception exception) {
            log.error("Failed to map amenities for homestay {}", homestay.getId(), exception);
            dto.setAmenities(new ArrayList<>());
        }
    }

    private void enrichSuggestedFeatures(
            HomestayDTO dto,
            Homestay homestay,
            List<String> referringSearchCriteria) {
        try {
            if (homestayFeatureAnalysisService != null) {
                dto.setSuggestedFeatures(
                        homestayFeatureAnalysisService.analyzeFeatures(homestay, referringSearchCriteria));
            } else {
                dto.setSuggestedFeatures(new ArrayList<>());
            }
        } catch (Exception exception) {
            log.error("Failed to analyze features for homestay {}", homestay.getId(), exception);
            dto.setSuggestedFeatures(new ArrayList<>());
        }
    }
}
