# 接口文档（前后端契约）

> 智联商城 · 智能客服 + 售后工单协同平台
> 版本 v1 · 最后更新 2026-09-14

---

## 这份文档怎么用

**顺序建议**：先读第 1 节（全局约定）、第 2 节（枚举字典）——这两节是所有接口的共同规则，读一遍就够了。
然后按第 11 节给出的**开发顺序**，一次挑一个接口去实现。每实现完一个，用文档里的 `curl` 命令验一次。

**三条硬规则**（违反了一定会返工）：

1. **返回体永远包一层 `Result`**，不要出现"这个接口直接返回数组、那个接口包 Result"。
2. **字段名一律驼峰**（`orderNo`），数据库下划线（`order_no`）由 MyBatis 的
   `map-underscore-to-camel-case=true` 自动转。Java 实体属性名永远不用下划线。
3. **前端传的 `userId` 一律不可信**。后端从 JWT 里解析，前端传什么都不看。
   这条是安全设计，也是面试可以讲的点。

**前端已经写好了**，在 `frontend/` 目录。它对接口的假设全部写在本文件里 —— 也就是说，
**只要后端按这份文档实现，前端改一个布尔值就能接上**（见第 12 节）。

---

## 1. 全局约定

### 1.1 地址与路由

| 环境 | 前端地址 | 后端地址 | 说明 |
|---|---|---|---|
| 本地开发 | `http://localhost:5500` | `http://localhost:8080` | 前端静态服务，后端 Spring Boot |
| 部署 | `http://<服务器>/` | 内部 `:8080` | nginx 托管静态文件，并把 `/api` 反代到后端 |

nginx 的配置见 `frontend/nginx.conf.example`。**走 nginx 是推荐做法**：
前端用相对路径 `/api/orders` 请求，同源，没有跨域问题（就是苍穹外卖那套）。

如果不想配 nginx、直接开两个端口，那后端要加 CORS 配置，前端把 `API_BASE` 填成
`http://localhost:8080`。

### 1.2 统一返回体

**所有接口**（包括内部接口）都返回这个结构：

```json
{
  "code": 1,
  "msg": "",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | **`1` = 成功，`0` = 业务失败**（注意不是 200） |
| `msg` | string | 失败时的原因，成功时为空字符串 |
| `data` | 泛型 | 成功时的数据；失败时为 `null` |

对应 Java 侧**已经写好的** `result/Result.java`，不用重写：

```java
Result.success(data)      // code=1
Result.error("订单不存在")  // code=0
```

> 为什么不用 200/500 当业务码？因为苍穹外卖就是这么做的，你已经熟悉了；
> 而且 HTTP 状态码表达的是"传输层"结果，业务结果用 `code` 表达，两者分开更清楚。
> 面试如果被问，可以答："HTTP 状态码管传输，业务 code 管结果，混在一起会让
> 网关、重试策略、监控告警没法区分'服务挂了'和'订单不存在'。"

### 1.3 分页约定

**所有列表接口**统一用这三个参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | `1` | 第几页，**从 1 开始** |
| `limit` | int | 否 | `10` | 每页条数 |
| `keyword` | string | 否 | | 模糊搜索关键字 |

**统一返回 `PageResult`**（Java 侧也已经写好了 `result/PageResult.java`）：

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 57,
    "records": [ ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | **符合条件的总条数**（不是当前页条数），前端靠它算总页数 |
| `records` | array | 当前页数据 |

`limit` 上限建议卡在 100，防止有人传 `limit=999999` 把库拖死。

### 1.4 字段格式

| 类型 | 格式 | 例子 |
|---|---|---|
| 时间 | 字符串 `yyyy-MM-dd HH:mm:ss` | `"2026-09-14 08:54:11"` |
| 日期 | 字符串 `yyyy-MM-dd` | `"2026-09-12"` |
| 金额 | **数字**，两位小数 | `583.86` |
| 布尔 | 真布尔 | `true` / `false` |

**时间不要返回时间戳数字**，也不要在末尾带 `Z`。用
`spring.jackson.date-format` + `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")` 或全局配置
`LocalDateTime` 序列化器。前端直接拿字符串显示，不做时区转换。

**金额用 `BigDecimal`，不要用 `double`**。JSON 里是数字类型，前端按数字渲染。

### 1.5 鉴权

**外部接口**（`/api/**`，前端调用）用 JWT：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

后端用一个拦截器：
1. 取 `Authorization` 头，去掉 `Bearer ` 前缀
2. 校验签名和过期时间，解析出 `userId`、`role`
3. 放进 `ThreadLocal`（`BaseContext.setCurrentId(...)`），请求结束后 `remove()`
4. 校验失败 → HTTP 401

**放行名单**（不需要登录）：`POST /api/auth/login`。

**内部接口**（`/internal/**`，Python AI 服务调用）用固定 token：

```
X-Internal-Token: <配置文件里的密钥>
```

对应一个 `@InternalOnly` 拦截器，校验这个头。**不要让 `/internal/**` 走 JWT**——
AI 服务没有用户身份，它拿的是一个受限的服务身份。

### 1.6 HTTP 状态码

| 场景 | HTTP | body |
|---|---|---|
| 成功（包括业务上"查不到"） | 200 | `{"code":1,...}` 或 `{"code":0,"msg":"..."}` |
| 未登录 / token 过期 | 401 | `{"code":401,"msg":"未登录或登录已过期"}` |
| 已登录但无权限（如 USER 查别人的单） | 403 | `{"code":403,"msg":"无权访问"}` |
| 资源不存在 | 200 + `code=0` | `{"code":0,"msg":"订单不存在"}` |

> 注意最后一行：**"订单不存在"用 200 + code=0**，不用 404。
> 原因：越权测试时，"查别人的单" 和 "查不存在的单" 应该返回**一样**的结果，
> 否则攻击者能通过"404 还是 403"反推出这个订单号是否存在。这是安全细节，面试可以讲。

