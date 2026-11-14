package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.entity.Admin;

public interface AdminService {
    Admin getAdminByUsername(String username);
    void resetAdminPassword(String username, String newPassword);
} 