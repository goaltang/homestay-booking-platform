package com.homestay3.homestaybackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片URL处理工具类
 * 统一处理所有图片URL的格式化和验证
 */
@Slf4j
@Component
public class ImageUrlUtil {

    @Value("${app.server.url:http://localhost:8081}")
    private String serverUrl;

    /**
     * 确保图片URL是完整的，包含域名
     */
    public String ensureCompleteImageUrl(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        
        // 如果已经是完整URL，直接返回
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }
        
        // 确保路径以/开头
        if (!imagePath.startsWith("/")) {
            imagePath = "/" + imagePath;
        }
        
        // 返回完整URL
        return serverUrl + imagePath;
    }

    /**
     * 批量处理图片URL列表
     */
    public List<String> ensureCompleteImageUrls(List<String> imagePaths) {
        if (imagePaths == null) {
            return null;
        }
        
        return imagePaths.stream()
                .map(this::ensureCompleteImageUrl)
                .filter(url -> url != null && !url.trim().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 获取默认的测试图片URL
     */
    public String getDefaultTestImageUrl() {
        return ensureCompleteImageUrl("/uploads/homestays/fcefb873-2ed7-4284-b1b9-b9145dc2188a.jpg");
    }

    /**
     * 验证图片URL是否有效
     */
    public boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }
        
        // 简单的URL格式检查
        return imageUrl.startsWith("http://") || 
               imageUrl.startsWith("https://") || 
               imageUrl.startsWith("/");
    }

    /**
     * 获取房源主图片
     * 优先级：封面图片 > 图片集第一张 > 默认图片
     */
    public String getMainImage(String coverImage, List<String> images) {
        // 1. 优先使用封面图片
        if (coverImage != null && !coverImage.trim().isEmpty()) {
            return ensureCompleteImageUrl(coverImage);
        }
        
        // 2. 其次使用图片集的第一张
        if (images != null && !images.isEmpty()) {
            String firstImage = images.get(0);
            if (firstImage != null && !firstImage.trim().isEmpty()) {
                return ensureCompleteImageUrl(firstImage);
            }
        }
        
        // 3. 返回默认图片
        return getDefaultTestImageUrl();
    }
} 