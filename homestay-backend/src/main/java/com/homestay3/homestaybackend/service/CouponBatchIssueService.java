package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.entity.CouponBatchIssueTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface CouponBatchIssueService {

    /**
     * 创建批量发券任务
     */
    CouponBatchIssueTask createBatchTask(Long templateId, String name, String filterType,
                                          Map<String, Object> filterParams, Long createdBy);

    /**
     * 执行批量发券任务（同步，MQ 消费者调用；异常抛给消费者触发重试）
     */
    void executeBatchTask(Long taskId);

    /**
     * 异步执行批量发券任务（MQ 关闭时的降级路径）
     */
    void executeBatchTaskAsync(Long taskId);

    /**
     * 查询任务列表
     */
    Page<CouponBatchIssueTask> getTaskList(String status, Pageable pageable);

    /**
     * 查询任务详情（含明细统计）
     */
    Map<String, Object> getTaskDetail(Long taskId);

    /**
     * 查询任务明细列表
     */
    org.springframework.data.domain.Page<com.homestay3.homestaybackend.entity.CouponBatchIssueItem> getTaskItems(Long taskId, String status, org.springframework.data.domain.Pageable pageable);

    /**
     * 重试失败项
     */
    void retryFailedItems(Long taskId);
}
