-- 演示数据脱敏；只由 tools/database/export_demo.py 在导出用的临时库中执行，不得对业务库运行。
-- 执行后由 export_demo.py 逐项核对结果，任何一项不满足都会中止导出。

-- 1. 管理员不进入公开演示数据；导入方用 import_data.py admin 自行创建。旧 admin 表一并清空。
DELETE FROM users WHERE role = 'ROLE_ADMIN';
DELETE FROM admin;

-- 2. 账号凭据与身份信息：统一演示密码 123456，清除身份证与重置令牌。
--    手机号改为不存在的 100 号段，避免与真实号码重合；邮箱统一到 example.com。
UPDATE users SET
    password = '$2a$10$oMeaCXPR2aItuzgcxsCEhOE1GWjakAlO87xZyyTjmfzFaTrZpDsD6',
    phone = CASE WHEN phone IS NULL OR phone = '' THEN phone ELSE CONCAT('100', LPAD(id, 8, '0')) END,
    email = CONCAT(username, '@example.com'),
    id_card = NULL,
    id_card_front = NULL,
    id_card_back = NULL,
    reset_token = NULL,
    reset_token_expiry = NULL;

-- 3. 订单与入住记录中的联系方式、门锁密码。
UPDATE orders SET guest_phone = CONCAT('100', LPAD(guest_id, 8, '0'))
WHERE guest_phone IS NOT NULL AND guest_phone <> '';
UPDATE orders SET door_password = NULL WHERE door_password IS NOT NULL;
UPDATE check_in_records SET door_password = LPAD(MOD(id * 7919, 1000000), 6, '0')
WHERE door_password IS NOT NULL;

-- 4. 访问轨迹：登录与操作日志不导出；审核日志去掉 IP 与 UA。
DELETE FROM login_log;
DELETE FROM operation_log;
UPDATE homestay_audit_logs SET ip_address = NULL, user_agent = NULL;
