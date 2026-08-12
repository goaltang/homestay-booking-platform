package com.homestay3.homestaybackend.dto;

import com.homestay3.homestaybackend.model.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationDTO {
    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private String idCard;
    private String idCardFront;
    private String idCardBack;
    private VerificationStatus status;
    private LocalDateTime submitTime;
    private LocalDateTime reviewTime;
    private String reviewNote;
} 