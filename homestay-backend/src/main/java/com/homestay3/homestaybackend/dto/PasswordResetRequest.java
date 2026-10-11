package com.homestay3.homestaybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {
    
    @NotBlank(message = "令牌不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "重置链接无效，请重新申请")
    @ToString.Exclude
    private String token;
    
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6个字符")
    @ToString.Exclude
    private String newPassword;
} 