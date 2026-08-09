-- =============================================================================
-- 性能压测数据初始化脚本
-- =============================================================================
-- ⚠️  仅供 homestay_perf 库使用！禁止在 dev / prod 库执行！
-- ⚠️  本脚本由 Flyway 跑完迁移后单独执行（不是 Flyway 脚本）
--
-- 执行方式：
--   docker exec -i homestay-mysql-perf mysql -uperf_user -pperf_pwd homestay_perf < init-perf-data.sql
--
-- 数据规模：
--   - 1 位房东 + 1000 个压测用住客
--   - 5000 间房型（横跨多个城市）
--   - 20000 张可用性记录
--   - 10000 张历史订单
-- =============================================================================

USE homestay_perf;

SET FOREIGN_KEY_CHECKS=0;

-- -----------------------------------------------------------------------------
-- 0. 安全护栏：双保险，确认库名
-- -----------------------------------------------------------------------------
SELECT DATABASE() AS current_db;
-- 若上面不是 homestay_perf，Ctrl+C 中止！

-- -----------------------------------------------------------------------------
-- 1. 清空业务表（按依赖倒序）
-- -----------------------------------------------------------------------------
TRUNCATE TABLE order_price_snapshot;
TRUNCATE TABLE orders;
TRUNCATE TABLE homestay_availability;
TRUNCATE TABLE homestay_groups;
TRUNCATE TABLE homestay_amenity;
TRUNCATE TABLE homestay_images;
TRUNCATE TABLE homestays;
TRUNCATE TABLE user_favorites;
TRUNCATE TABLE users;
TRUNCATE TABLE login_log;
TRUNCATE TABLE operation_log;

SET FOREIGN_KEY_CHECKS=1;

-- -----------------------------------------------------------------------------
-- 2. 基础用户：1 个房东 + 1 个 admin + 1000 个住客
-- -----------------------------------------------------------------------------
-- 密码统一为 123456（BCrypt 哈希，强度 10）
-- 由 BCryptPasswordEncoder 生成并验证（旧哈希 $2a$10$N9qo8uLO... 不匹配任何密码）
SET @bcrypt_pwd = '$2a$10$YZcWhCdgQuaipqAyPpKSXuLj.l2xQ5aC5PXj8bXdW9ff3iBW7BV9G';

INSERT INTO users (username, email, password, phone, role, real_name, nickname, created_at, updated_at, enabled)
VALUES
  ('perf_host',     'perf_host@test.local',     @bcrypt_pwd, '13800000001', 'HOST',     '压测房东', '压测房东', NOW(), NOW(), 1),
  ('perf_admin',    'perf_admin@test.local',    @bcrypt_pwd, '13800000002', 'ADMIN',    '压测管理员', '压测管理员', NOW(), NOW(), 1);

-- 1000 个住客（用数字生成器避免大 SQL）
DROP PROCEDURE IF EXISTS seed_guests;
DELIMITER //
CREATE PROCEDURE seed_guests()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 1000 DO
    INSERT INTO users (username, email, password, phone, role, real_name, nickname, created_at, updated_at, enabled)
    VALUES (
      CONCAT('perf_guest_', i),
      CONCAT('perf_guest_', i, '@test.local'),
      @bcrypt_pwd,
      CONCAT('139', LPAD(i, 8, '0')),
      'GUEST',
      CONCAT('压测住客', i),
      CONCAT('user', i),
      NOW(), NOW(), 1
    );
    SET i = i + 1;
  END WHILE;
END //
DELIMITER ;

CALL seed_guests();
DROP PROCEDURE seed_guests;