### 1.7 RBAC 权限规则

| 接口 | USER | AGENT | ADMIN |
|---|---|---|---|
| `GET /api/orders` | 只能看自己的 | 全部 | 全部 |
| `GET /api/orders/{orderNo}` | 只能是自己的，否则"订单不存在" | 全部 | 全部 |
| `GET /api/after-sales` | 只能看自己的 | 全部 | 全部 |
| `POST /api/after-sales/{no}/transition` | ❌ | ✅ | ✅ |
| `POST /api/after-sales/{no}/cancel` | 只有自己的 `PENDING` 单 | ❌ | ✅ |
| `GET /api/stats/orders` | 自己的 | 全部 | 全部 |

**实现要点**：查询永远带上 `user_id` 条件，不要"先查出来再判断"，那样容易漏。

```java
// 非管理员只能看自己的 —— 条件加在 SQL 里，不是加在 Java 的 if 里
if (!"ADMIN".equals(role) && !"AGENT".equals(role)) {
    query.eq(Order::getUserId, BaseContext.getCurrentId());
}
```

---

## 2. 枚举字典

**这些值前后端必须完全一致**，前端在 `frontend/assets/ui.js` 的 `SM` 对象里定义了一份。

### 2.1 订单状态 `t_order.status`

| 值 | 中文 | 前端徽标颜色 |
|---|---|---|
| `PENDING_PAY` | 待付款 | 灰 |
| `PAID` | 已付款 | 蓝 |
| `SHIPPED` | 已发货 | 蓝 |
| `DELIVERING` | 运输中 | 蓝 |
| `RECEIVED` | 已签收 | 绿 |
| `CANCELLED` | 已取消 | 灰 |

**后端必须同时返回 `status`（枚举值）和 `statusText`（中文）**，前端两个都用：
`status` 用来判断、筛选，`statusText` 用来显示。

### 2.2 工单状态 `t_after_sale.status`

| 值 | 中文 | 含义 |
|---|---|---|
| `PENDING` | 待审核 | 刚创建，等客服处理 |
| `MANUAL_REVIEW` | 待人工 | **AI 置信度低于阈值时直接进这个状态** |
| `APPROVED` | 已通过 | 客服审核通过 |
| `REFUNDING` | 退款中 | 已发起退款/退货流程 |
| `COMPLETED` | 已完成 | 终态 |
| `REJECTED` | 已驳回 | 终态 |
| `CANCELLED` | 已撤销 | 终态（用户或客服撤销） |

### 2.3 工单类型 `t_after_sale.type`

| 值 | 中文 |
|---|---|
| `REFUND` | 退货退款 |
| `EXCHANGE` | 换货 |
| `REPAIR` | 维修 |

### 2.4 其他

| 字典 | 值 |
|---|---|
| 工单来源 `source` | `AI`（AI 创建）/ `WEB`（用户自助） |
| 流转操作人 `operatorType` | `USER` / `AGENT` / `ADMIN` / `SYSTEM` / `AI` |
| AI 意图 `intent` | `ORDER_QUERY` 查订单物流 / `AFTER_SALE_APPLY` 申请售后 / `POLICY_QA` 政策咨询 / `OTHER` 其他 |

### 2.5 退货资格 `eligibility`（前端筛选参数用）

| 值 | 含义 | 判定 |
|---|---|---|
| `eligible` | 有资格 | 最紧急那件的 `refund_deadline > NOW() + 24h` |
| `soon` | 即将到期 | `NOW() < refund_deadline <= NOW() + 24h` |
| `expired` | 已超期 | `refund_deadline <= NOW()` |

> **"最紧急那件"**：一张订单可能有多件商品，各自有退货截止时间。页面显示和筛选
> 都以"截止时间最近的那件"为准，这样客服一眼看到最该先处理的。
> 这个规则前端在 `worstEligBadge()` 里实现了一遍，后端要在 SQL 里再实现一遍 —— 
> **后端是权威，前端只是显示**。

---

## 3. 接口清单（总表）

前端已经把这 13 个外部接口全部用上了，加 5 个内部接口。

| # | 方法 | 路径 | 用途 | 权限 | 对应页面 |
|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 登录换 JWT | 公开 | login.html |
| 2 | GET | `/api/auth/me` | 当前登录用户 | 登录即可 | 全部 |
| 3 | GET | `/api/orders` | 订单列表（分页/筛选） | 登录即可 | orders.html |
| 4 | GET | `/api/orders/{orderNo}` | 订单详情（含明细+物流） | 登录即可 | orders.html 抽屉 |
| 5 | GET | `/api/stats/orders` | 各状态订单数 | 登录即可 | orders.html KPI |
| 6 | GET | `/api/after-sales` | 工单列表（含状态统计） | 登录即可 | tickets.html |
| 7 | GET | `/api/after-sales/{ticketNo}` | 工单详情（含流转记录） | 登录即可 | tickets.html 抽屉 |
| 8 | POST | `/api/after-sales` | 用户自助建工单 | USER | orders.html 按钮 |
| 9 | POST | `/api/after-sales/{ticketNo}/transition` | 状态流转 | AGENT/ADMIN | tickets.html 按钮 |
| 10 | GET | `/api/chat/sessions` | 会话列表 | 登录即可 | chat.html 左栏 |
| 11 | POST | `/api/chat/sessions` | 新建会话 | 登录即可 | chat.html 新建 |
| 12 | GET | `/api/chat/history` | 某会话历史消息 | 登录即可 | chat.html |
| 13 | POST | `/api/chat` | **对话入口** | 登录即可 | chat.html 输入框 |
| 14 | GET | `/internal/orders/{orderNo}` | 查订单（含资格） | 内部 token | Python 调 |
| 15 | GET | `/internal/orders/search` | 搜用户订单 | 内部 token | Python 调 |
| 16 | POST | `/internal/after-sale/check-eligible` | 校验退货资格 | 内部 token | Python 调 |
| 17 | POST | `/internal/after-sale/create` | AI 建工单（幂等） | 内部 token | Python 调 |
| 18 | POST | `/internal/after-sale/search` | 查工单 | 内部 token | Python 调 |

