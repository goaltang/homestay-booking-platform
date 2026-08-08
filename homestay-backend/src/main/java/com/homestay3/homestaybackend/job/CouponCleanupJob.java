package com.homestay3.homestaybackend.job;

import com.homestay3.homestaybackend.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 优惠券定时清理任务
 * 被动清理（查询时触发）无法覆盖不活跃用户，因此需要独立定时任务兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponCleanupJob {

    private final UserCouponRepository userCouponRepository;

    /**
     * 每小时执行一次：批量将已过期的 AVAILABLE 券标记为 EXPIRED
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void expireOutdatedCoupons() {
        log.info("[定时任务] 开始清理过期优惠券...");
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = userCouponRepository.expireOutdatedCoupons(now);
            log.info("[定时任务] 过期优惠券清理完成，共标记 {} 张券为 EXPIRED", updated);
        } catch (Exception e) {
            log.error("[定时任务] 过期优惠券清理失败", e);
        }
    }
}
