package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户
     */
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("update User u set u.lastLogin = :lastLogin, u.updatedAt = :lastLogin where u.id = :id")
    int updateLastLogin(@Param("id") Long id, @Param("lastLogin") LocalDateTime lastLogin);
    
    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
    
    /**
     * 检查用户名是否存在
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 检查邮箱是否存在
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 根据重置令牌查找用户
     * @param resetToken 重置令牌
     * @return 用户
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByResetToken(String resetToken);
    
    // 根据角色查询用户
    List<User> findByRole(String role);
    
    Long countByCreatedAtBefore(LocalDateTime dateTime);
    
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 根据验证状态查询用户
     * @param status 验证状态
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    /**
     * 查询所有有验证状态的用户
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByVerificationStatusNotNull(Pageable pageable);

    /**
     * 根据用户名和验证状态查询用户
     * @param username 用户名（模糊查询）
     * @param status 验证状态
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByUsernameContainingAndVerificationStatus(String username, VerificationStatus status, Pageable pageable);

    /**
     * 根据用户名查询所有有验证状态的用户
     * @param username 用户名（模糊查询）
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByUsernameContainingAndVerificationStatusNotNull(String username, Pageable pageable);

    /**
     * 查询所有上传过身份证照片或有验证状态的用户
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByIdCardFrontNotNullOrIdCardBackNotNullOrVerificationStatusNotNull(Pageable pageable);
} 