---

## 4. 认证

### 4.1 `POST /api/auth/login`

**请求**

```json
{ "username": "user0108", "password": "123456" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | 是 | 登录名，如 `user0001` |
| `password` | string | 是 | 明文密码，后端用 BCrypt 比对 |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEwOCwicm9sZSI6IkFHRU5UIn0.xxx",
    "user": {
      "id": 108,
      "username": "user0108",
      "nickname": "郭思远",
      "phone": "13000000000",
      "role": "AGENT",
      "roleText": "客服"
    }
  }
}
```

**实现要点**

- 密码在库里是 **BCrypt 密文**（造数据脚本写入的，明文统一是 `123456`）。
  用 `BCrypt.checkpw(明文, 密文)` 比对，**不要用 `equals`**，也不要自己写 MD5。
- JWT 里至少放 `userId` 和 `role`，有效期 24 小时。签名密钥写在
  `application.properties`，**不要提交到 git**（你的 `.gitignore` 里已经忽略了）。
- 密码错误时统一返回 `{"code":0,"msg":"用户名或密码错误"}`，
  **不要区分"用户不存在"和"密码错误"**，否则会泄露哪些用户名存在。

**验一下**

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user0108","password":"123456"}'
```

### 4.2 `GET /api/auth/me`

返回当前 token 对应的用户，前端刷新页面时用它恢复登录态。

**响应** `data` 同上方的 `user` 对象。

---

## 5. 订单

### 5.1 `GET /api/orders`

**订单列表**。前端订单管理页的主接口。

**请求参数**（全部是 query string）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | `1` | 页码 |
| `limit` | int | 否 | `10` | 每页条数 |
| `keyword` | string | 否 | | 匹配 **订单号 / 用户昵称 / 商品名**（三个字段任一命中） |
| `status` | string | 否 | | 订单状态，见 2.1 |
| `userId` | long | 否 | | 按用户筛。**USER 角色传了也无效**，后端强制用自己的 id |
| `eligibility` | string | 否 | | `eligible` / `soon` / `expired`，见 2.5 |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 57,
    "records": [
      {
        "orderNo": "SO2026090900353",
        "userId": 1,
        "userNickname": "谢雨欣",
        "userPhone": "13094475781",
        "status": "RECEIVED",
        "statusText": "已签收",
        "totalAmount": 583.86,
        "payAmount": 583.86,
        "payTime": "2026-09-14 09:12:00",
        "shipTime": "2026-09-14 15:30:00",
        "receiveTime": "2026-09-14 20:54:11",
        "receiverName": "徐雅静",
        "receiverPhone": "13005697472",
        "receiverAddr": "广东省深圳市南山区科苑南路 2666 号",
        "createTime": "2026-09-14 08:54:11",
        "items": [
          {
            "productId": 24,
            "productName": "折叠收纳箱",
            "price": 86.98,
            "quantity": 2,
            "refundDeadline": "2026-09-21 20:54:11",
            "refundEligible": true
          },
          {
            "productId": 43,
            "productName": "丝绒口红套装",
            "price": 204.95,
            "quantity": 2,
            "refundDeadline": "2026-09-21 20:54:11",
            "refundEligible": true
          }
        ],
        "logistics": {
          "trackNo": "SF062649192963",
          "currentStatus": "已签收",
          "currentNode": "已签收，签收人：本人",
          "estimatedArrival": "2026-09-12"
        }
      }
    ]
  }
}
```

**字段说明**

| 字段 | 说明 |
|---|---|
| `refundEligible` | **由后端计算**：`refund_deadline > NOW()`。AI 和前端都不算这个规则 |
| `logistics` | 未发货的订单返回 `null`，前端显示"暂无物流信息" |
| `items` | 列表接口也要返回，前端要显示商品摘要和退货资格；量不大，直接 join 查 |

**关于 `eligibility` 筛选的 SQL 提示**

"最紧急的那件商品"用子查询取最小 `refund_deadline`：

```sql
-- expired 的写法
WHERE EXISTS (
  SELECT 1 FROM t_order_item i
  WHERE i.order_no = o.order_no
    AND i.refund_deadline <= NOW()
)

-- soon 的写法
WHERE EXISTS (
  SELECT 1 FROM t_order_item i
  WHERE i.order_no = o.order_no
    AND i.refund_deadline > NOW()
    AND i.refund_deadline <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
)
```

**验一下**

```bash
curl -s "http://localhost:8080/api/orders?page=1&limit=3&status=RECEIVED" -H "Authorization: Bearer <token>"
curl -s "http://localhost:8080/api/orders?eligibility=soon" -H "Authorization: Bearer <token>"
```

### 5.2 `GET /api/orders/{orderNo}`

**订单详情**，比列表多一个用途：给你自己写 `refundEligible` 判断做自测。

**路径参数**：`orderNo` —— 订单号，如 `SO2026090900353`（**不是 id**）

**响应**：`data` 是单个订单对象，结构同 5.1 的 `records` 元素。

**关键要求**

1. 越权检查：`order.userId != 当前用户id` 且角色是 `USER` → 返回 `{"code":0,"msg":"订单不存在"}`，
   **不要返回 403**（原因见 1.6）。
2. `refundEligible` 必须逐件商品算，不能整单算一个。
   你的库里故意造了 88 条"卡在 7 天整"的边界数据，**写完之后拿它们自测**。

### 5.3 `GET /api/stats/orders`

