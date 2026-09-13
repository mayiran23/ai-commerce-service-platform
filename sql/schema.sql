CREATE DATABASE IF NOT EXISTS commerce_service
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE commerce_service;

-- ============ 用户与权限 ============
CREATE TABLE t_user (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  username     VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录名',
  password     VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密',
  nickname     VARCHAR(64),
  phone        VARCHAR(20),
  role         VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/AGENT/ADMIN',
  status       TINYINT      NOT NULL DEFAULT 1,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
) COMMENT '用户表';

-- ============ 商品 ============
CREATE TABLE t_category (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  name     VARCHAR(64) NOT NULL,
  parent_id BIGINT DEFAULT 0
) COMMENT '商品分类';

CREATE TABLE t_product (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL,
  category_id BIGINT,
  price       DECIMAL(10,2) NOT NULL,
  description TEXT,
  image_url   VARCHAR(255),
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_category (category_id)
) COMMENT '商品表';

-- ============ 订单 ============
CREATE TABLE t_order (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no       VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
  user_id        BIGINT NOT NULL,
  status         VARCHAR(20) NOT NULL COMMENT 'PENDING_PAY/PAID/SHIPPED/DELIVERING/RECEIVED/CANCELLED',
  total_amount   DECIMAL(10,2) NOT NULL,
  pay_amount     DECIMAL(10,2) NOT NULL,
  pay_time       DATETIME,
  ship_time      DATETIME,
  receive_time   DATETIME,
  receiver_name  VARCHAR(64),
  receiver_phone VARCHAR(20),
  receiver_addr  VARCHAR(255),
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_status (user_id, status),
  INDEX idx_order_no (order_no)
) COMMENT '订单表';

CREATE TABLE t_order_item (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id       BIGINT NOT NULL,
  order_no       VARCHAR(32) NOT NULL,
  product_id     BIGINT NOT NULL,
  product_name   VARCHAR(128) NOT NULL,
  product_price  DECIMAL(10,2) NOT NULL,
  quantity       INT NOT NULL DEFAULT 1,
  -- 退货资格截止：签收后 7 天
  refund_deadline DATETIME COMMENT '退货资格截止时间',
  INDEX idx_order_no (order_no),
  INDEX idx_order_id (order_id)
) COMMENT '订单明细表';

CREATE TABLE t_order_logistics (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no          VARCHAR(32) NOT NULL,
  track_no          VARCHAR(64),
  current_status    VARCHAR(32) NOT NULL COMMENT '已发货/运输中/派送中/已签收',
  current_node      VARCHAR(128) COMMENT '当前所在节点，如：南京江宁集散中心',
  estimated_arrival DATE,
  update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_no (order_no)
) COMMENT '物流轨迹表';

-- ============ 售后工单 ============
CREATE TABLE t_after_sale (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no      VARCHAR(32) NOT NULL UNIQUE COMMENT '工单号 AS20260912001',
  order_no       VARCHAR(32) NOT NULL,
  order_item_id  BIGINT,
  user_id        BIGINT NOT NULL,
  type           VARCHAR(20) NOT NULL COMMENT 'REFUND退货退款/EXCHANGE换货/REPAIR维修',
  reason         VARCHAR(500),
  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 COMMENT 'PENDING待审核/APPROVED已通过/REFUNDING退款中/COMPLETED已完成/REJECTED已驳回/MANUAL_REVIEW待人工/CANCELLED已撤销',
  -- AI 相关：人机协同的关键字段
  ai_generated   TINYINT NOT NULL DEFAULT 0 COMMENT '是否 AI 创建',
  ai_confidence  DECIMAL(4,3) COMMENT 'AI 置信度 0-1，低于阈值转人工',
  source         VARCHAR(20) DEFAULT 'AI' COMMENT 'AI/WEB',
  handler_id     BIGINT COMMENT '处理人（客服）',
  handle_remark  VARCHAR(500),
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_status (user_id, status),
  INDEX idx_ticket_no (ticket_no),
  INDEX idx_order_no (order_no)
) COMMENT '售后工单表';

CREATE TABLE t_after_sale_flow (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no     VARCHAR(32) NOT NULL,
  from_status   VARCHAR(20),
  to_status     VARCHAR(20) NOT NULL,
  operator_id   BIGINT,
  operator_type VARCHAR(20) NOT NULL COMMENT 'USER用户/AGENT客服/ADMIN管理员/SYSTEM系统/AI',
  remark        VARCHAR(500),
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ticket_no (ticket_no)
) COMMENT '工单流转记录（每状态变更写一条）';

-- ============ 知识库 ============
CREATE TABLE t_kb_doc (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  title       VARCHAR(255) NOT NULL,
  category    VARCHAR(64) COMMENT '退换货政策/物流说明/支付问题',
  file_path   VARCHAR(255),
  status      TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '知识文档表';

CREATE TABLE t_kb_chunk (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id      BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content     TEXT NOT NULL,
  vector_id   VARCHAR(64) COMMENT 'Chroma 中的 id，反向定位用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc (doc_id)
) COMMENT '文档切片表（向量存 Chroma，原文存这里）';

-- ============ 对话 ============
CREATE TABLE t_chat_session (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id  VARCHAR(64) NOT NULL UNIQUE,
  user_id     BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '会话表';

CREATE TABLE t_chat_message (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id   VARCHAR(64) NOT NULL,
  role         VARCHAR(20) NOT NULL COMMENT 'user/assistant/tool',
  intent       VARCHAR(32) COMMENT 'AI 判定的意图',
  content      TEXT NOT NULL,
  tool_calls   JSON COMMENT 'AI 调用了哪些工具、参数、结果摘要',
  confidence   DECIMAL(4,3),
  latency_ms   INT COMMENT '响应耗时，评估用',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) COMMENT '对话消息表（每条都记，评估与复盘用）';

-- ============ 离线评估 ============
CREATE TABLE t_eval_case (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  query             VARCHAR(500) NOT NULL COMMENT '用户问题',
  expected_intent   VARCHAR(32) NOT NULL,
  expected_tool     VARCHAR(64) COMMENT '期望调用的工具，可为空',
  expected_keywords VARCHAR(255) COMMENT '回答中应包含的关键词',
  category          VARCHAR(32) COMMENT '用于分组统计'
) COMMENT '评估测试集';

CREATE TABLE t_eval_result (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no       VARCHAR(32) NOT NULL COMMENT '一批跑批的编号，用于对比优化前后',
  case_id        BIGINT NOT NULL,
  actual_intent  VARCHAR(32),
  actual_tool    VARCHAR(64),
  intent_correct TINYINT,
  tool_correct   TINYINT,
  answer         TEXT,
  latency_ms     INT,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_batch (batch_no)
) COMMENT '评估结果表';
