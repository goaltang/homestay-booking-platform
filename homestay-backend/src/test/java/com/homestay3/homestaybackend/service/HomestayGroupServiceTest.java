package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.HomestayDTO;
import com.homestay3.homestaybackend.dto.HomestayGroupDTO;
import com.homestay3.homestaybackend.dto.HomestayGroupRequest;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayGroup;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayGroupRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomestayGroupServiceTest {

    @Mock
    private HomestayGroupRepository groupRepository;

    @Mock
    private HomestayRepository homestayRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HomestayGroupService groupService;

    private User host;
    private HomestayGroup group;
    private Homestay homestay1;
    private Homestay homestay2;

    @BeforeEach
    void setUp() {
        host = new User();
        host.setId(1L);
        host.setUsername("testhost");
        host.setRole("ROLE_HOST");

        group = HomestayGroup.builder()
                .id(1L)
                .name("海景房")
                .code("sea-view")
                .description("所有海景房源")
                .icon("ocean")
                .color("#0066cc")
                .owner(host)
                .sortOrder(1)
                .isDefault(false)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        homestay1 = new Homestay();
        homestay1.setId(10L);
        homestay1.setTitle("海景大床房");
        homestay1.setType("apartment");
        homestay1.setPrice(new BigDecimal("500.00"));
        homestay1.setStatus(HomestayStatus.ACTIVE);
        homestay1.setCoverImage("https://example.com/img1.jpg");
        homestay1.setCityText("厦门");
        homestay1.setProvinceText("福建");
        homestay1.setOwner(host);
        homestay1.setGroup(group);

        homestay2 = new Homestay();
        homestay2.setId(11L);
        homestay2.setTitle("海景双床房");
        homestay2.setType("apartment");
        homestay2.setPrice(new BigDecimal("600.00"));
        homestay2.setStatus(HomestayStatus.ACTIVE);
        homestay2.setCoverImage("https://example.com/img2.jpg");
        homestay2.setCityText("厦门");
        homestay2.setProvinceText("福建");
        homestay2.setOwner(host);
        homestay2.setGroup(group);
    }

    @Test
    void getGroupsByOwner_Success() {
        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndEnabledTrueOrderBySortOrderAsc(1L))
                .thenReturn(Arrays.asList(group));

        List<HomestayGroupDTO> result = groupService.getGroupsByOwner("testhost");

        assertEquals(1, result.size());
        assertEquals("海景房", result.get(0).getName());
        assertEquals("sea-view", result.get(0).getCode());
        assertEquals(1L, result.get(0).getOwnerId());
        verify(groupRepository).findByOwnerIdAndEnabledTrueOrderBySortOrderAsc(1L);
    }

    @Test
    void getGroupsByOwner_UserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                groupService.getGroupsByOwner("unknown"));
    }

    @Test
    void getGroupById_Success() {
        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));

        HomestayGroupDTO result = groupService.getGroupById(1L, "testhost");

        assertEquals("海景房", result.getName());
        assertEquals("sea-view", result.getCode());
    }

    @Test
    void getGroupById_NotFound() {
        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                groupService.getGroupById(999L, "testhost"));
    }

    @Test
    void createGroup_Success() {
        HomestayGroupRequest request = HomestayGroupRequest.builder()
                .name("别墅")
                .code("villa")
                .description("所有别墅房源")
                .icon("house")
                .color("#cc6600")
                .sortOrder(2)
                .enabled(true)
                .build();

        HomestayGroup savedGroup = HomestayGroup.builder()
                .id(2L)
                .name("别墅")
                .code("villa")
                .description("所有别墅房源")
                .icon("house")
                .color("#cc6600")
                .owner(host)
                .sortOrder(2)
                .isDefault(false)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.existsByOwnerIdAndCode(1L, "villa")).thenReturn(false);
        when(groupRepository.existsByOwnerIdAndName(1L, "别墅")).thenReturn(false);
        when(groupRepository.save(any(HomestayGroup.class))).thenReturn(savedGroup);

        HomestayGroupDTO result = groupService.createGroup(request, "testhost");

        assertEquals("别墅", result.getName());
        assertEquals("villa", result.getCode());
        verify(groupRepository).save(any(HomestayGroup.class));
    }

    @Test
    void createGroup_DuplicateName() {
        HomestayGroupRequest request = HomestayGroupRequest.builder()
                .name("海景房")
                .code("sea-view-2")
                .build();

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.existsByOwnerIdAndName(1L, "海景房")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                groupService.createGroup(request, "testhost"));
    }

    @Test
    void createGroup_DuplicateCode() {
        HomestayGroupRequest request = HomestayGroupRequest.builder()
                .name("新房")
                .code("sea-view")
                .build();

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.existsByOwnerIdAndCode(1L, "sea-view")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                groupService.createGroup(request, "testhost"));
    }

    @Test
    void updateGroup_Success() {
        HomestayGroupRequest request = HomestayGroupRequest.builder()
                .name("海景房(更新)")
                .code("sea-view")
                .description("更新后的描述")
                .sortOrder(5)
                .enabled(false)
                .build();

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByOwnerIdAndName(1L, "海景房(更新)")).thenReturn(false);
        when(groupRepository.save(any(HomestayGroup.class))).thenAnswer(i -> i.getArgument(0));

        HomestayGroupDTO result = groupService.updateGroup(1L, request, "testhost");

        assertEquals("海景房(更新)", result.getName());
        assertEquals("更新后的描述", result.getDescription());
        verify(groupRepository).save(any(HomestayGroup.class));
    }

    @Test
    void updateGroup_NotFound() {
        HomestayGroupRequest request = HomestayGroupRequest.builder()
                .name("新房")
                .build();

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                groupService.updateGroup(999L, request, "testhost"));
    }

    @Test
    void deleteGroup_Success() {
        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));
        when(homestayRepository.findByOwnerIdAndGroupId(1L, 1L)).thenReturn(Arrays.asList(homestay1, homestay2));

        groupService.deleteGroup(1L, "testhost");

        verify(homestayRepository).saveAll(any());
        verify(groupRepository).delete(group);
        assertNull(homestay1.getGroup());
        assertNull(homestay2.getGroup());
    }

    @Test
    void deleteGroup_DefaultGroup() {
        group.setIsDefault(true);

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));

        assertThrows(IllegalArgumentException.class, () ->
                groupService.deleteGroup(1L, "testhost"));
    }

    @Test
    void assignHomestaysToGroup_Success() {
        List<Long> homestayIds = Arrays.asList(10L, 11L);

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));
        when(homestayRepository.findAllByIdIn(homestayIds)).thenReturn(Arrays.asList(homestay1, homestay2));

        groupService.assignHomestaysToGroup(1L, homestayIds, "testhost");

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(homestayRepository).saveAll(captor.capture());

        List<Homestay> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(group, saved.get(0).getGroup());
        assertEquals(group, saved.get(1).getGroup());
    }

    @Test
    void assignHomestaysToGroup_WrongOwner() {
        User otherHost = new User();
        otherHost.setId(2L);
        otherHost.setUsername("otherhost");

        Homestay otherHomestay = new Homestay();
        otherHomestay.setId(20L);
        otherHomestay.setTitle("别人的房源");
        otherHomestay.setOwner(otherHost);

        List<Long> homestayIds = Arrays.asList(20L);

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));
        when(homestayRepository.findAllByIdIn(homestayIds)).thenReturn(Arrays.asList(otherHomestay));

        assertThrows(IllegalArgumentException.class, () ->
                groupService.assignHomestaysToGroup(1L, homestayIds, "testhost"));
    }

    @Test
    void removeHomestaysFromGroup_Success() {
        List<Long> homestayIds = Arrays.asList(10L, 11L);

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(homestayRepository.findAllByIdIn(homestayIds)).thenReturn(Arrays.asList(homestay1, homestay2));

        groupService.removeHomestaysFromGroup(homestayIds, "testhost");

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(homestayRepository).saveAll(captor.capture());

        List<Homestay> saved = captor.getValue();
        assertNull(saved.get(0).getGroup());
        assertNull(saved.get(1).getGroup());
    }

    @Test
    void getHomestaysByGroup_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Homestay> homestayPage = new PageImpl<>(Arrays.asList(homestay1, homestay2));

        when(userRepository.findByUsername("testhost")).thenReturn(Optional.of(host));
        when(groupRepository.findByOwnerIdAndId(1L, 1L)).thenReturn(Optional.of(group));
        when(homestayRepository.findByGroupIdAndOwnerId(1L, 1L, pageable)).thenReturn(homestayPage);

        Page<HomestayDTO> result = groupService.getHomestaysByGroup(1L, "testhost", pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("海景大床房", result.getContent().get(0).getTitle());
    }
}