订单页顶部的统计卡片。

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 57,
    "byStatus": {
      "PENDING_PAY": 8,
      "PAID": 8,
      "SHIPPED": 8,
      "DELIVERING": 8,
      "RECEIVED": 25
    }
  }
}
```

**实现要点**：用 `GROUP BY status` 一次查出来，不要写 6 个 `count(*)`：

```sql
SELECT status, COUNT(*) AS n FROM t_order
[WHERE user_id = ?]     -- 非 AGENT/ADMIN 时加这个条件
GROUP BY status
```

---

## 6. 售后工单

### 6.1 `GET /api/after-sales`

**工单列表**。除了分页数据，还要返回**各状态的统计数**（页面顶部四个卡片）。

**请求参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | `1` | 页码 |
| `limit` | int | 否 | `10` | 每页条数 |
| `keyword` | string | 否 | | 匹配 工单号 / 订单号 / 商品名 |
| `status` | string | 否 | | 工单状态，见 2.2 |
| `source` | string | 否 | | `AI` / `WEB` |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 8,
    "records": [
      {
        "ticketNo": "AS20260914001",
        "orderNo": "SO2026090900353",
        "productId": 43,
        "productName": "丝绒口红套装",
        "userId": 1,
        "userName": "谢雨欣",
        "type": "REFUND",
        "reason": "口红颜色和商品图差距太大，只拆了一支，想退货",
        "status": "MANUAL_REVIEW",
        "statusText": "待人工",
        "aiGenerated": true,
        "aiConfidence": 0.58,
        "source": "AI",
        "handlerId": 108,
        "handlerName": "郭思远",
        "handleRemark": null,
        "createTime": "2026-09-14 15:30:00",
        "updateTime": "2026-09-14 15:30:00"
      }
    ],
    "stats": {
      "PENDING": 2,
      "MANUAL_REVIEW": 1,
      "APPROVED": 1,
      "REFUNDING": 1,
      "COMPLETED": 1,
      "REJECTED": 1,
      "CANCELLED": 1
    }
  }
}
```

**注意**

- `stats` 是**不受 `status` 筛选影响**的全量统计（否则点了"待审核"之后，
  其他卡片的数字会全变 0，体验很怪）。要单独查一次。
- `aiConfidence` 是 `0~1` 的小数（`DECIMAL(4,3)`），不是百分数。前端自己乘 100 显示。
- `aiGenerated` 在库里是 `TINYINT`，**要转成 JSON 布尔**，前端判断 `if (t.aiGenerated)`。
- 非管理员/客服只能看到自己的工单（`user_id` 条件）。

### 6.2 `GET /api/after-sales/{ticketNo}`

**工单详情**，比列表多两样：`order`（关联订单快照）和 `flows`（流转记录）。

**路径参数**：`ticketNo` —— 工单号，如 `AS20260914001`

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "ticketNo": "AS20260914003",
    "orderNo": "SO2026090400190",
    "productName": "跑步运动腰包",
    "userId": 91,
    "userName": "刘宇轩",
    "type": "REFUND",
    "reason": "腰包拉链拉不动，收到就是坏的",
    "status": "REFUNDING",
    "statusText": "退款中",
    "aiGenerated": true,
    "aiConfidence": 0.91,
    "source": "AI",
    "handlerName": "韩丽娟",
    "handleRemark": "已确认为质量问题，运费我方承担",
    "createTime": "2026-09-13 13:00:00",
    "updateTime": "2026-09-14 13:00:00",
    "order": {
      "orderNo": "SO2026090400190",
      "status": "RECEIVED",
      "statusText": "已签收",
      "userId": 91,
      "userNickname": "刘宇轩",
      "payAmount": 399.36,
      "receiveTime": "2026-09-08 01:56:10",
      "items": [
        {
          "productId": 36,
          "productName": "跑步运动腰包",
          "price": 133.12,
          "quantity": 3,
          "refundDeadline": "2026-09-15 01:56:10",
          "refundEligible": true
        }
      ]
    },
    "flows": [
      {
        "fromStatus": null,
        "toStatus": "PENDING",
        "operatorType": "AI",
        "operatorName": "智能客服",
        "remark": "校验通过：签收 6 天，在 7 天无理由期内",
        "createTime": "2026-09-13 13:00:00"
      },
      {
        "fromStatus": "PENDING",
        "toStatus": "APPROVED",
        "operatorType": "AGENT",
        "operatorName": "韩丽娟",
        "remark": "照片已核实，确属质量问题，同意退货退款",
        "createTime": "2026-09-13 19:00:00"
      },
      {
        "fromStatus": "APPROVED",
        "toStatus": "REFUNDING",
        "operatorType": "AGENT",
        "operatorName": "韩丽娟",
        "remark": "已生成退货面单，等待用户寄回后原路退款",
        "createTime": "2026-09-14 13:00:00"
      }
    ],
    "allowedTransitions": ["COMPLETED"]
  }
}
```

**字段说明**

| 字段 | 说明 |
|---|---|
| `flows` | 对应 `t_after_sale_flow` 表，**按 `create_time` 升序**。第一条的 `fromStatus` 是 `null`（表示"从无到有"） |
| `allowedTransitions` | 当前状态下**允许流转到的目标状态**数组，由状态机算出来 |
| `order` | 关联订单快照，前端要显示"这个工单是哪一单的哪件商品" |

`allowedTransitions` 这个字段很关键：**前端靠它决定显示哪几个按钮**，
后端靠同一张状态机表做真正的校验。两边规则一致，但**后端才是权威**——
前端就算被改坏了，后端也必须拦住非法流转。

### 6.3 `POST /api/after-sales`

用户在前端自助建工单（`source = WEB`，`aiGenerated = false`）。

**请求**

```json
{
  "orderNo": "SO2026090900353",
  "productId": 43,
  "type": "REFUND",
  "reason": "收到时外包装破损"
}
```

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "ticketNo": "AS20260914009",
    "status": "PENDING",
    "message": "工单已创建，等待客服审核"
  }
}
```

**实现要点**

- 创建时**必须同时写一条 `t_after_sale_flow`**（`fromStatus=null`, `toStatus=PENDING`），
  保证任何工单都有完整轨迹。这两件事放同一个 `@Transactional`。
