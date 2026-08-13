package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.HomestayGroupDTO;
import com.homestay3.homestaybackend.dto.HomestayGroupRequest;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayGroup;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.repository.HomestayGroupRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomestayGroupService {

    private final HomestayGroupRepository groupRepository;
    private final HomestayRepository homestayRepository;
    private final UserRepository userRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<HomestayGroupDTO> getGroupsByOwner(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        List<HomestayGroup> groups = groupRepository.findByOwnerIdAndEnabledTrueOrderBySortOrderAsc(user.getId());

        return groups.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public HomestayGroupDTO getGroupById(Long groupId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HomestayGroup group = groupRepository.findByOwnerIdAndId(user.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        return toDTO(group);
    }

    @Transactional
    public HomestayGroupDTO createGroup(HomestayGroupRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            if (groupRepository.existsByOwnerIdAndCode(user.getId(), request.getCode())) {
                throw new IllegalArgumentException("分组编码已存在: " + request.getCode());
            }
        }

        if (groupRepository.existsByOwnerIdAndName(user.getId(), request.getName())) {
            throw new IllegalArgumentException("分组名称已存在: " + request.getName());
        }

        HomestayGroup group = HomestayGroup.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .icon(request.getIcon())
                .color(request.getColor())
                .owner(user)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isDefault(false)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        HomestayGroup saved = groupRepository.save(group);
        log.info("创建房源分组成功: id={}, name={}, owner={}", saved.getId(), saved.getName(), username);

        return toDTO(saved);
    }

    @Transactional
    public HomestayGroupDTO updateGroup(Long groupId, HomestayGroupRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HomestayGroup group = groupRepository.findByOwnerIdAndId(user.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        if (!group.getName().equals(request.getName())
                && groupRepository.existsByOwnerIdAndName(user.getId(), request.getName())) {
            throw new IllegalArgumentException("分组名称已存在: " + request.getName());
        }

        if (request.getCode() != null && !request.getCode().isBlank()
                && !request.getCode().equals(group.getCode())
                && groupRepository.existsByOwnerIdAndCode(user.getId(), request.getCode())) {
            throw new IllegalArgumentException("分组编码已存在: " + request.getCode());
        }

        group.setName(request.getName());
        if (request.getCode() != null) {
            group.setCode(request.getCode());
        }
        group.setDescription(request.getDescription());
        group.setIcon(request.getIcon());
        group.setColor(request.getColor());
        if (request.getSortOrder() != null) {
            group.setSortOrder(request.getSortOrder());
        }
        if (request.getEnabled() != null) {
            group.setEnabled(request.getEnabled());
        }

        HomestayGroup saved = groupRepository.save(group);
        log.info("更新房源分组成功: id={}, name={}, owner={}", saved.getId(), saved.getName(), username);

        return toDTO(saved);
    }

    @Transactional
    public void deleteGroup(Long groupId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HomestayGroup group = groupRepository.findByOwnerIdAndId(user.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw new IllegalArgumentException("默认分组不能删除");
        }

        List<Homestay> homestays = homestayRepository.findByOwnerIdAndGroupId(user.getId(), groupId);
        for (Homestay homestay : homestays) {
            homestay.setGroup(null);
        }
        homestayRepository.saveAll(homestays);

        groupRepository.delete(group);
        log.info("删除房源分组成功: id={}, name={}, owner={}", groupId, group.getName(), username);
    }

    @Transactional
    public void assignHomestaysToGroup(Long groupId, List<Long> homestayIds, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HomestayGroup group = groupRepository.findByOwnerIdAndId(user.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        List<Homestay> homestays = homestayRepository.findAllByIdIn(homestayIds);
        for (Homestay homestay : homestays) {
            if (!homestay.getOwner().getId().equals(user.getId())) {
                throw new IllegalArgumentException("房源不属于当前房东: " + homestay.getId());
            }
            homestay.setGroup(group);
        }
        homestayRepository.saveAll(homestays);

        log.info("批量分配房源到分组成功: groupId={}, count={}, owner={}", groupId, homestays.size(), username);
    }

    @Transactional
    public void removeHomestaysFromGroup(List<Long> homestayIds, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        List<Homestay> homestays = homestayRepository.findAllByIdIn(homestayIds);
        for (Homestay homestay : homestays) {
            if (!homestay.getOwner().getId().equals(user.getId())) {
                throw new IllegalArgumentException("房源不属于当前房东: " + homestay.getId());
            }
            if (homestay.getGroup() != null) {
                homestay.setGroup(null);
            }
        }
        homestayRepository.saveAll(homestays);

        log.info("批量移除房源分组成功: count={}, owner={}", homestays.size(), username);
    }

    public Page<HomestayDTO> getHomestaysByGroup(Long groupId, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));

        HomestayGroup group = groupRepository.findByOwnerIdAndId(user.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        Page<Homestay> homestays = homestayRepository.findByGroupIdAndOwnerId(group.getId(), user.getId(), pageable);

        return homestays.map(h -> {
            HomestayDTO dto = new HomestayDTO();
            dto.setId(h.getId());
            dto.setTitle(h.getTitle());
            dto.setType(h.getType());
            dto.setPrice(h.getPrice() != null ? h.getPrice().toString() : null);
            dto.setStatus(h.getStatus() != null ? h.getStatus().name() : null);
            dto.setCoverImage(h.getCoverImage());
            dto.setCityText(h.getCityText());
            dto.setProvinceText(h.getProvinceText());
            return dto;
        });
    }

    private HomestayGroupDTO toDTO(HomestayGroup group) {
        long homestayCount = homestayRepository.countByGroupId(group.getId());

        return HomestayGroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .code(group.getCode())
                .description(group.getDescription())
                .icon(group.getIcon())
                .color(group.getColor())
                .ownerId(group.getOwner().getId())
                .ownerUsername(group.getOwner().getUsername())
                .sortOrder(group.getSortOrder())
                .isDefault(group.getIsDefault())
                .enabled(group.getEnabled())
                .homestayCount(homestayCount)
                .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    // ==================== 管理员方法 ====================

    public Page<HomestayGroupDTO> adminGetGroups(Pageable pageable, String keyword, Long ownerId) {
        Page<HomestayGroup> groups;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasOwnerId = ownerId != null;

        if (hasKeyword && hasOwnerId) {
            groups = groupRepository.searchByKeywordAndOwnerId(keyword, ownerId, pageable);
        } else if (hasKeyword) {
            groups = groupRepository.searchByKeyword(keyword, pageable);
        } else if (hasOwnerId) {
            groups = groupRepository.findByOwnerId(ownerId, pageable);
        } else {
            groups = groupRepository.findAllOrderByCreatedAtDesc(pageable);
        }
        return groups.map(this::toDTO);
    }

    public HomestayGroupDTO adminGetGroupById(Long groupId) {
        HomestayGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));
        return toDTO(group);
    }

    @Transactional
    public HomestayGroupDTO adminToggleGroupEnabled(Long groupId, boolean enabled) {
        HomestayGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));
        group.setEnabled(enabled);
        HomestayGroup saved = groupRepository.save(group);
        log.info("管理员{}分组: id={}, name={}, enabled={}", enabled ? "启用" : "禁用", saved.getId(), saved.getName(), enabled);
        return toDTO(saved);
    }

    @Transactional
    public void adminDeleteGroup(Long groupId) {
        HomestayGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));

        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw new IllegalArgumentException("默认分组不能删除");
        }

        List<Homestay> homestays = homestayRepository.findByOwnerIdAndGroupId(group.getOwner().getId(), groupId);
        for (Homestay homestay : homestays) {
            homestay.setGroup(null);
        }
        homestayRepository.saveAll(homestays);

        groupRepository.delete(group);
        log.info("管理员删除分组: id={}, name={}, owner={}", groupId, group.getName(), group.getOwner().getUsername());
    }
}
