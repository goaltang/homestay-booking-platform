package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HomestayRepository extends JpaRepository<Homestay, Long>, JpaSpecificationExecutor<Homestay> {
    
    /**
     * 根据ID查找房源并加悲观锁（防止并发预订）
     * @param id 房源ID
     * @return 房源
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Homestay h WHERE h.id = :id")
    Optional<Homestay> findByIdWithLock(@Param("id") Long id);
    
    /**
     * 根据ID查找房源并加载设施
     * @param id 房源ID
     * @return 房源（带设施）
     */
    @Query("SELECT h FROM Homestay h LEFT JOIN FETCH h.amenities WHERE h.id = :id")
    Optional<Homestay> findByIdWithAmenities(@Param("id") Long id);
    
    /**
     * 根据ID查找房源并加载房东和设施（解决N+1问题）
     * @param id 房源ID
     * @return 房源（带房东和设施）
     */
    @Query("SELECT DISTINCT h FROM Homestay h LEFT JOIN FETCH h.owner LEFT JOIN FETCH h.amenities WHERE h.id = :id")
    Optional<Homestay> findByIdWithDetails(@Param("id") Long id);
    
    /**
     * 根据状态查找房源并预加载房东和设施（解决N+1问题）
     * @param status 状态
     * @return 房源列表
     */
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT h FROM Homestay h WHERE h.status = :status")
    List<Homestay> findByStatusWithDetails(@Param("status") HomestayStatus status);
    
    /**
     * 根据状态和推荐状态查找房源并预加载房东和设施
     */
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT h FROM Homestay h WHERE h.status = :status AND h.featured = true")
    List<Homestay> findByStatusAndFeaturedTrueWithDetails(@Param("status") HomestayStatus status);
    
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT h FROM Homestay h WHERE h.status = :status AND h.featured = false")
    List<Homestay> findByStatusAndFeaturedFalseWithDetails(@Param("status") HomestayStatus status);
    
    /**
     * 根据类型和状态查找房源并预加载房东和设施
     */
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT h FROM Homestay h WHERE h.type = :type AND h.status = :status")
    List<Homestay> findByTypeAndStatusWithDetails(@Param("type") String type, @Param("status") HomestayStatus status);
    
    /**
     * 根据房东用户名查找房源并预加载房东和设施
     */
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT h FROM Homestay h WHERE h.owner.username = :username")
    List<Homestay> findByOwnerUsernameWithDetails(@Param("username") String username);
    
    /**
     * 根据状态查找房源
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByStatus(HomestayStatus status);

    /**
     * 按 ID 批量查询并预加载房东/设施/图片（解决 ES 搜索结果组装 DTO 的 N+1）
     */
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    List<Homestay> findAllByIdIn(Collection<Long> ids);
    
    /**
     * 根据状态查找房源并按创建时间倒序排列
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByStatusOrderByCreatedAtDesc(HomestayStatus status);

    /**
     * 根据状态查找前500条房源并按创建时间倒序排列（用于热门/推荐分页，避免全量扫描）
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findTop500ByStatusOrderByCreatedAtDesc(HomestayStatus status);
    
    /**
     * 根据省份、城市和状态查找房源
     * @param provinceCode 省份代码
     * @param cityCode 城市代码  
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByProvinceCodeAndCityCodeAndStatus(String provinceCode, String cityCode, HomestayStatus status);
    
    /**
     * 根据状态和是否推荐查找房源
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByStatusAndFeaturedTrue(HomestayStatus status);
    
    /**
     * 根据状态和是否推荐查找房源
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByStatusAndFeaturedFalse(HomestayStatus status);
    
    /**
     * 根据类型和状态查找房源
     * @param type 类型
     * @param status 状态
     * @return 房源列表
     */
    List<Homestay> findByTypeAndStatus(String type, HomestayStatus status);
    
    /**
     * 查找推荐房源
     * @return 房源列表
     */
    List<Homestay> findByFeaturedTrue();
    
    /**
     * 根据类型查找房源
     * @param type 类型
     * @return 房源列表
     */
    List<Homestay> findByType(String type);
    
    /**
     * 根据价格范围查找房源
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @return 房源列表
     */
    @Query("SELECT h FROM Homestay h WHERE h.price BETWEEN :minPrice AND :maxPrice AND h.status = 'ACTIVE'")
    List<Homestay> findByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);
    
    /**
     * 根据容纳人数查找房源
     * @param guestCount 容纳人数
     * @return 房源列表
     */
    @Query("SELECT h FROM Homestay h WHERE h.maxGuests >= :guestCount AND h.status = 'ACTIVE'")
    List<Homestay> findByGuestCapacity(@Param("guestCount") Integer guestCount);
    
    /**
     * 高级搜索房源
     * @param location 位置（城市或省份）
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param guestCount 容纳人数
     * @param type 类型
     * @return 房源列表
     */
    @Query("SELECT h FROM Homestay h WHERE " +
           "(:location IS NULL OR h.provinceText LIKE %:location% OR h.cityText LIKE %:location% OR h.districtText LIKE %:location% OR h.addressDetail LIKE %:location%) AND " +
           "(:minPrice IS NULL OR h.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR h.price <= :maxPrice) AND " +
           "(:guestCount IS NULL OR h.maxGuests >= :guestCount) AND " +
           "(:type IS NULL OR h.type = :type) AND " +
           "h.status = 'ACTIVE'")
    @EntityGraph(value = "Homestay.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    List<Homestay> searchHomestays(
            @Param("location") String location,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("guestCount") Integer guestCount,
            @Param("type") String type);
    
    /**
     * 根据所有者查找房源
     * @param owner 所有者
     * @return 房源列表
     */
    List<Homestay> findByOwner(User owner);
    
    /**
     * 根据标题模糊查询房源
     * @param title 标题
     * @param pageable 分页
     * @return 房源分页
     */
    Page<Homestay> findByTitleContaining(String title, Pageable pageable);
    
    /**
     * 根据标题和状态模糊查询房源
     * @param title 标题
     * @param status 状态
     * @param pageable 分页
     * @return 房源分页
     */
    Page<Homestay> findByTitleContainingAndStatus(String title, String status, Pageable pageable);
    
    /**
     * 根据标题和类型模糊查询房源
     * @param title 标题
     * @param type 类型
     * @param pageable 分页
     * @return 房源分页
     */
    Page<Homestay> findByTitleContainingAndType(String title, String type, Pageable pageable);
    
    /**
     * 根据标题、状态和类型模糊查询房源
     * @param title 标题
     * @param status 状态
     * @param type 类型
     * @param pageable 分页
     * @return 房源分页
     */
    Page<Homestay> findByTitleContainingAndStatusAndType(String title, String status, String type, Pageable pageable);
    
    // 统计房东的房源数量
    @Query("SELECT COUNT(h) FROM Homestay h WHERE h.owner.id = :ownerId")
    Long countByOwnerId(@Param("ownerId") Long ownerId);
    
    // 添加统计房东房源数量的方法
    @Query("SELECT COUNT(h) FROM Homestay h WHERE h.owner.id = :hostId")
    Long countByHostId(@Param("hostId") Long hostId);
    
    // 获取房东的所有房源
    @Query("SELECT h FROM Homestay h WHERE h.owner.id = :ownerId")
    Page<Homestay> findByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);
    
    List<Homestay> findByOwnerId(Long ownerId);
    
    @Query("SELECT h FROM Homestay h WHERE h.owner.username = :username")
    List<Homestay> findByOwnerUsername(@Param("username") String username);
    
    @Query("SELECT h FROM Homestay h WHERE h.owner.username = :username AND h.status = :status")
    List<Homestay> findByOwnerUsernameAndStatus(@Param("username") String username, @Param("status") String status);
    
    @Query("SELECT COUNT(h) FROM Homestay h WHERE h.owner.username = :username")
    Long countByOwnerUsername(@Param("username") String username);
    
    @Query("SELECT COUNT(h) FROM Homestay h WHERE h.owner.username = :username AND h.status = :status")
    Long countByOwnerUsernameAndStatus(@Param("username") String username, @Param("status") String status);
    
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT h.provinceCode, COUNT(h) FROM Homestay h GROUP BY h.provinceCode")
    List<Object[]> countByProvince();

    /**
     * 计算指定位置和类型的房源的平均价格，排除特定房源ID。
     * @param cityCode 城市代码 (必须)
     * @param districtCode 区域代码 (可选, 如果为null则不区区域)
     * @param propertyType 房源类型 (必须)
     * @param excludeHomestayId 要排除的房源ID (避免自身比较)
     * @param status 房源状态 (例如 "PUBLISHED" 或 "ACTIVE")
     * @return 平均价格，如果没有符合条件的房源则返回null
     */
    @Query("SELECT AVG(h.price) FROM Homestay h " +
           "WHERE h.cityCode = :cityCode " +
           "AND (:districtCode IS NULL OR h.districtCode = :districtCode) " +
           "AND h.type = :propertyType " +
           "AND h.id != :excludeHomestayId " +
           "AND h.status = :status " +
           "AND h.price IS NOT NULL AND h.price > 0")
    Double findAveragePriceForComparableHomestays(
            @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("propertyType") String propertyType,
            @Param("excludeHomestayId") Long excludeHomestayId,
            @Param("status") HomestayStatus status
    );

    /**
     * 获取详细的价格统计数据 - 多维度匹配
     * @param cityCode 城市代码
     * @param districtCode 区域代码 (可选)
     * @param propertyType 房源类型
     * @param minGuests 最少容纳人数
     * @param maxGuests 最多容纳人数
     * @param excludeHomestayId 排除的房源ID
     * @param status 房源状态
     * @return 价格列表用于统计分析
     */
    @Query("SELECT h.price FROM Homestay h " +
           "WHERE h.cityCode = :cityCode " +
           "AND (:districtCode IS NULL OR h.districtCode = :districtCode) " +
           "AND (:propertyType IS NULL OR h.type = :propertyType) " +
           "AND h.maxGuests >= :minGuests " +
           "AND h.maxGuests <= :maxGuests " +
           "AND h.id != :excludeHomestayId " +
           "AND h.status = :status " +
           "AND h.price IS NOT NULL AND h.price > 0 " +
           "ORDER BY h.price")
    List<BigDecimal> findPricesForDetailedComparison(
            @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("propertyType") String propertyType,
            @Param("minGuests") Integer minGuests,
            @Param("maxGuests") Integer maxGuests,
            @Param("excludeHomestayId") Long excludeHomestayId,
            @Param("status") HomestayStatus status
    );

    /**
     * 获取相似房源的价格统计 - 包含设施权重
     * @param cityCode 城市代码
     * @param districtCode 区域代码
     * @param propertyType 房源类型
     * @param maxGuests 容纳人数
     * @param excludeHomestayId 排除的房源ID
     * @param status 房源状态
     * @return 包含设施数量的价格数据
     */
    @Query("SELECT h.price, SIZE(h.amenities) as amenityCount FROM Homestay h " +
           "WHERE h.cityCode = :cityCode " +
           "AND (:districtCode IS NULL OR h.districtCode = :districtCode) " +
           "AND h.type = :propertyType " +
           "AND ABS(h.maxGuests - :maxGuests) <= 2 " +
           "AND h.id != :excludeHomestayId " +
           "AND h.status = :status " +
           "AND h.price IS NOT NULL AND h.price > 0")
    List<Object[]> findPricesWithAmenityCount(
            @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("propertyType") String propertyType,
            @Param("maxGuests") Integer maxGuests,
            @Param("excludeHomestayId") Long excludeHomestayId,
            @Param("status") HomestayStatus status
    );

    /**
     * 获取特定月份的价格趋势 (如果有时间相关的价格数据)
     * @param cityCode 城市代码
     * @param propertyType 房源类型
     * @param month 月份
     * @param status 房源状态
     * @return 特定月份的平均价格
     */
    @Query("SELECT AVG(h.price) FROM Homestay h " +
           "WHERE h.cityCode = :cityCode " +
           "AND h.type = :propertyType " +
           "AND h.status = :status " +
           "AND h.price IS NOT NULL AND h.price > 0")
    Double findAveragePriceByMonth(
            @Param("cityCode") String cityCode,
            @Param("propertyType") String propertyType,
            @Param("status") HomestayStatus status
    );

    /**
     * 统计低于指定价格的房源数量百分比
     * @param cityCode 城市代码
     * @param districtCode 区域代码
     * @param propertyType 房源类型
     * @param targetPrice 目标价格
     * @param excludeHomestayId 排除的房源ID
     * @param status 房源状态
     * @return 低于目标价格的房源数量
     */
    @Query("SELECT COUNT(h) FROM Homestay h " +
           "WHERE h.cityCode = :cityCode " +
           "AND (:districtCode IS NULL OR h.districtCode = :districtCode) " +
           "AND h.type = :propertyType " +
           "AND h.price < :targetPrice " +
           "AND h.id != :excludeHomestayId " +
           "AND h.status = :status " +
           "AND h.price IS NOT NULL AND h.price > 0")
    Long countHomestaysBelowPrice(
            @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("propertyType") String propertyType,
            @Param("targetPrice") BigDecimal targetPrice,
            @Param("excludeHomestayId") Long excludeHomestayId,
            @Param("status") HomestayStatus status
    );

    List<Homestay> findByOwnerIdAndGroupId(Long ownerId, Long groupId);

    @Query("SELECT COUNT(h) FROM Homestay h WHERE h.group.id = :groupId")
    Long countByGroupId(@Param("groupId") Long groupId);

    /**
     * 查找缺少经纬度坐标的房源
     */
    List<Homestay> findByLatitudeIsNullOrLongitudeIsNull();

    @Query("SELECT h FROM Homestay h WHERE h.group.id = :groupId AND h.owner.id = :ownerId")
    Page<Homestay> findByGroupIdAndOwnerId(@Param("groupId") Long groupId, @Param("ownerId") Long ownerId, Pageable pageable);

    /**
     * 统计指定状态的房源数量
     */
    Long countByStatus(HomestayStatus status);

    /**
     * 统计指定状态下覆盖的不同城市数量
     */
    @Query("SELECT COUNT(DISTINCT h.cityText) FROM Homestay h WHERE h.status = :status AND h.cityText IS NOT NULL AND h.cityText <> ''")
    Long countDistinctCityTextByStatus(@Param("status") HomestayStatus status);
} 
