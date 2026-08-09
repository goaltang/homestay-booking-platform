package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.CouponBatchIssueTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CouponBatchIssueTaskRepository extends JpaRepository<CouponBatchIssueTask, Long> {

    Page<CouponBatchIssueTask> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<CouponBatchIssueTask> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);
}
