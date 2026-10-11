package com.homestay3.homestaybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.HostDTO;
import com.homestay3.homestaybackend.dto.HostStatisticsDTO;
import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.dto.ReviewDTO;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.VerificationStatus;
import com.homestay3.homestaybackend.model.UserRole;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import com.homestay3.homestaybackend.service.FileService;
import com.homestay3.homestaybackend.service.HostService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import com.homestay3.homestaybackend.dto.HomestayOptionDTO;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class HostServiceImpl implements HostService {

    private static final Logger logger = LoggerFactory.getLogger(HostServiceImpl.class);
    private final UserRepository userRepository;
    private final HomestayRepository homestayRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public HostDTO getHostInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HostDTO hostDTO = HostDTO.fromUser(user);
        
        // 解析languages字段
        if (user.getLanguages() != null && !user.getLanguages().isEmpty()) {
            try {
                // 先尝试JSON格式解析
                List<String> languages = objectMapper.readValue(user.getLanguages(), new TypeReference<List<String>>() {});
                hostDTO.setLanguages(languages);
            } catch (JsonProcessingException e) {
                // JSON解析失败，尝试逗号分隔格式
                try {
                    List<String> languages = Arrays.asList(user.getLanguages().split(","));
                    // 去除每个语言项的首尾空格
                    languages = languages.stream()
                            .map(String::trim)
                            .filter(lang -> !lang.isEmpty())
                            .collect(Collectors.toList());
                    hostDTO.setLanguages(languages);
                    logger.debug("使用逗号分隔格式解析语言字段: {}", languages);
                } catch (Exception ex) {
                    logger.error("解析语言字段失败，原始数据: {}", user.getLanguages(), ex);
                    hostDTO.setLanguages(new ArrayList<>());
                }
            }
        } else {
            hostDTO.setLanguages(new ArrayList<>());
        }
        
        // 解析companions字段
        if (user.getCompanions() != null && !user.getCompanions().isEmpty()) {
            try {
                // 先尝试JSON格式解析
                List<Map<String, String>> companions = objectMapper.readValue(user.getCompanions(), 
                        new TypeReference<List<Map<String, String>>>() {});
                hostDTO.setCompanions(companions);
            } catch (JsonProcessingException e) {
                // JSON解析失败，可能是简单字符串格式，设置为空数组
                logger.warn("解析接待伙伴字段失败，原始数据: {}, 将设置为空数组", user.getCompanions());
                hostDTO.setCompanions(new ArrayList<>());
            }
        } else {
            hostDTO.setCompanions(new ArrayList<>());
        }
        
        // 填充统计数据
        HostStatisticsDTO statistics = getHostStatistics(username);
        hostDTO.setHomestayCount(statistics.getHomestayCount());
        hostDTO.setOrderCount(statistics.getOrderCount());
        hostDTO.setReviewCount(statistics.getReviewCount());
        hostDTO.setRating(statistics.getRating());
        
        return hostDTO;
    }

    @Override
    @Transactional
    public HostDTO updateHostInfo(HostDTO hostDTO, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
        
        // 更新基本信息
        if (hostDTO.getNickname() != null) {
            user.setNickname(hostDTO.getNickname());
        }
        if (hostDTO.getEmail() != null) {
            user.setEmail(hostDTO.getEmail());
        }
        if (hostDTO.getPhone() != null) {
            user.setPhone(hostDTO.getPhone());
        }
        if (hostDTO.getRealName() != null) {
            user.setRealName(hostDTO.getRealName());
        }
        if (hostDTO.getIdCard() != null) {
            user.setIdCard(hostDTO.getIdCard());
        }
        
        // 更新房东专属信息
        if (hostDTO.getOccupation() != null) {
            user.setOccupation(hostDTO.getOccupation());
        }
        if (hostDTO.getIntroduction() != null) {
            user.setIntroduction(hostDTO.getIntroduction());
        }
        if (hostDTO.getHostResponseRate() != null) {
            user.setHostResponseRate(hostDTO.getHostResponseRate());
        }
        if (hostDTO.getHostResponseTime() != null) {
            user.setHostResponseTime(hostDTO.getHostResponseTime());
        }
        
        // 序列化languages字段
        if (hostDTO.getLanguages() != null) {
            try {
                String languagesJson = objectMapper.writeValueAsString(hostDTO.getLanguages());
                user.setLanguages(languagesJson);
            } catch (JsonProcessingException e) {
                logger.error("序列化语言字段失败", e);
            }
        }
        
        // 序列化companions字段
        if (hostDTO.getCompanions() != null) {
            try {
                String companionsJson = objectMapper.writeValueAsString(hostDTO.getCompanions());
                user.setCompanions(companionsJson);
            } catch (JsonProcessingException e) {
                logger.error("序列化接待伙伴字段失败", e);
            }
        }
        
        // 如果是新房东，设置hostSince字段
        if (user.getHostSince() == null) {
            user.setHostSince(LocalDateTime.now());
        }
        
        user = userRepository.save(user);
        return getHostInfo(username); // 返回完整信息
    }

    // 头像上传功能已迁移到FileController统一处理
    // 请使用 /api/files/upload?type=avatar 端点

    @Override
    public HostStatisticsDTO getHostStatistics(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
        
        // 获取房源数量
        Long homestayCount = homestayRepository.countByOwnerId(user.getId());
        
        // 获取订单总数
        Long orderCount = orderRepository.countByHostId(user.getId());
        
        // 获取待处理订单数
        Long pendingOrders = orderRepository.countByHostIdAndStatus(user.getId(), "PENDING");
        
        // 获取已完成订单数
        Long completedOrders = orderRepository.countByHostIdAndStatus(user.getId(), "COMPLETED");
        
        // 获取已取消订单数
        Long cancelledOrders = orderRepository.countByHostIdAndStatus(user.getId(), "CANCELLED");
        
        // 获取评价总数
        Long reviewCount = reviewRepository.countByHostId(user.getId());
        
        // 获取平均评分
        Double avgRating = reviewRepository.getAverageRatingByHostId(user.getId());
        
        // 获取总收入
        Double totalEarnings = orderRepository.getTotalEarningsByHostId(user.getId());
        
        return HostStatisticsDTO.builder()
                .homestayCount(homestayCount.intValue())
                .orderCount(orderCount.intValue())
                .reviewCount(reviewCount.intValue())
                .rating(avgRating)
                .totalEarnings(totalEarnings)
                .pendingOrders(pendingOrders.intValue())
                .completedOrders(completedOrders.intValue())
                .cancelledOrders(cancelledOrders.intValue())
                .build();
    }

    @Override
    public Page<?> getHostHomestays(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return homestayRepository.findByOwnerId(user.getId(), pageable)
                .map(homestay -> convertToHomestayDTO(homestay));
    }

    @Override
    public Page<?> getHostOrders(String username, int page, int size, String status) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        if (status != null && !status.isEmpty()) {
            return orderRepository.findByHostIdAndStatus(user.getId(), status, pageable)
                    .map(order -> convertToOrderDTO(order));
        } else {
            return orderRepository.findByHostId(user.getId(), pageable)
                    .map(order -> convertToOrderDTO(order));
        }
    }

    @Override
    public Page<?> getHostReviews(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return reviewRepository.findByHostId(user.getId(), pageable)
                .map(review -> convertToReviewDTO(review));
    }

    private HomestayDTO convertToHomestayDTO(Homestay homestay) {
        if (homestay == null) {
            return null;
        }
        return HomestayDTO.builder()
                .id(homestay.getId())
                .title(homestay.getTitle())
                .description(homestay.getDescription())
                .price(homestay.getPrice() != null ? homestay.getPrice().toString() : "0")
                .addressDetail(homestay.getAddressDetail())
                .type(homestay.getType())
                .status(homestay.getStatus() != null ? homestay.getStatus().name() : null)
                .maxGuests(homestay.getMaxGuests())
                .minNights(homestay.getMinNights())
                .maxNights(homestay.getMaxNights())
                .coverImage(homestay.getCoverImage())
                .featured(homestay.getFeatured())
                .provinceCode(homestay.getProvinceCode())
                .cityCode(homestay.getCityCode())
                .districtCode(homestay.getDistrictCode())
                .createdAt(homestay.getCreatedAt())
                .updatedAt(homestay.getUpdatedAt())
                .build();
    }
    
    private OrderDTO convertToOrderDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .homestayId(order.getHomestay().getId())
                .homestayTitle(order.getHomestay().getTitle())
                .guestId(order.getGuest().getId())
                .checkInDate(order.getCheckInDate())
                .checkOutDate(order.getCheckOutDate())
                .guestCount(order.getGuestCount())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createTime(order.getCreatedAt())
                .build();
    }
    
    private ReviewDTO convertToReviewDTO(Review review) {
        return ReviewDTO.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .homestayId(review.getHomestay().getId())
                .rating(review.getRating())
                .content(review.getContent())
                .createTime(review.getCreateTime())
                .build();
    }
    
    @Override
    public HostDTO getHomestayHostInfo(Long homestayId) {
        // 根据房源ID查询房源信息
        Homestay homestay = homestayRepository.findById(homestayId)
                .orElseThrow(() -> new ResourceNotFoundException("房源不存在，ID: " + homestayId));
        
        // 获取房源所有者ID
        Long ownerId = homestay.getOwner().getId();
        
        // 根据房东ID查询用户信息
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("房东不存在，ID: " + ownerId));
        
        // 创建DTO对象并返回
        HostDTO hostDTO = HostDTO.fromUser(owner);
        
        // === 补充缺失字段 ===
        
        // 1. 设置房东加入时间
        if (owner.getCreatedAt() != null) {
            hostDTO.setHostSince(owner.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        
        // 2. 计算并设置房东评分
        Double avgRating = reviewRepository.getAverageRatingByHostId(ownerId);
        if (avgRating != null && avgRating > 0) {
            hostDTO.setHostRating(String.format("%.1f", avgRating));
        }
        
        // 3. 计算房东经验年数
        if (owner.getCreatedAt() != null) {
            long years = ChronoUnit.YEARS.between(owner.getCreatedAt(), LocalDateTime.now());
            hostDTO.setHostYears(String.valueOf(Math.max(1, years))); // 至少1年
        }
        
        // 4. 设置房源数量显示
        Long homestayCount = homestayRepository.countByOwnerId(ownerId);
        if (homestayCount != null && homestayCount > 0) {
            hostDTO.setHostAccommodations(homestayCount.toString());
        }
        
        // 5. 计算响应率和响应时间（基于订单处理效率）
        calculateHostResponseMetrics(hostDTO, ownerId);
        
        // 解析languages字段
        if (owner.getLanguages() != null && !owner.getLanguages().isEmpty()) {
            try {
                // 先尝试JSON格式解析
                List<String> languages = objectMapper.readValue(owner.getLanguages(), new TypeReference<List<String>>() {});
                hostDTO.setLanguages(languages);
            } catch (JsonProcessingException e) {
                // JSON解析失败，尝试逗号分隔格式
                try {
                    List<String> languages = Arrays.asList(owner.getLanguages().split(","));
                    // 去除每个语言项的首尾空格
                    languages = languages.stream()
                            .map(String::trim)
                            .filter(lang -> !lang.isEmpty())
                            .collect(Collectors.toList());
                    hostDTO.setLanguages(languages);
                    logger.debug("使用逗号分隔格式解析语言字段: {}", languages);
                } catch (Exception ex) {
                    logger.error("解析语言字段失败，原始数据: {}", owner.getLanguages(), ex);
                    hostDTO.setLanguages(new ArrayList<>());
                }
            }
        } else {
            hostDTO.setLanguages(new ArrayList<>());
        }
        
        // 解析companions字段
        if (owner.getCompanions() != null && !owner.getCompanions().isEmpty()) {
            try {
                // 先尝试JSON格式解析
                List<Map<String, String>> companions = objectMapper.readValue(owner.getCompanions(), 
                        new TypeReference<List<Map<String, String>>>() {});
                hostDTO.setCompanions(companions);
            } catch (JsonProcessingException e) {
                // JSON解析失败，可能是简单字符串格式，设置为空数组
                logger.warn("解析接待伙伴字段失败，原始数据: {}, 将设置为空数组", owner.getCompanions());
                hostDTO.setCompanions(new ArrayList<>());
            }
        } else {
            hostDTO.setCompanions(new ArrayList<>());
        }
        
        // 填充统计数据
        Long orderCount = orderRepository.countByHostId(ownerId);
        Long reviewCount = reviewRepository.countByHostId(ownerId);
        
        hostDTO.setHomestayCount(homestayCount != null ? homestayCount.intValue() : 0);
        hostDTO.setOrderCount(orderCount != null ? orderCount.intValue() : 0);
        hostDTO.setReviewCount(reviewCount != null ? reviewCount.intValue() : 0);
        hostDTO.setRating(avgRating != null ? avgRating : 0.0);
        
        logger.info("房东信息构建完成，房东ID: {}, 房源数: {}, 订单数: {}, 评价数: {}, 评分: {}", 
                ownerId, hostDTO.getHomestayCount(), hostDTO.getOrderCount(), 
                hostDTO.getReviewCount(), hostDTO.getRating());
        
        // 返回结果
        return hostDTO;
    }
    
    /**
     * 计算房东响应指标
     * 基于订单处理效率和评价反馈
     */
    private void calculateHostResponseMetrics(HostDTO hostDTO, Long hostId) {
        try {
            // 获取最近30天的订单数据
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            
            // 查询订单处理效率（这里需要扩展Order实体，添加响应时间字段）
            // 暂时使用模拟逻辑
            Long totalOrders = orderRepository.countByHostId(hostId);
            Long recentOrders = orderRepository.countByHostIdAndCreatedAtAfter(hostId, thirtyDaysAgo);
            
            // 基于活跃度计算响应率
            if (totalOrders != null && totalOrders > 0) {
                if (recentOrders != null && recentOrders > 0) {
                    // 活跃房东
                    hostDTO.setHostResponseRate("95%");
                    hostDTO.setHostResponseTime("1小时内");
                } else if (totalOrders > 5) {
                    // 有经验但不太活跃
                    hostDTO.setHostResponseRate("85%");
                    hostDTO.setHostResponseTime("6小时内");
                } else {
                    // 新房东
                    hostDTO.setHostResponseRate("90%");
                    hostDTO.setHostResponseTime("12小时内");
                }
            } else {
                // 没有订单历史
                hostDTO.setHostResponseRate("未知");
                hostDTO.setHostResponseTime("未知");
            }
            
        } catch (Exception e) {
            logger.warn("计算房东响应指标失败: {}", e.getMessage());
            hostDTO.setHostResponseRate("90%");
            hostDTO.setHostResponseTime("24小时内");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> updateHostProfile(String username, Map<String, Object> profileData) {
        logger.info("更新房东个人资料: username={}, data={}", username, profileData);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 角色转换应通过 becomeHost() 方法完成，此处不再自动转换
        if (!UserRole.ROLE_HOST.name().equals(user.getRole())) {
            throw new RuntimeException("请先完成房东入驻流程");
        }
        
        // 更新用户基本信息
        if (profileData.containsKey("realName")) {
            user.setRealName((String) profileData.get("realName"));
        }
        
        if (profileData.containsKey("phone")) {
            user.setPhone((String) profileData.get("phone"));
        }
        
        if (profileData.containsKey("gender")) {
            user.setGender((String) profileData.get("gender"));
        }
        
        if (profileData.containsKey("birthday")) {
            user.setBirthday((String) profileData.get("birthday"));
        }
        
        if (profileData.containsKey("occupation")) {
            user.setOccupation((String) profileData.get("occupation"));
        }
        
        if (profileData.containsKey("languages")) {
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) profileData.get("languages");
            user.setLanguages(String.join(",", languages));
        }
        
        if (profileData.containsKey("introduction")) {
            user.setIntroduction((String) profileData.get("introduction"));
        }
        
        if (profileData.containsKey("idCard")) {
            user.setIdCard((String) profileData.get("idCard"));
        }
        
        if (profileData.containsKey("idCardFront")) {
            user.setIdCardFront((String) profileData.get("idCardFront"));
        }
        
        if (profileData.containsKey("idCardBack")) {
            user.setIdCardBack((String) profileData.get("idCardBack"));
        }
        
        if (profileData.containsKey("verificationStatus")) {
            String status = (String) profileData.get("verificationStatus");
            try {
                VerificationStatus verificationStatus = VerificationStatus.valueOf(status);
                user.setVerificationStatus(verificationStatus);
            } catch (IllegalArgumentException e) {
                logger.warn("无效的验证状态: {}", status);
            }
        }
        
        // 处理头像字段
        if (profileData.containsKey("avatar")) {
            String avatarUrl = (String) profileData.get("avatar");
            user.setAvatar(avatarUrl);
            logger.info("更新用户头像: username={}, avatarUrl={}", username, avatarUrl);
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        return getHostProfile(username);
    }

    @Override
    public Map<String, Object> getHostProfile(String username) {
        logger.info("获取房东个人资料: username={}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 允许非房东用户也能调用此接口（入驻流程需要预填已有信息）
        // 非房东用户会返回基本资料
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("realName", user.getRealName());
        profile.put("idCard", user.getIdCard());
        profile.put("gender", user.getGender());
        profile.put("birthday", user.getBirthday());
        profile.put("occupation", user.getOccupation());
        
        // 处理语言列表
        if (user.getLanguages() != null && !user.getLanguages().isEmpty()) {
            profile.put("languages", Arrays.asList(user.getLanguages().split(",")));
        } else {
            profile.put("languages", new ArrayList<>());
        }
        
        profile.put("introduction", user.getIntroduction());
        profile.put("avatar", user.getAvatar());
        profile.put("verificationStatus", user.getVerificationStatus() != null ? 
                user.getVerificationStatus().name() : VerificationStatus.UNVERIFIED.name());
        profile.put("idCardFront", user.getIdCardFront());
        profile.put("idCardBack", user.getIdCardBack());
        
        // 获取房源数量
        Long homestayCount = homestayRepository.countByHostId(user.getId());
        profile.put("homestayCount", homestayCount);
        
        // 获取订单数量
        Long orderCount = orderRepository.countByHostId(user.getId());
        profile.put("orderCount", orderCount);
        
        // 获取评价数量
        Long reviewCount = reviewRepository.countByHostId(user.getId());
        profile.put("reviewCount", reviewCount);
        
        // 添加成为房东时间
        profile.put("hostSince", user.getCreatedAt() != null ? 
                user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "");
        
        return profile;
    }

    @Override
    @Transactional
    public String uploadHostDocument(String username, MultipartFile file, String type) throws IOException {
        logger.info("上传房东证件照片: username={}, type={}", username, type);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 确保用户是房东
        if (!user.getRole().equals(UserRole.ROLE_HOST.name())) {
            throw new RuntimeException("只有房东才能上传证件照片");
        }
        
        // 使用FileService上传文件
        Map<String, Object> result = fileService.uploadFile(file, FileService.TYPE_COMMON);
        String fileUrl = (String) result.get("fileUrl");
        
        // 更新用户资料
        if ("idCardFront".equals(type)) {
            user.setIdCardFront(fileUrl);
        } else if ("idCardBack".equals(type)) {
            user.setIdCardBack(fileUrl);
        }
        
        userRepository.save(user);
        
        return fileUrl;
    }

    @Override
    public List<HomestayOptionDTO> getHostHomestayOptions(String username) {
        // 1. 获取房东用户实体
        User host = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("房东用户不存在: " + username));

        // 2. 查询该房东的所有房源 (只查询 ID 和 Title，如果 HomestayRepository 有优化方法更好，否则查询完整实体)
        // 假设 HomestayRepository 没有只查询 ID 和 Title 的方法，先获取完整列表
        List<Homestay> homestays = homestayRepository.findByOwnerId(host.getId());
        
        // 3. 映射为 HomestayOptionDTO 列表
        return homestays.stream()
                .map(homestay -> new HomestayOptionDTO(homestay.getId(), homestay.getTitle()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Map<String, Object> becomeHost(String username) {
        logger.info("用户申请成为房东: username={}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        // 如果已经是房东，直接返回成功
        if (UserRole.ROLE_HOST.name().equals(user.getRole())) {
            logger.info("用户已是房东: username={}", username);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "您已经是房东");
            result.put("isAlreadyHost", true);
            return result;
        }

        // 将用户角色从 ROLE_USER 转换为 ROLE_HOST
        String oldRole = user.getRole();
        user.setRole(UserRole.ROLE_HOST.name());

        // 设置成为房东的时间
        if (user.getHostSince() == null) {
            user.setHostSince(LocalDateTime.now());
        }

        userRepository.save(user);

        logger.info("用户角色转换成功: username={}, {} -> {}", username, oldRole, user.getRole());

        // 生成新的JWT token，包含更新后的角色
        String newToken = jwtTokenProvider.generateToken(
            user.getUsername(),
            user.getId(),
            UserRole.ROLE_HOST.name()
        );

        logger.info("为用户 {} 生成新的JWT token", username);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已成为房东");
        result.put("isAlreadyHost", false);
        result.put("role", user.getRole());
        result.put("token", newToken);

        return result;
    }
} 