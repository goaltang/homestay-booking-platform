-- 显式开发/演示数据；只由 tools/database/import_data.py demo 调用。
-- 内容源自旧 db/seed_data.sql；不清表、不固定主键；房型使用 V30 标准代码。
-- 演示账号 host1、host2、user1 密码均为 123456，仅用于隔离的开发/演示环境。
-- MySQL 8 / InnoDB；调用方已开启 STRICT_ALL_TABLES。
-- 负数无法写入 unsigned 列，业务表非空时 mysql 批处理停止并断开连接，回滚事务。
CREATE TEMPORARY TABLE demo_requires_empty_business_tables (
    empty_only TINYINT UNSIGNED NOT NULL
) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO demo_requires_empty_business_tables (empty_only)
SELECT -1 WHERE
    EXISTS (SELECT 1 FROM users WHERE role IS NULL OR role <> 'ROLE_ADMIN') OR
    EXISTS (SELECT 1 FROM homestays) OR
    EXISTS (SELECT 1 FROM orders) OR
    EXISTS (SELECT 1 FROM homestay_images) OR
    EXISTS (SELECT 1 FROM homestay_amenity);

INSERT INTO users (username, email, password, role, enabled, created_at, updated_at, verification_status, real_name)
VALUES ('host1', 'host1@test.com', '$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6', 'ROLE_HOST', 1, NOW(), NOW(), 'VERIFIED', '房东一号');
SET @demo_user_1 = LAST_INSERT_ID();

INSERT INTO users (username, email, password, role, enabled, created_at, updated_at, verification_status, real_name)
VALUES ('host2', 'host2@test.com', '$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6', 'ROLE_HOST', 1, NOW(), NOW(), 'VERIFIED', '房东二号');
SET @demo_user_2 = LAST_INSERT_ID();

INSERT INTO users (username, email, password, role, enabled, created_at, updated_at, verification_status, real_name)
VALUES ('user1', 'user1@test.com', '$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6', 'ROLE_USER', 1, NOW(), NOW(), 'VERIFIED', '普通用户一号');
SET @demo_user_3 = LAST_INSERT_ID();

INSERT INTO homestays (title, type, price, status, max_guests, min_nights, province_code, city_code, district_code, address_detail, description, owner_id, auto_confirm, cover_image, featured, created_at, updated_at)
VALUES ('杭州西湖·断桥残雪湖景房', 'ENTIRE', 688.00, 'ACTIVE', 2, 1, '330000', '330100', '330106', '西湖区北山街1号', '推窗即见西湖，步行可达断桥', @demo_user_1, 1, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800', 1, NOW(), NOW());
SET @demo_home_1 = LAST_INSERT_ID();

INSERT INTO homestays (title, type, price, status, max_guests, min_nights, province_code, city_code, district_code, address_detail, description, owner_id, auto_confirm, cover_image, featured, created_at, updated_at)
VALUES ('成都太古里·繁华里Loft', 'LOFT', 458.00, 'ACTIVE', 4, 1, '510000', '510100', '510104', '锦江区中纱帽街', '现代简约风格，俯瞰太古里夜景', @demo_user_2, 1, 'https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800', 1, NOW(), NOW());
SET @demo_home_2 = LAST_INSERT_ID();

INSERT INTO homestays (title, type, price, status, max_guests, min_nights, province_code, city_code, district_code, address_detail, description, owner_id, auto_confirm, cover_image, featured, created_at, updated_at)
VALUES ('大理古城·苍山下小院', 'COURTYARD', 299.00, 'ACTIVE', 3, 2, '530000', '532900', '532901', '大理市古城南门', '阳光充足的小院，可以看苍山雪', @demo_user_1, 1, 'https://images.unsplash.com/photo-1505691938895-1758d7dde511?w=800', 0, NOW(), NOW());
SET @demo_home_3 = LAST_INSERT_ID();

INSERT INTO homestays (title, type, price, status, max_guests, min_nights, province_code, city_code, district_code, address_detail, description, owner_id, auto_confirm, cover_image, featured, created_at, updated_at)
VALUES ('上海武康路·老洋房露台房', 'VILLA', 888.00, 'ACTIVE', 2, 1, '310000', '310100', '310104', '徐汇区武康路100号', '体验旧上海的风情，带超大私家露台', @demo_user_1, 1, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800', 1, NOW(), NOW());
SET @demo_home_4 = LAST_INSERT_ID();

INSERT INTO homestays (title, type, price, status, max_guests, min_nights, province_code, city_code, district_code, address_detail, description, owner_id, auto_confirm, cover_image, featured, created_at, updated_at)
VALUES ('北京三里屯·潮流艺术公寓', 'ENTIRE', 750.00, 'ACTIVE', 2, 1, '110000', '110100', '110105', '朝阳区工体北路', '波普艺术装修，极速Wi-Fi', @demo_user_2, 1, 'https://images.unsplash.com/photo-1536376074432-cd42da8544af?w=800', 0, NOW(), NOW());
SET @demo_home_5 = LAST_INSERT_ID();

INSERT INTO homestay_images (homestay_id, image_url)
VALUES (@demo_home_1, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800');
INSERT INTO homestay_images (homestay_id, image_url)
VALUES (@demo_home_2, 'https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800');
INSERT INTO homestay_images (homestay_id, image_url)
VALUES (@demo_home_3, 'https://images.unsplash.com/photo-1505691938895-1758d7dde511?w=800');
INSERT INTO homestay_images (homestay_id, image_url)
VALUES (@demo_home_4, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800');
INSERT INTO homestay_images (homestay_id, image_url)
VALUES (@demo_home_5, 'https://images.unsplash.com/photo-1536376074432-cd42da8544af?w=800');

COMMIT;