- 工单号生成规则：`AS` + `yyyyMMdd` + 3 位当日流水，如 `AS20260914001`。
  并发下用唯一索引兜底，冲突就重试。

### 6.4 `POST /api/after-sales/{ticketNo}/transition`

**状态流转**。工单页上所有按钮都打这个接口。

**请求**

```json
{
  "toStatus": "APPROVED",
  "remark": "照片已核实，确属质量问题，同意退货退款"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `toStatus` | string | 是 | 目标状态，见 2.2 |
| `remark` | string | 否 | 处理备注，**建议强制必填**（真实工单系统都要求留痕） |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "ticketNo": "AS20260914003",
    "status": "COMPLETED",
    "statusText": "已完成"
  }
}
```

**非法流转必须报错**

```json
{ "code": 0, "msg": "不允许从 已完成 流转到 退款中", "data": null }
```

**实现要点（这段是面试重点）**

1. 状态用**枚举**，流转关系用一个 `Map` 定义：

```java
public enum AfterSaleStatus {
    PENDING, MANUAL_REVIEW, APPROVED, REFUNDING, COMPLETED, REJECTED, CANCELLED;

    // 合法的流转关系，和前端 ui.js 里的 TRANSITIONS 必须一致
    private static final Map<AfterSaleStatus, Set<AfterSaleStatus>> TRANSITIONS = Map.of(
        PENDING,       Set.of(APPROVED, REJECTED, MANUAL_REVIEW, CANCELLED),
        MANUAL_REVIEW, Set.of(APPROVED, REJECTED, CANCELLED),
        APPROVED,      Set.of(REFUNDING),
        REFUNDING,     Set.of(COMPLETED),
        COMPLETED,     Set.of(),
        REJECTED,      Set.of(),
        CANCELLED,     Set.of()
    );

    public boolean canTransferTo(AfterSaleStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
```

2. 整个操作放一个 `@Transactional`：**更新工单状态** + **插入一条流转记录**，同时成功或同时失败。

3. 记录 `operatorType` 和 `operatorId`，从 `ThreadLocal` 里取当前登录人。

4. **并发保护**：两个客服同时点"审核通过"，应该只有一个成功。
   用乐观锁（`version` 字段）或 `UPDATE ... WHERE status = 'PENDING'` 判断影响行数。

### 6.5 工单状态机 · 合法流转表

```
PENDING ──────┬──► APPROVED ──► REFUNDING ──► COMPLETED
              ├──► REJECTED
              ├──► MANUAL_REVIEW ──┬──► APPROVED ──► REFUNDING ──► COMPLETED
              │                    ├──► REJECTED
              │                    └──► CANCELLED
              └──► CANCELLED
```

| 当前状态 | 允许流转到 |
|---|---|
| `PENDING` | `APPROVED`、`REJECTED`、`MANUAL_REVIEW`、`CANCELLED` |
| `MANUAL_REVIEW` | `APPROVED`、`REJECTED`、`CANCELLED` |
| `APPROVED` | `REFUNDING` |
| `REFUNDING` | `COMPLETED` |
| `COMPLETED` | —— 终态，不能再流转 |
| `REJECTED` | —— 终态 |
| `CANCELLED` | —— 终态 |

**这张表必须在 Java 和前端各写一份，且内容完全相同。** 前端那份在
`frontend/assets/ui.js` 的 `SM.TRANSITIONS`。

---

## 7. 对话

### 7.1 `GET /api/chat/sessions`

左侧会话列表。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | long | 否 | 只对 AGENT/ADMIN 生效；USER 强制自己的 |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": [
    {
      "sessionId": "S20260914093012",
      "userId": 1,
      "userName": "谢雨欣",
      "title": "退货 · 口红颜色不符",
      "lastMessage": "您反馈的是「颜色与图片不符」，这类情况需要人工核实…",
      "createTime": "2026-09-14 09:30:12",
      "updateTime": "2026-09-14 16:30:00"
    }
  ]
}
```

按 `update_time DESC` 排序，最新的会话在最上面。

### 7.2 `POST /api/chat/sessions`

**请求**

```json
{ "userId": 1, "userName": "谢雨欣" }
```

**响应**

```json
{ "code": 1, "msg": "", "data": { "sessionId": "S20260914171122" } }
```

**实现要点**：`sessionId` 要全局唯一（`t_chat_session.session_id` 上有唯一索引），
用 `UUID` 去掉横线，或 `S` + 时间戳 + 随机数。

### 7.3 `GET /api/chat/history`

**请求参数**：`sessionId`（必填）

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": [
    { "role": "user", "content": "我的订单到哪了", "createTime": "2026-09-14 09:30:20" },
    {
      "role": "assistant",
      "content": "您最近有 3 笔订单：…",
      "intent": "ORDER_QUERY",
      "confidence": 0.96,
      "latencyMs": 1720,
      "createTime": "2026-09-14 09:30:22",
      "tools": [
        {
          "name": "search_user_orders",
          "args": {},
          "status": "ok",
          "ms": 96,
          "result": "返回 3 条"
        }
      ]
    }
  ]
}
```

**注意**：`tools` 在库里是 `t_chat_message.tool_calls`（**JSON 字段**），
返回时要**反序列化成数组**，不是字符串。前端要用它渲染"工具调用"那块彩色区域 —— 
**这是整个项目在面试时最值得展示的部分**，别偷懒返回 `null`。

### 7.4 `POST /api/chat` ⭐ 最核心的接口

**对话入口**。前端 → Java → Python AI 服务 → Java → 前端。

**请求**

```json
{
  "sessionId": "S20260914093012",
  "message": "我的订单到哪了"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 否 | 传 `null` 表示新建会话，响应里会带回新的 `sessionId` |
| `message` | string | 是 | 用户这句话 |

**响应**

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "sessionId": "S20260914093012",
    "answer": "您最近有 3 笔订单：1. 丝绒口红套装等 2 件，9 月 14 日已签收……",
    "intent": "ORDER_QUERY",
    "confidence": 0.96,
    "latencyMs": 1720,
    "tools": [
      { "name": "search_user_orders", "args": {}, "status": "ok", "ms": 96, "result": "返回 3 条" }
    ]
  }
}
```