-- -----------------------------------------------------------------------------
-- 3. 房型：5000 间（5 个城市 × 1000 间）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_homestays;
DELIMITER //
CREATE PROCEDURE seed_homestays()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE city_idx INT;
  DECLARE cities TEXT DEFAULT '北京,上海,杭州,成都,深圳';
  DECLARE city_codes TEXT DEFAULT '110000,310000,330100,510100,440300';
  DECLARE city_name VARCHAR(20);
  DECLARE city_code VARCHAR(10);
  DECLARE city_pos INT;
  DECLARE codes_pos INT;

  WHILE i <= 5000 DO
    SET city_idx = ((i - 1) MOD 5) + 1;
    SET city_name = SUBSTRING_INDEX(SUBSTRING_INDEX(cities, ',', city_idx), ',', -1);
    SET city_code = SUBSTRING_INDEX(SUBSTRING_INDEX(city_codes, ',', city_idx), ',', -1);

    INSERT INTO homestays (
      owner_id, title, type, price, status,
      max_guests, min_nights, max_nights,
      province_text, city_text, district_text, address_detail,
      province_code, city_code, district_code,
      latitude, longitude,
      created_at, updated_at
    )
    SELECT
      (SELECT id FROM users WHERE username='perf_host' LIMIT 1),
      CONCAT('压测房型#', i, '-', city_name),
      'APARTMENT',
      ROUND(100 + RAND() * 900, 2),
      'ACTIVE',
      2 + FLOOR(RAND() * 4),
      1,
      30,
      '测试省',
      city_name,
      CONCAT('测试区', ((i - 1) MOD 10) + 1),
      CONCAT('测试路', i, '号'),
      '110000',
      city_code,
      CONCAT(city_code, '_', LPAD(((i - 1) MOD 10) + 1, 2, '0')),
      30 + RAND() * 10,
      110 + RAND() * 20,
      NOW(), NOW();

    SET i = i + 1;
  END WHILE;
END //
DELIMITER ;

CALL seed_homestays();
DROP PROCEDURE seed_homestays;

-- -----------------------------------------------------------------------------
-- 4. 可用性：每间房未来 60 天 = 300,000 行
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_availability;
DELIMITER //
CREATE PROCEDURE seed_availability()
BEGIN
  DECLARE h_id BIGINT;
  DECLARE d INT;
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR SELECT id FROM homestays;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO h_id;
    IF done THEN LEAVE read_loop; END IF;

    SET d = 0;
    WHILE d < 60 DO
      INSERT INTO homestay_availability (homestay_id, date, status, locked, custom_price, created_at, updated_at)
      VALUES (
        h_id,
        DATE_ADD(CURDATE(), INTERVAL d DAY),
        'AVAILABLE',
        0,
        NULL,
        NOW(), NOW()
      );
      SET d = d + 1;
    END WHILE;
  END LOOP;
  CLOSE cur;
END //
DELIMITER ;

CALL seed_availability();
DROP PROCEDURE seed_availability;

-- -----------------------------------------------------------------------------
-- 5. 历史订单：10000 张（让"我的订单"接口有真实压力）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS seed_orders;
DELIMITER //
CREATE PROCEDURE seed_orders()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE h_id BIGINT;
  DECLARE g_id BIGINT;
  DECLARE nights INT;
  DECLARE checkin DATE;
  DECLARE checkout DATE;
  DECLARE total DECIMAL(10,2);

  WHILE i <= 10000 DO
    SET h_id = (SELECT id FROM homestays ORDER BY RAND() LIMIT 1);
    SET g_id = (SELECT id FROM users WHERE role='GUEST' ORDER BY RAND() LIMIT 1);
    SET nights = 1 + FLOOR(RAND() * 5);
    SET checkin = DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 90) DAY);
    SET checkout = DATE_ADD(checkin, INTERVAL nights DAY);
    SET total = (SELECT price FROM homestays WHERE id=h_id) * nights;

    INSERT INTO orders (
      order_number, homestay_id, guest_id, guest_phone,
      check_in_date, check_out_date, nights, guest_count,
      price, total_amount, status, payment_status, version, created_at, updated_at
    ) VALUES (
      CONCAT('PERF', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(i, 8, '0')),
      h_id, g_id, '13900000000',
      checkin, checkout, nights, 2,
      (SELECT price FROM homestays WHERE id=h_id), total,
      'COMPLETED', 'PAID', 0,
      NOW(), NOW()
    );

    SET i = i + 1;
  END WHILE;
END //
DELIMITER ;

CALL seed_orders();
DROP PROCEDURE seed_orders;

-- -----------------------------------------------------------------------------
-- 6. 统计
-- -----------------------------------------------------------------------------
SELECT 'users'        AS tbl, COUNT(*) AS cnt FROM users
UNION ALL
SELECT 'homestays',          COUNT(*) FROM homestays
UNION ALL
SELECT 'homestay_availability', COUNT(*) FROM homestay_availability
UNION ALL
SELECT 'orders',             COUNT(*) FROM orders;

-- 期望输出：
-- users                  1002
-- homestays              5000
-- homestay_availability  300000
-- orders                 10000
