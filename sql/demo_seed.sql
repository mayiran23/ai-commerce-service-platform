-- ============================================================
-- 演示数据准备脚本 · user0001（谢雨欣）
-- ============================================================
-- 用途：造一笔「在退货期内 + 无工单」的订单，用于演示 AI 顺畅退货流程。
--       之所以需要它：种子数据里所有 RECEIVED 订单的 refund_deadline 都已过期，
--       AI 只能回答"超期转人工"，演示不出主流程。
--
-- 设计：订单 SO2026092200501 / 全棉四件套（product_id 23）
--       时间线：下单 → 发货 → 签收 → 退货截止（签收 + 7 天）
--       商品名与 user0001 已有商品不重复，避免 AI 搜索时产生歧义
--
-- 执行时机：docker-compose 首次启动时自动跑（挂到 mysql 的 initdb 目录）
--           也可以手动跑：
--           mysql -h127.0.0.1 -uroot -p<密码> --default-character-set=utf8mb4 commerce_service < demo_seed.sql
--
-- ⚠️ 幂等：可重复执行（先按订单号清理，再插入）
--
-- 🔴 时间线是**相对今天动态算的**，不是写死的日期。
--    最初写死了 2026-09-22 ~ 2026-10-02，结果到了 10/8 这份数据就过期了 ——
--    AI 会正确回答"已超过 7 天无理由退货期"，但演示不出"顺畅退货"那一幕。
--    改成相对时间后，无论哪天跑，退货截止日永远在 5 天之后。
-- ============================================================

USE commerce_service;

-- ⚠️ 这一行必须有！
-- 容器里的 mysql 客户端在 LANG=C 环境下默认按 latin1 读脚本，
-- 不写这行中文会被二次编码存成乱码（表现为 å…¨æ£‰å››ä»¶å¥—），
-- 而且不会报任何错，只是 AI 搜"四件套"永远搜不到。
SET NAMES utf8mb4;

-- ---------- 0. 相对时间变量 ----------
-- 签收 = 今天往前 2 天；退货截止 = 签收 + 7 天 = 今天往后 5 天
SET @pay_time  := DATE_SUB(NOW(), INTERVAL 4 DAY);
SET @ship_time := DATE_SUB(NOW(), INTERVAL 3 DAY);
SET @recv_time := DATE_SUB(NOW(), INTERVAL 2 DAY);
SET @deadline  := DATE_ADD(@recv_time, INTERVAL 7 DAY);

-- ---------- 1. 清理旧数据（幂等，注意顺序：子表 → 主表） ----------
DELETE FROM t_order_logistics WHERE order_no = 'SO2026092200501';
DELETE FROM t_after_sale_flow
 WHERE ticket_no IN (SELECT ticket_no FROM (SELECT ticket_no FROM t_after_sale WHERE order_no = 'SO2026092200501') tmp);
DELETE FROM t_after_sale    WHERE order_no = 'SO2026092200501';
DELETE FROM t_order_item    WHERE order_no = 'SO2026092200501';
DELETE FROM t_order         WHERE order_no = 'SO2026092200501';

-- ---------- 2. 订单主表 ----------
INSERT INTO t_order
  (order_no, user_id, status, total_amount, pay_amount,
   pay_time, ship_time, receive_time,
   receiver_name, receiver_phone, receiver_addr, create_time, update_time)
VALUES
  ('SO2026092200501', 1, 'RECEIVED', 429.90, 429.90,
   @pay_time, @ship_time, @recv_time,
   '赵俊杰', '13596069140', '北京市朝阳区望京东路 6 号',
   @pay_time, @recv_time);

-- ---------- 3. 订单商品项（refund_deadline = 签收时间 + 7 天） ----------
INSERT INTO t_order_item
  (order_id, order_no, product_id, product_name, product_price, quantity, refund_deadline)
SELECT id, order_no, 23, '全棉四件套', 429.90, 1, @deadline
  FROM t_order WHERE order_no = 'SO2026092200501';

-- ---------- 4. 物流 ----------
INSERT INTO t_order_logistics
  (order_no, track_no, current_status, current_node, estimated_arrival, update_time)
VALUES
  ('SO2026092200501', 'SF102938475610', '已签收', '已签收，签收人：本人',
   DATE_SUB(CURDATE(), INTERVAL 2 DAY), @recv_time);

-- ---------- 5. 自检：期望 SO2026092200501 那行 days_left > 0 且 has_ticket = 0 ----------
SELECT o.order_no, o.status, oi.id AS item_id, oi.product_id, oi.product_name,
       DATE_FORMAT(oi.refund_deadline, '%Y-%m-%d %H:%i') AS refund_deadline,
       DATEDIFF(oi.refund_deadline, NOW()) AS days_left,
       (SELECT COUNT(*) FROM t_after_sale a WHERE a.order_item_id = oi.id) AS has_ticket
  FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id
 WHERE o.user_id = 1
 ORDER BY o.id;


-- ============================================================
-- 附：演示后的清理（想恢复现场时执行）
-- ============================================================
-- DELETE FROM t_order_logistics WHERE order_no = 'SO2026092200501';
-- DELETE FROM t_after_sale_flow
--  WHERE ticket_no IN (SELECT ticket_no FROM (SELECT ticket_no FROM t_after_sale WHERE order_no = 'SO2026092200501') tmp);
-- DELETE FROM t_after_sale WHERE order_no = 'SO2026092200501';
-- DELETE FROM t_order_item WHERE order_no = 'SO2026092200501';
-- DELETE FROM t_order      WHERE order_no = 'SO2026092200501';


-- ============================================================
-- 附：演示台词（配合这段数据）
-- ============================================================
-- 场景一 · 在退货期内（演示主流程）
--   用户：我买的四件套洗了一次就严重掉色，能退吗
--   AI  ：search_user_orders → check_refund_eligible(eligible=true)
--         → "这个还在 7 天无理由退货期内，需要我帮您提交申请吗？"
--   用户：确认，帮我提交
--   AI  ：create_after_sale → 工单 AS2026xxxxxxxx，状态 待处理(PENDING)
--
-- 场景二 · 已超期（演示人机协同兜底）
--   用户：我买的口红到货时膏体就是断的（对应 item 249 / 355，均超期）
--   AI  ：check_refund_eligible(eligible=false, suggestManualReview=true)
--         → "已超过 7 天无理由期，但这是质量问题，我帮您提交人工审核"
--   AI  ：create_after_sale → 工单状态 待人工(MANUAL_REVIEW)
