package com.homestay3.homestaybackend.job;

import com.homestay3.homestaybackend.repository.HolidayCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 节假日数据覆盖监控。
 * 每周检查一次，如果未来 12 个月内无节假日数据则告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayDataCoverageMonitor {

    private final HolidayCalendarRepository holidayCalendarRepository;

    /**
     * 每周一上午 10 点检查节假日数据覆盖情况
     */
    @Scheduled(cron = "0 0 10 * * MON")
    public void checkCoverage() {
        try {
            LocalDate now = LocalDate.now();
            LocalDate threshold = now.plusMonths(12);

            var maxDate = holidayCalendarRepository.findMaxHolidayDateByRegionCode("CN");
            if (maxDate.isEmpty()) {
                log.warn("[节假日监控] 数据库中无节假日数据！请通过管理后台 > 节假日管理 > 生成预设数据");
                return;
            }

            long remainingDays = ChronoUnit.DAYS.between(now, maxDate.get());
            if (maxDate.get().isBefore(threshold)) {
                log.warn("[节假日监控] 节假日数据即将不足！最新日期: {}, 覆盖仅剩 {} 天。请更新 HolidayPresetService 并重新生成数据",
                        maxDate.get(), remainingDays);
            } else {
                log.info("[节假日监控] 节假日数据覆盖充足，最新日期: {}, 剩余 {} 天", maxDate.get(), remainingDays);
            }
        } catch (Exception e) {
            log.error("[节假日监控] 检查失败", e);
        }
    }
}
