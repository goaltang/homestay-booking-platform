package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.HolidayCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

    Optional<HolidayCalendar> findByDateAndRegionCode(LocalDate date, String regionCode);

    List<HolidayCalendar> findByDateBetweenAndRegionCodeAndIsHolidayTrue(LocalDate start, LocalDate end, String regionCode);

    List<HolidayCalendar> findByDateBetweenAndRegionCode(LocalDate start, LocalDate end, String regionCode);

    boolean existsByDateAndRegionCode(LocalDate date, String regionCode);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(h.date) FROM HolidayCalendar h WHERE h.regionCode = :regionCode AND h.isHoliday = true")
    Optional<LocalDate> findMaxHolidayDateByRegionCode(@org.springframework.data.repository.query.Param("regionCode") String regionCode);
}
