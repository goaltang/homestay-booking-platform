package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.HomestayDetailDTO;
import com.homestay3.homestaybackend.dto.HomestaySummaryDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.HomestayAvailabilityService;
import com.homestay3.homestaybackend.service.HomestayQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomestayQueryServiceImpl implements HomestayQueryService {

    private final HomestayRepository homestayRepository;
    private final UserRepository userRepository;
    private final HomestayAvailabilityService availabilityService;
    private final HomestayDtoAssembler homestayDtoAssembler;
    private final HomestayMutationSupport homestayMutationSupport;
    private final HomestaySpecificationSupport homestaySpecificationSupport;

    @Override
    @Cacheable(value = "homestayList", key = "'all-active'")
    public List<HomestayDTO> getAllHomestays() {
        try {
            List<Homestay> homestays = homestayRepository.findByStatusWithDetails(HomestayStatus.ACTIVE);
            if (homestays.isEmpty()) {
                return new ArrayList<>();
            }
            return homestayDtoAssembler.toDTOs(homestays, null, false);
        } catch (Exception exception) {
            log.error("Failed to fetch all homestays", exception);
            return new ArrayList<>();
        }
    }

    @Override
    @Cacheable(value = "homestayList", key = "'all-active-summary'")
    public List<HomestaySummaryDTO> getAllHomestaySummaries() {
        try {
            List<Homestay> homestays = homestayRepository.findByStatusWithDetails(HomestayStatus.ACTIVE);
            if (homestays.isEmpty()) {
                return new ArrayList<>();
            }
            return homestayDtoAssembler.toSummaryDTOs(homestays, null);
        } catch (Exception exception) {
            log.error("Failed to fetch all homestay summaries", exception);
            return new ArrayList<>();
        }
    }

    @Override
    @Deprecated
    public List<HomestayDTO> getFeaturedHomestays() {
        List<Homestay> featuredHomestays =
                homestayRepository.findByStatusAndFeaturedTrueWithDetails(HomestayStatus.ACTIVE);

        List<Homestay> finalHomestayList = new ArrayList<>();
        if (featuredHomestays.size() < 6) {
            List<Homestay> regularHomestays =
                    homestayRepository.findByStatusAndFeaturedFalseWithDetails(HomestayStatus.ACTIVE);
            int remaining = 6 - featuredHomestays.size();
            if (regularHomestays.size() > remaining) {
                regularHomestays = regularHomestays.subList(0, remaining);
            }
            finalHomestayList.addAll(featuredHomestays);
            finalHomestayList.addAll(regularHomestays);
        } else if (featuredHomestays.size() > 6) {
            finalHomestayList.addAll(featuredHomestays.subList(0, 6));
        } else {
            finalHomestayList.addAll(featuredHomestays);
        }

        return homestayDtoAssembler.toDTOs(finalHomestayList, null, false);
    }

    @Override
    @Cacheable(value = "homestayDetails", key = "#id")
    public HomestayDTO getHomestayById(Long id, List<String> referringSearchCriteria) {
        Homestay homestay = homestayRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homestay not found with id: " + id));
        return homestayDtoAssembler.toDTO(homestay, referringSearchCriteria);
    }

    @Override
    @Cacheable(value = "homestayDetails", key = "#id + ':detail'")
    public HomestayDetailDTO getHomestayDetailById(Long id, List<String> referringSearchCriteria) {
        Homestay homestay = homestayRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homestay not found with id: " + id));
        return homestayDtoAssembler.toDetailDTO(homestay, referringSearchCriteria);
    }

    @Override
    public List<HomestayDTO> getHomestaysByPropertyType(String propertyType) {
        List<Homestay> homestays =
                homestayRepository.findByTypeAndStatusWithDetails(propertyType, HomestayStatus.ACTIVE);
        return homestayDtoAssembler.toDTOs(homestays, null, false);
    }

    @Override
    public List<HomestaySummaryDTO> getHomestaySummariesByPropertyType(String propertyType) {
        List<Homestay> homestays =
                homestayRepository.findByTypeAndStatusWithDetails(propertyType, HomestayStatus.ACTIVE);
        return homestayDtoAssembler.toSummaryDTOs(homestays, null);
    }

    @Override
    public Page<HomestayDTO> getHomestaysByPage(Pageable pageable) {
        Page<Homestay> homestaysPage = homestayRepository.findAll(
                homestaySpecificationSupport.withDetailFetch(null),
                pageable);
        return homestayDtoAssembler.toDTOPage(homestaysPage, null, false);
    }

    @Override
    public Page<HomestaySummaryDTO> getHomestaySummaryPage(Pageable pageable) {
        Page<Homestay> homestaysPage = homestayRepository.findAll(
                homestaySpecificationSupport.withDetailFetch(null),
                pageable);
        return homestayDtoAssembler.toSummaryDTOPage(homestaysPage, null);
    }

    @Override
    public List<HomestayDTO> getHomestaysByOwner(String username) {
        try {
            User owner = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
            log.debug("Found owner {} for username {}", owner.getId(), username);

            List<Homestay> homestays = homestayRepository.findByOwnerUsernameWithDetails(username);
            if (homestays == null || homestays.isEmpty()) {
                return new ArrayList<>();
            }

            return homestayDtoAssembler.toDTOs(homestays, null, false);
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch homestays by owner {}", username, exception);
            throw new RuntimeException("获取房源列表失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public List<HomestaySummaryDTO> getHomestaySummariesByOwner(String username) {
        try {
            User owner = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
            log.debug("Found owner {} for username {}", owner.getId(), username);

            List<Homestay> homestays = homestayRepository.findByOwnerUsernameWithDetails(username);
            if (homestays == null || homestays.isEmpty()) {
                return new ArrayList<>();
            }

            return homestayDtoAssembler.toSummaryDTOs(homestays, null);
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch homestay summaries by owner {}", username, exception);
            throw new RuntimeException("获取房源列表失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HomestaySummaryDTO> getHomestaySummaryPageByOwner(String username, String status, String type, Long groupId, Pageable pageable) {
        try {
            User owner = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
            log.debug("Found owner {} for username {}", owner.getId(), username);

            Specification<Homestay> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("owner").get("username"), username));

                if (StringUtils.hasText(status)) {
                    try {
                        predicates.add(cb.equal(root.get("status"), HomestayStatus.valueOf(status.toUpperCase())));
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status filter: {}, ignoring", status);
                    }
                }
                if (StringUtils.hasText(type)) {
                    predicates.add(cb.equal(root.get("type"), type));
                }
                if (groupId != null && groupId > 0) {
                    predicates.add(cb.equal(root.get("group").get("id"), groupId));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<Homestay> page = homestayRepository.findAll(
                    homestaySpecificationSupport.withOwnerAndGroupFetch(spec), pageable);
            return homestayDtoAssembler.toSummaryDTOPage(page, null);
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch homestay summary page by owner {}", username, exception);
            throw new RuntimeException("获取房源列表失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean checkHomestayReadyForReview(Long homestayId) {
        Homestay homestay = homestayRepository.findById(homestayId)
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在，ID: " + homestayId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            String currentUsername = authentication.getName();
            boolean isAdmin = homestayMutationSupport.isAdminUser();
            boolean isOwner = homestay.getOwner() != null
                    && homestay.getOwner().getUsername().equals(currentUsername);

            if (!isAdmin && !isOwner) {
                throw new AccessDeniedException("您没有权限查看此房源的审核状态");
            }
        }

        return homestayMutationSupport.isHomestayReadyForReview(homestay);
    }

    @Override
    public String getHomestayReviewReadinessDetails(Long homestayId) {
        Homestay homestay = homestayRepository.findById(homestayId)
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在，ID: " + homestayId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            String currentUsername = authentication.getName();
            boolean isAdmin = homestayMutationSupport.isAdminUser();
            boolean isOwner = homestay.getOwner() != null
                    && homestay.getOwner().getUsername().equals(currentUsername);

            if (!isAdmin && !isOwner) {
                throw new AccessDeniedException("您没有权限查看此房源的审核状态");
            }
        }

        return homestayMutationSupport.getHomestayReviewReadinessDetails(homestay);
    }

    @Override
    public boolean isHomestayOwner(Long homestayId, String username) {
        try {
            return homestayRepository.findById(homestayId)
                    .map(h -> h.getOwner() != null && username.equals(h.getOwner().getUsername()))
                    .orElse(false);
        } catch (Exception exception) {
            log.error("Failed to check ownership for homestay {} and user {}", homestayId, username, exception);
            return false;
        }
    }

    @Override
    @Cacheable(value = "homestayList", key = "'active-summary-page:' + #featured + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<HomestaySummaryDTO> getActiveHomestaySummaryPage(Boolean featured, Pageable pageable) {
        try {
            Specification<Homestay> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("status"), HomestayStatus.ACTIVE));
                if (featured != null) {
                    predicates.add(cb.equal(root.get("featured"), featured));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<Homestay> homestayPage = homestayRepository.findAll(
                    homestaySpecificationSupport.withOwnerFetch(spec), pageable);
            return homestayDtoAssembler.toSummaryDTOPage(homestayPage, null);
        } catch (Exception exception) {
            log.error("Failed to fetch active homestay summary page", exception);
            throw new RuntimeException("获取房源列表失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public List<LocalDate> getUnavailableDates(Long homestayId) {
        try {
            if (!homestayRepository.existsById(homestayId)) {
                return new ArrayList<>();
            }

            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusYears(1);
            return availabilityService.getBookedDates(homestayId, today, endDate);
        } catch (Exception exception) {
            log.error("Failed to fetch unavailable dates for homestay {}", homestayId, exception);
            return new ArrayList<>();
        }
    }
}