**这个接口里，Java 要做 5 件事**（顺序很重要）：

```
1. 从 JWT 解析出 userId 和 role          ← 绝不能让前端传 userId，能伪造
2. 校验 sessionId 属于该用户（防越权）
3. HTTP POST 到 Python：{userId, sessionId, message}
      Python 地址读配置：ai.service.url=http://localhost:8000
4. 拿到 Python 的返回后，落库 t_chat_message 两条（user 一条、assistant 一条）
5. 原样把 answer/intent/confidence/tools 返回给前端
```

**为什么不让前端直连 Python？**（面试会问）

> 因为 `userId` 必须由 Java 从 JWT 里解析后注入。如果前端直接调 Python，
> 要么前端传 `userId`（可伪造，等于没有鉴权），要么 Python 自己解析 JWT
> （那 AI 服务就得耦合鉴权逻辑，且拿不到统一的权限模型）。
> 由 Java 转发还有一个好处：**AI 的所有动作都过一遍业务服务，天然可审计**。

**Python 侧的约定**（你在写 Python 服务时按这个来）

```
POST http://localhost:8000/chat
Body: { "userId": 1, "sessionId": "S2026...", "message": "我的订单到哪了" }
返回: { "answer": "...", "intent": "ORDER_QUERY", "confidence": 0.96,
        "latencyMs": 1720, "tools": [ ... ] }
```

**超时与降级**（这一步别漏，面试加分）

Python 或大模型挂了不能把整个对话功能带崩：

```java
try {
    resp = restTemplate.postForObject(aiUrl + "/chat", req, ChatResp.class);
} catch (Exception e) {
    log.error("AI 服务调用失败", e);
    return Result.success(ChatResp.fallback("客服繁忙，请稍后再试或转人工"));
}
```

超时时间设 10~15 秒（大模型本来就慢，设 3 秒会天天超时）。

---

## 8. 内部接口（Python AI 服务 → Java）

**全部要求请求头 `X-Internal-Token: <密钥>`**，密钥写在 `application.properties`：

```properties
internal.token=dev-internal-token-change-me
ai.service.url=http://localhost:8000
```

**为什么要有这一层**（背下来，面试必问）：

> Python AI 服务**不直接连 MySQL**。它要查任何业务数据，只能 HTTP 调用 Java 的
> `/internal/*` 接口。理由有四个：
> 1. **数据所有权清晰**：业务数据只由业务服务写，AI 只是"只读 + 提交申请"
> 2. **可独立扩容**：AI 是 IO 密集（等大模型响应），Java 是事务密集，扩容策略完全不同
> 3. **安全**：AI 拿到的是受限的服务 token，即使提示词被注入也碰不到数据库
> 4. **可审计**：AI 的所有动作都经过 Java，天然留下审计日志

### 8.1 `GET /internal/orders/{orderNo}`

**查询参数**：`userId`（必填，用于校验归属）

**响应**

```json
{
  "orderNo": "SO2026090900353",
  "status": "RECEIVED",
  "statusText": "已签收",
  "payAmount": 583.86,
  "payTime": "2026-09-14 09:12:00",
  "receiveTime": "2026-09-14 20:54:11",
  "logistics": {
    "trackNo": "SF062649192963",
    "currentStatus": "已签收",
    "currentNode": "已签收，签收人：本人",
    "estimatedArrival": "2026-09-12"
  },
  "items": [
    {
      "productId": 43,
      "productName": "丝绒口红套装",
      "price": 204.95,
      "quantity": 2,
      "refundDeadline": "2026-09-21 20:54:11",
      "refundEligible": true
    }
  ]
}
```

> **`refundEligible` 的定位**：它由 Java 算，AI 不需要懂"签收后 7 天"这条业务规则。
> **规则收敛在 Java，AI 只负责理解和表达**。这个设计面试时值得讲：
> "业务规则的唯一来源是业务服务，AI 层不复制规则，避免两边算出不同结果。"

**注意**：`/internal/*` 的返回**不包 `Result`**，直接返回业务对象。
因为调用方是程序不是人，`code/msg` 那层包壳没有意义，出错用 HTTP 状态码表达。

### 8.2 `GET /internal/orders/search`

| 参数 | 必填 | 说明 |
|---|---|---|
| `userId` | 是 | 用户 ID |
| `status` | 否 | 订单状态 |
| `keyword` | 否 | 商品名模糊 |
| `limit` | 否 | 默认 5 |

**响应**：订单数组（结构同 8.1，可省略 `logistics`）。

### 8.3 `POST /internal/after-sale/check-eligible`

**校验某订单商品是否具备退货资格**。AI 在建工单**之前必须先调这个**。

**请求**

```json
{ "orderNo": "SO2026090900353", "productId": 43 }
```

**响应（有资格）**

```json
{
  "eligible": true,
  "code": "OK",
  "message": "该商品在 7 天无理由退货期内（截止 2026-09-21）",
  "suggestManualReview": false,
  "refundDeadline": "2026-09-21 20:54:11"
}
```

**响应（已超期）**

```json
{
  "eligible": false,
  "code": "OVER_DEADLINE",
  "message": "该商品已超过 7 天无理由退货期限（截止 2026-09-08），建议转人工审核",
  "suggestManualReview": true,
  "refundDeadline": "2026-09-08 21:29:02"
}
```

**`suggestManualReview` 是关键字段**：

AI 看到 `eligible=false` 且 `suggestManualReview=true` 时，**不应该直接说"不行"**，
而应该建一张 `MANUAL_REVIEW` 的工单，并告诉用户"已为您提交人工审核，客服会在 24 小时内联系您"。

