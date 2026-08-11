package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.annotation.OperationLog;
import com.homestay3.homestaybackend.dto.ApiResponse;
import com.homestay3.homestaybackend.dto.BannerDTO;
import com.homestay3.homestaybackend.entity.Banner;
import com.homestay3.homestaybackend.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminBannerController {

    private final HomeService homeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerDTO>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(homeService.getAllBanners()));
    }

    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sortOrder"));
        Page<BannerDTO> result = homeService.getBannersPage(keyword, pageRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerDTO>> getById(@PathVariable Long id) {
        BannerDTO dto = homeService.getBannerById(id);
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.error("Banner不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping
    @OperationLog(operationType = "CREATE", resource = "BANNER", resourceId = "#result?.body?.data?.id")
    public ResponseEntity<ApiResponse<BannerDTO>> create(@RequestBody Banner banner) {
        BannerDTO created = homeService.createBanner(banner);
        return ResponseEntity.ok(ApiResponse.success(created, "创建成功"));
    }

    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE", resource = "BANNER", resourceId = "#id")
    public ResponseEntity<ApiResponse<BannerDTO>> update(@PathVariable Long id, @RequestBody Banner banner) {
        BannerDTO updated = homeService.updateBanner(id, banner);
        if (updated == null) {
            return ResponseEntity.ok(ApiResponse.error("Banner不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(updated, "更新成功"));
    }

    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE", resource = "BANNER", resourceId = "#id")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        homeService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功"));
    }

    @PatchMapping("/{id}/toggle")
    @OperationLog(operationType = "UPDATE", resource = "BANNER", resourceId = "#id")
    public ResponseEntity<ApiResponse<BannerDTO>> toggleEnabled(@PathVariable Long id) {
        BannerDTO updated = homeService.toggleBannerEnabled(id);
        if (updated == null) {
            return ResponseEntity.ok(ApiResponse.error("Banner不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(updated, "状态更新成功"));
    }
}
