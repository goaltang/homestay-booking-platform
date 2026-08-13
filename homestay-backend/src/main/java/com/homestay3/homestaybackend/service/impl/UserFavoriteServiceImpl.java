package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.UserFavoriteDTO;
import com.homestay3.homestaybackend.mapper.HomestayMapper;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.UserFavorite;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserFavoriteRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.UserFavoriteService;
import com.homestay3.homestaybackend.service.search.UserBehaviorTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserFavoriteServiceImpl implements UserFavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final HomestayRepository homestayRepository;
    private final UserRepository userRepository;
    private final HomestayMapper homestayMapper;
    private final UserBehaviorTrackingService userBehaviorTrackingService;

    @Autowired
    public UserFavoriteServiceImpl(UserFavoriteRepository userFavoriteRepository,
                                   HomestayRepository homestayRepository,
                                   UserRepository userRepository,
                                   HomestayMapper homestayMapper,
                                   UserBehaviorTrackingService userBehaviorTrackingService) {
        this.userFavoriteRepository = userFavoriteRepository;
        this.homestayRepository = homestayRepository;
        this.userRepository = userRepository;
        this.homestayMapper = homestayMapper;
        this.userBehaviorTrackingService = userBehaviorTrackingService;
    }

    @Override
    @Transactional
    public UserFavoriteDTO addFavorite(Long userId, Long homestayId) {
        log.info("用户 {} 添加收藏民宿 {}", userId, homestayId);
        
        // 检查用户是否存在
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        
        // 检查民宿是否存在
        Homestay homestay = homestayRepository.findById(homestayId)
                .orElseThrow(() -> new IllegalArgumentException("民宿不存在: " + homestayId));
        
        // 检查是否已收藏
        if (userFavoriteRepository.existsByUserIdAndHomestayId(userId, homestayId)) {
            throw new IllegalStateException("已经收藏过该民宿");
        }
        
        // 创建收藏记录
        UserFavorite userFavorite = new UserFavorite(userId, homestayId);
        userFavorite = userFavoriteRepository.save(userFavorite);

        try {
            userBehaviorTrackingService.trackFavorite(
                    userId,
                    null,
                    homestayId,
                    homestay.getCityCode(),
                    homestay.getType(),
                    homestay.getPrice());
        } catch (Exception e) {
            log.warn("记录收藏行为失败: userId={}, homestayId={}, error={}", userId, homestayId, e.getMessage());
        }
        
        log.info("成功添加收藏: 用户 {} 收藏民宿 {}", userId, homestayId);
        
        return new UserFavoriteDTO(userFavorite.getId(), userFavorite.getUserId(), 
                                   userFavorite.getHomestayId(), userFavorite.getCreatedAt());
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long homestayId) {
        log.info("用户 {} 取消收藏民宿 {}", userId, homestayId);
        
        if (!userFavoriteRepository.existsByUserIdAndHomestayId(userId, homestayId)) {
            throw new IllegalStateException("尚未收藏该民宿");
        }
        
        userFavoriteRepository.deleteByUserIdAndHomestayId(userId, homestayId);
        log.info("成功取消收藏: 用户 {} 取消收藏民宿 {}", userId, homestayId);
    }

    @Override
    public boolean isFavorite(Long userId, Long homestayId) {
        return userFavoriteRepository.existsByUserIdAndHomestayId(userId, homestayId);
    }

    @Override
    public List<HomestayDTO> getUserFavoriteHomestays(Long userId) {
        log.info("获取用户 {} 的收藏民宿列表", userId);
        
        List<Long> homestayIds = userFavoriteRepository.findHomestayIdsByUserId(userId);
        if (homestayIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Homestay> homestays = homestayRepository.findAllByIdIn(homestayIds);
        
        // 保持收藏的顺序
        List<HomestayDTO> result = new ArrayList<>();
        for (Long homestayId : homestayIds) {
            homestays.stream()
                    .filter(h -> h.getId().equals(homestayId))
                    .findFirst()
                    .ifPresent(homestay -> {
                        try {
                            HomestayDTO dto = homestayMapper.toDTO(homestay);
                            result.add(dto);
                        } catch (Exception e) {
                            log.error("转换民宿 {} 到DTO时出错: {}", homestayId, e.getMessage());
                        }
                    });
        }
        
        log.info("用户 {} 共有 {} 个收藏民宿", userId, result.size());
        return result;
    }

    @Override
    public List<Long> getUserFavoriteHomestayIds(Long userId) {
        return userFavoriteRepository.findHomestayIdsByUserId(userId);
    }

    @Override
    public List<UserFavoriteDTO> getUserFavorites(Long userId) {
        List<UserFavorite> favorites = userFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return favorites.stream()
                .map(f -> new UserFavoriteDTO(f.getId(), f.getUserId(), f.getHomestayId(), f.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void clearUserFavorites(Long userId) {
        log.info("清空用户 {} 的所有收藏", userId);
        userFavoriteRepository.deleteAllByUserId(userId);
    }

    @Override
    public long getUserFavoriteCount(Long userId) {
        return userFavoriteRepository.countByUserId(userId);
    }

    @Override
    public long getHomestayFavoriteCount(Long homestayId) {
        return userFavoriteRepository.countByHomestayId(homestayId);
    }

    @Override
    public List<Boolean> checkFavoriteStatus(Long userId, List<Long> homestayIds) {
        return homestayIds.stream()
                .map(homestayId -> userFavoriteRepository.existsByUserIdAndHomestayId(userId, homestayId))
                .collect(Collectors.toList());
    }
}
