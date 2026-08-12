package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.BannerDTO;
import com.homestay3.homestaybackend.dto.HomeStatsDTO;
import com.homestay3.homestaybackend.entity.Banner;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.BannerRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final HomestayRepository homestayRepository;
    private final ReviewRepository reviewRepository;
    private final BannerRepository bannerRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final @Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor;

    @Cacheable("homeStats")
    public HomeStatsDTO getStats() {
        // 近30天订单数查询的时间范围
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime now = LocalDateTime.now();

        // 5 个相互独立的只读 count 查询并行执行
        CompletableFuture<Long> homestayCountF = CompletableFuture.supplyAsync(() -> homestayRepository.countByStatus(HomestayStatus.ACTIVE), taskExecutor);
        CompletableFuture<Long> cityCountF = CompletableFuture.supplyAsync(() -> homestayRepository.countDistinctCityTextByStatus(HomestayStatus.ACTIVE), taskExecutor);
        CompletableFuture<Long> totalReviewsF = CompletableFuture.supplyAsync(() -> reviewRepository.countPublicReviews(), taskExecutor);
        CompletableFuture<Long> positiveReviewsF = CompletableFuture.supplyAsync(() -> reviewRepository.countPublicPositiveReviews(4), taskExecutor);
        CompletableFuture<Long> thirtyDaysOrdersF = CompletableFuture.supplyAsync(() -> orderRepository.countByCreatedAtBetween(thirtyDaysAgo, now), taskExecutor);

        // 汇合：任一子任务失败会以 CompletionException 包装抛出，与串行抛异常语义一致
        CompletableFuture.allOf(homestayCountF, cityCountF, totalReviewsF, positiveReviewsF, thirtyDaysOrdersF).join();

        Long homestayCount = homestayCountF.join();
        Long cityCount = cityCountF.join();
        Long totalReviews = totalReviewsF.join();
        Long positiveReviews = positiveReviewsF.join();
        Long recentOrders = thirtyDaysOrdersF.join();

        Double positiveRate = 0.0;
        if (totalReviews != null && totalReviews > 0 && positiveReviews != null) {
            positiveRate = Math.round((positiveReviews.doubleValue() / totalReviews.doubleValue()) * 1000) / 10.0;
        }

        // 今日可预订房源数（简化：统计活跃房源数，实际应考虑房态）—— 与房源总数同口径，复用并行结果
        Long availableToday = homestayCount;

        return HomeStatsDTO.builder()
                .homestayCount(homestayCount != null ? homestayCount : 0L)
                .cityCount(cityCount != null ? cityCount : 0L)
                .positiveRate(positiveRate)
                .recentOrders(recentOrders != null ? recentOrders : 0L)
                .availableToday(availableToday != null ? availableToday : 0L)
                .build();
    }

    @Cacheable("homeBanners")
    public List<BannerDTO> getActiveBanners() {
        return bannerRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toBannerDTO)
                .collect(Collectors.toList());
    }

    // ========== Banner 管理接口 ==========

    public List<BannerDTO> getAllBanners() {
        return bannerRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"))
                .stream()
                .map(this::toBannerDTO)
                .collect(Collectors.toList());
    }

    public Page<BannerDTO> getBannersPage(String keyword, Pageable pageable) {
        Page<Banner> page;
        if (keyword != null && !keyword.trim().isEmpty()) {
            page = bannerRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            page = bannerRepository.findAll(pageable);
        }
        List<BannerDTO> content = page.getContent().stream()
                .map(this::toBannerDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    public BannerDTO getBannerById(Long id) {
        return bannerRepository.findById(id)
                .map(this::toBannerDTO)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = "homeBanners", allEntries = true)
    public BannerDTO createBanner(Banner banner) {
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        if (banner.getEnabled() == null) {
            banner.setEnabled(true);
        }
        Banner saved = bannerRepository.save(banner);
        return toBannerDTO(saved);
    }

    @Transactional
    @CacheEvict(value = "homeBanners", allEntries = true)
    public BannerDTO updateBanner(Long id, Banner update) {
        Banner existing = bannerRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        if (update.getTitle() != null) existing.setTitle(update.getTitle());
        if (update.getSubtitle() != null) existing.setSubtitle(update.getSubtitle());
        if (update.getImageUrl() != null) existing.setImageUrl(update.getImageUrl());
        if (update.getLinkUrl() != null) existing.setLinkUrl(update.getLinkUrl());
        if (update.getBgGradient() != null) existing.setBgGradient(update.getBgGradient());
        if (update.getSortOrder() != null) existing.setSortOrder(update.getSortOrder());
        if (update.getEnabled() != null) existing.setEnabled(update.getEnabled());
        Banner saved = bannerRepository.save(existing);
        return toBannerDTO(saved);
    }

    @Transactional
    @CacheEvict(value = "homeBanners", allEntries = true)
    public void deleteBanner(Long id) {
        bannerRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(value = "homeBanners", allEntries = true)
    public BannerDTO toggleBannerEnabled(Long id) {
        Banner existing = bannerRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        existing.setEnabled(!Boolean.TRUE.equals(existing.getEnabled()));
        Banner saved = bannerRepository.save(existing);
        return toBannerDTO(saved);
    }

    private BannerDTO toBannerDTO(Banner banner) {
        if (banner == null) {
            return null;
        }
        return BannerDTO.builder()
                .id(banner.getId())
                .title(banner.getTitle())
                .subtitle(banner.getSubtitle())
                .imageUrl(banner.getImageUrl())
                .linkUrl(banner.getLinkUrl())
                .bgGradient(banner.getBgGradient())
                .sortOrder(banner.getSortOrder())
                .enabled(banner.getEnabled())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
