package com.homestay3.homestaybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminIdentityResponse {
    private final Long id;
    private final String username;
    private final String role;
}