**这一条同时证明四件事**（面试必讲）：
1. 你理解 AI 的能力边界（不是什么都敢让它干）
2. 你有风控意识（敏感操作要人工）
3. 你懂人机协同（AI 干 80%，人干 20%）
4. 你会设计兜底（AI 不确定时怎么办）

`code` 的可能值：`OK` / `OVER_DEADLINE` / `NOT_RECEIVED`（还没签收）/ `NOT_QUALITY`（非质量问题场景）。

### 8.4 `POST /internal/after-sale/create`

**AI 建工单**。注意：**只建工单，不执行退款**。

**请求**

```json
{
  "orderNo": "SO2026090900353",
  "productId": 43,
  "type": "REFUND",
  "reason": "用户说：口红颜色和图片差太多，想退货",
  "aiGenerated": true,
  "aiConfidence": 0.92
}
```

**响应**

```json
{
  "ticketNo": "AS20260914001",
  "status": "MANUAL_REVIEW",
  "message": "工单已创建，待人工审核"
}
```

**两条必须做到的规则**

1. **置信度决定初始状态**
   - `aiConfidence >= 0.7` → `PENDING`
   - `aiConfidence < 0.7` → `MANUAL_REVIEW`（这就是人机协同兜底）

2. **幂等**（面试加分项）：同一个 `orderNo + productId + type`，
   如果已经存在 `PENDING` 或 `MANUAL_REVIEW` 状态的工单，**直接返回已有的工单号，不新建**。

   为什么需要：AI 多轮对话里，用户可能连说两遍"我要退货"，不加幂等就会建出两张单，
   客服看到会骂人。**幂等键用唯一索引兜底**：

```sql
-- 可以加一个唯一索引来强制幂等（只对未完结的工单生效，MySQL 不支持部分索引，
-- 所以实际做法是：先 SELECT 查活跃工单，命中就返回；并发下靠唯一索引兜底）
```

### 8.5 `POST /internal/after-sale/search`

| 参数 | 必填 | 说明 |
|---|---|---|
| `userId` | 是 | 用户 ID |
| `ticketNo` | 否 | 工单号 |

**响应**：工单数组（含 `status`、`statusText`、`productName`、`createTime`）。

用途：用户问"我上次那个退货申请怎么样了"，AI 调这个查。

---

## 9. 错误码表

`code` 只有 `1`（成功）和 `0`（失败）两个值，**具体原因看 `msg`**。
如果需要程序判断失败类型，用 `data` 里带字段，或者扩展 `code`：

| code | 含义 | 什么时候用 |
|---|---|---|
| `1` | 成功 | —— |
| `0` | 通用业务失败 | 看 `msg` |
| `401` | 未登录 / token 过期 | 拦截器返回，前端会自动跳登录页 |
| `403` | 无权限 | 越权访问 |
| `500` | 服务端异常 | 全局异常处理器兜底 |

**全局异常处理器**（建议写一个 `@RestControllerAdvice`），把异常统一转成 `Result`：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBiz(BusinessException e) {
        return Result.error(e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后再试");
    }
}
```

**注意最后那个 `Exception` 分支：不要 `e.getMessage()` 返回给前端**，
会泄露堆栈和 SQL 片段。日志里打详细，返回给用户一句笼统的。

---

## 10. 页面 ↔ 接口映射

前端每页底部都有一行提示条，写的就是这些。方便你联调时对照。

| 页面 | 调用的接口 |
|---|---|
| `login.html` | `POST /api/auth/login` |
| `orders.html` | `GET /api/orders`、`GET /api/orders/{orderNo}`、`GET /api/stats/orders` |
| `tickets.html` | `GET /api/after-sales`、`GET /api/after-sales/{ticketNo}`、`POST /api/after-sales/{ticketNo}/transition` |
| `chat.html` | `GET /api/chat/sessions`、`POST /api/chat/sessions`、`GET /api/chat/history`、`POST /api/chat` |

---

## 11. 建议的开发顺序

**一次只做一件事，每步都要有"通关证据"**。不要跳步。

> **2026-09-15 调整**：实际执行时，"登录 + JWT"从第 1 步**挪到了第 3 天** ——
> 先用第 1 天把订单列表接口跑通，第 2 天补分页与筛选，第 3 天才做 JWT。
> 理由是让第一天就能在浏览器看到数据库真数据；
> 而补 JWT 时只需改 Controller 里取 userId 的那一行，返工极小。
> 逐日安排见 **`docs/DEV_PLAN.md`**（那份是执行用的，本表是设计用的，内容不冲突）。

| 步 | 做什么 | 通关证据 |
|---|---|---|
| 1 | 登录接口 + JWT 工具类 + 拦截器 + 放行 login | `curl` 登录拿到 token；不带 token 访问 `/api/orders` 返回 401 |
| 2 | `GET /api/auth/me` | 带 token 能查回自己 |
| 3 | `GET /api/orders`（先不做筛选，只做分页） | 浏览器打开返回 10 条真实订单 JSON |
| 4 | 给列表加 `keyword` / `status` / `userId` 筛选 | `?status=RECEIVED` 结果条数变少且正确 |
| 5 | `GET /api/orders/{orderNo}`（含明细 + 物流）+ 算 `refundEligible` | 拿 88 条边界数据自测，边界当天判定正确 |
| 6 | `GET /api/stats/orders` | 前端 KPI 卡片出现真实数字 |
| 7 | **切前端开关联调订单页** | orders.html 关掉 mock，页面数据来自数据库 |
| 8 | 工单实体 + `GET /api/after-sales` + `stats` | 列表能出（此时库是空表，可以先手动插两条测试） |
| 9 | `GET /api/after-sales/{ticketNo}` + `allowedTransitions` | `flows` 是数组不是字符串 |
| 10 | `POST /api/after-sales/{ticketNo}/transition` + 状态机校验 | 非法流转被拦住并返回明确 msg |
| 11 | `POST /api/after-sales`（自助建单，含幂等） | 同一单连提两次只出一张工单 |
| 12 | **切前端开关联调工单页** | 点"审核通过"能真的改库并新增一条流转记录 |
| 13 | `GET /api/chat/sessions` / `POST /api/chat/sessions` / `GET /api/chat/history` | 会话列表和消息能出 |
| 14 | `POST /api/chat` 先返回假数据（不接 Python） | 前端能收到结构正确的响应并渲染 |
| 15 | 接上 Python，跑通一次真实对话 | 前端看到真实 AI 回答 + 工具调用 |
| 16 | `/internal/*` 五个接口 + `@InternalOnly` 拦截器 | Python 能查到订单；不带 token 返回 401 |

**第 14 步很重要**：先把 `POST /api/chat` 用假数据打通，
保证"Java ↔ 前端"链路是通的，再去接 Python。这样出问题时你能确定
是 Java 的问题还是 Python 的问题——**否则两个变量一起动，排错会很痛苦**。

---

## 12. 前后端联调

前端在 `frontend/assets/api.js` 顶部有一个开关：

```javascript
var USE_MOCK = true;      // ← 改这一个地方
```

| 值 | 行为 |
|---|---|
| `true` | 用 `frontend/assets/mock.js` 里的假数据，不发网络请求。**你现在看到的页面就是靠它渲染的** |
| `false` | 真的发 HTTP 请求到后端 |

**建议按接口逐个切**，不要等全写完才切：

```javascript
// 例：订单接口已经写好了，但工单还没写
// 那就先整体切成 false，然后确保后端至少实现了订单相关接口，
// 工单页会报错——这是正常的，前端会弹出"Mock 未实现"之类的提示，不影响订单页验证。
```

后端地址在同一个文件的 `API_BASE`：

```javascript
var API_BASE = '';                          // 走 nginx 同源（推荐）
var API_BASE = 'http://localhost:8080';     // 前端直连后端（后端要开 CORS）
```

**怎么确认当前用的是真数据还是假数据？**：页面右上角有一行小字，
mock 模式显示「Mock 数据（后端未接入）」，真实模式显示后端地址。

---

## 13. 容易踩的坑

| 现象 | 原因 | 修法 |
|---|---|---|
| 查出来的字段全是 `null` | 数据库是 `order_no`，Java 是 `orderNo` | `application.properties` 加 `mybatis.configuration.map-underscore-to-camel-case=true` |
| 时间返回成 `2026-09-14T08:54:11` 带 T | Jackson 默认 ISO 格式 | 全局配置 `LocalDateTime` 序列化格式为 `yyyy-MM-dd HH:mm:ss` |
| 金额显示成 `583.8600000001` | 用了 `double` | 用 `BigDecimal`，数据库 `DECIMAL(10,2)` |
| 权限校验被绕过 | 前端传了 `userId` 就信了 | **永远从 JWT 解析**，忽略前端传的 `userId` |
| 越权能查到别人的单 | 只在 Java 里 `if` 判断，没加 SQL 条件 | 查询条件里带 `user_id`，查不到自然返回空 |
| 工单创建了但流转记录没有 | 两步没在同一个事务 | 加 `@Transactional` |
| 非法流转没被拦住 | 只在前端隐藏了按钮 | 后端必须再校验一次状态机 |
| AI 重复建单 | 没做幂等 | 同 `orderNo+productId+type` 有活跃工单就返回已有的 |
| 跨域报错 | 前端直连 8080 且后端没配 CORS | 用 nginx 反代（推荐），或配 `@CrossOrigin` |
| Python 挂了导致对话 500 | 没做降级 | try/catch，超时 10~15 秒，失败时返回兜底话术 |
| 前端 401 后一直转圈 | 没处理 401 | `api.js` 里已经统一处理：401 自动跳登录页 |

---

## 附：等价的 curl 验收脚本

后端写好后，把 `<token>` 换成登录拿到的真实 token，逐条跑一遍：

```bash
# 1. 登录
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user0108","password":"123456"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "TOKEN=$TOKEN"

# 2. 当前用户
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# 3. 订单列表（第一页 3 条）
curl -s "http://localhost:8080/api/orders?page=1&limit=3" -H "Authorization: Bearer $TOKEN"

# 4. 只查已签收
curl -s "http://localhost:8080/api/orders?status=RECEIVED&limit=5" -H "Authorization: Bearer $TOKEN"

# 5. 只查「即将到期」的（客服最该先处理的）
curl -s "http://localhost:8080/api/orders?eligibility=soon" -H "Authorization: Bearer $TOKEN"

# 6. 关键词搜索
curl -s "http://localhost:8080/api/orders?keyword=口红" -H "Authorization: Bearer $TOKEN"

# 7. 订单详情（把订单号换成你库里真实存在的）
curl -s "http://localhost:8080/api/orders/SO2026090900353" -H "Authorization: Bearer $TOKEN"

# 8. 订单统计
curl -s http://localhost:8080/api/stats/orders -H "Authorization: Bearer $TOKEN"

# 9. 工单列表
curl -s "http://localhost:8080/api/after-sales?page=1&limit=10" -H "Authorization: Bearer $TOKEN"

# 10. 非法流转应该报错（拿一个已完成/已驳回的工单试）
curl -s -X POST http://localhost:8080/api/after-sales/AS20260912006/transition \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"toStatus":"REFUNDING","remark":"测试非法流转"}'

# 11. 越权测试：用普通用户 token 查别人的订单，应返回"订单不存在"
#     （先用 user0001 登录拿 USER_TOKEN，再查一个不属于他的订单号）

# 12. 内部接口：不带 token 应返回 401
curl -s http://localhost:8080/internal/orders/SO2026090900353?userId=1
# 带上正确 token
curl -s -H "X-Internal-Token: dev-internal-token-change-me" \
  "http://localhost:8080/internal/orders/SO2026090900353?userId=1"
```
